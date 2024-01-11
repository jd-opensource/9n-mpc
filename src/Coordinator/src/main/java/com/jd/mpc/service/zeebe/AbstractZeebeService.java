package com.jd.mpc.service.zeebe;

import com.google.common.collect.Maps;
import com.jd.mpc.common.enums.TaskStatusEnum;
import com.jd.mpc.common.enums.TaskTypeEnum;
import com.jd.mpc.common.util.CommonUtils;
import com.jd.mpc.domain.offline.commons.OfflineTask;
import com.jd.mpc.service.FileService;
import com.jd.mpc.service.TaskSupport;
import io.camunda.zeebe.client.api.response.ActivatedJob;
import io.camunda.zeebe.client.api.worker.JobClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;
import java.util.Map;

/**
 * @Description: abstract
 * 
 * @Date: 2022/10/17
 */
@Service
public abstract class AbstractZeebeService implements IZeebeService{

    @Value("${spring.redis.host}")
    String host;
    @Value("${spring.datasource.url}")
    String dbUrl;
    @Value("${spring.datasource.username}")
    String dbName;
    @Value("${spring.datasource.password}")
    String dbPwd;
    @Value("${spring.redis.port}")
    int port;
    @Value("${spring.redis.password}")
    String password;
    @Value("${grpc.proxy.host}")
    String proxyHost;
    @Value("${grpc.proxy.port}")
    String proxyPort;
    @Value("${grpc.proxy.local-port}")
    String proxyLocalPort;
    @Value("${target}")
    String localTarget;
    @Value("${mount.data.path}")
    String mountDataPath;
    @Value("${k8s.namespace}")
    String nameSpace;
    @Resource
    FileService fileService;
    @Resource
    TaskSupport taskSupport;
    @Value("${node.ip}")
    String nodeIp;
    @Value("${node.port}")
    String nodePort;
    @Value("${k8s.name.prefix}")
    String k8sPrefix;
    @Value("${portal.url}")
    String portalUrl;

    @Override
    public boolean match(TaskTypeEnum taskType) {
        return false;
    }

    @Override
    public List<OfflineTask> compile(JobClient client, ActivatedJob job) {
        throw new UnsupportedOperationException();
    }

    public OfflineTask initTask(TaskTypeEnum taskType,String deployYamlPath,Map<String,Object> varMap) {
        OfflineTask offlineTask = new OfflineTask();
        offlineTask.setId((String) varMap.get("InputVariable_id"));
        offlineTask.setSubId(0);
        offlineTask.setTaskIndex(0);
        offlineTask.setStatus(TaskStatusEnum.NEW.getStatus());
        offlineTask.setTaskType(taskType.getName());
        offlineTask.setRole(taskType.getName());
        offlineTask.setDeploymentPath(deployYamlPath);
        offlineTask.setCpu(CommonUtils.getPositiveIntegerOrDefault(varMap,"InputVariable_cpu",null));
        offlineTask.setMemory(CommonUtils.getPositiveIntegerOrDefault(varMap,"InputVariable_memory",null));
        offlineTask.setImage(CommonUtils.getStringOrDefault(varMap,"InputVariable_image",null));
        Map<String,String> envMap = Maps.newHashMap();
        if (varMap.containsKey("InputVariable_env") && varMap.get("InputVariable_env") != null){
            envMap = (Map<String, String>) varMap.get("InputVariable_env");
        }
        if (varMap.containsKey("InputVariable_labels") && varMap.get("InputVariable_labels") != null){
            offlineTask.setLabels((Map<String, String>) varMap.get("InputVariable_labels"));
        }
        offlineTask.setCustomerId(CommonUtils.getStringOrDefault(varMap,"InputVariable_customer_id",null));
        offlineTask.setName(CommonUtils.genPodName(offlineTask, null));
        /** k8s环境参数配置 */
        offlineTask.setRedis_server(host + ":" + port);
        offlineTask.setRedis_password(password);
        offlineTask.setProxy_remote(proxyHost + ":" + proxyPort);
        /** 算子侧参数配置 */
        Map<String, String> parameters = Maps.newHashMap();
        this.assembleParamMap(parameters);
        offlineTask.setParameters(parameters);
        offlineTask.setExtParameters(envMap);
        return offlineTask;
    }

    private void assembleParamMap(Map<String, String> param) {
        param.put("redis-host", host);
        param.put("redis-port", String.valueOf(port));
        param.put("redis-pwd", password);
        param.put("redis-server", host + ":" + port);
        param.put("redis-password", password);
        param.put("proxy-remote", proxyHost + ":" + proxyPort);
    }
}
