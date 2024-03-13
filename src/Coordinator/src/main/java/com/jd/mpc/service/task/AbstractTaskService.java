package com.jd.mpc.service.task;

import com.jd.mpc.common.enums.TaskStatusEnum;
import com.jd.mpc.domain.offline.commons.Job;
import com.jd.mpc.domain.offline.commons.OfflineTask;
import com.jd.mpc.domain.offline.commons.PreJob;
import com.jd.mpc.service.FileService;
import com.jd.mpc.service.TaskSupport;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.util.Map;

/**
 * @Description: abstract task service, every taskService should inherit it
 * 
 * @Date: 2022/6/28
 */
@Service
public abstract class AbstractTaskService implements ITaskService{

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
    public boolean match(PreJob preJob) {
        return false;
    }

    @Override
    public Map<String, PreJob> createTaskMap(PreJob preJob) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Job compile(PreJob preJob) {
        throw new UnsupportedOperationException();
    }

    public OfflineTask initOriTask(PreJob preJob) {
        OfflineTask oriTask = preJob.getTasks().get(0);
        oriTask.setId(preJob.getId());
        oriTask.setSubId(0);
        oriTask.setTaskIndex(0);
        oriTask.setStatus(TaskStatusEnum.NEW.getStatus());
        /** k8s环境参数配置 */
        oriTask.setRedis_server(host + ":" + port);
        oriTask.setRedis_password(password);
        oriTask.setProxy_remote(proxyHost + ":" + proxyPort);
        /** 算子侧参数配置 */
        this.assembleParamMap(oriTask.getParameters());
        return oriTask;
    }

    private void assembleParamMap(Map<String, String> map) {
        map.put("redis-host", host);
        map.put("redis-port", String.valueOf(port));
        map.put("redis-pwd", password);
        map.put("redis-server", host + ":" + port);
        map.put("redis-password", password);
        map.put("proxy-remote", proxyHost + ":" + proxyPort);
    }
}
