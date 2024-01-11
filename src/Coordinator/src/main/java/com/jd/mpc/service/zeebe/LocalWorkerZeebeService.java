package com.jd.mpc.service.zeebe;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.jd.mpc.common.constant.DeploymentPathConstant;
import com.jd.mpc.common.enums.TaskTypeEnum;
import com.jd.mpc.common.util.CommonUtils;
import com.jd.mpc.domain.offline.commons.OfflineTask;
import io.camunda.zeebe.client.api.response.ActivatedJob;
import io.camunda.zeebe.client.api.worker.JobClient;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @Description: local-worker
 * 
 * @Date: 2022/10/18
 */
@Service
public class LocalWorkerZeebeService extends AbstractZeebeService{

    @Override
    public boolean match(TaskTypeEnum taskType) {
        return TaskTypeEnum.LOCAL_WORKER.equals(taskType);
    }

    @Override
    public List<OfflineTask> compile(JobClient client, ActivatedJob job) {
        //init
        OfflineTask offlineTask = this.initTask(TaskTypeEnum.LOCAL_WORKER,DeploymentPathConstant.LOCAL_WORKER,job.getVariablesAsMap());
        //param
        Map<String, String> param = offlineTask.getParameters();
        Map<String, Object> varMap = job.getVariablesAsMap();

        String clusterId = offlineTask.getId() + "-" + offlineTask.getSubId() + "-"
                + offlineTask.getTaskIndex();
        param.put("application-id", clusterId);
        param.put("cluster-id", clusterId);
        if (CommonUtils.getStringOrDefault(varMap,"InputVariable_script_path",null) != null){
            //脚本不为空,是执行shell. 脚本为空,即为执行args命令
            param.put("input",String.valueOf(varMap.get("InputVariable_script_path")));
        }
        List<String> argsList = Lists.newArrayList();
        if (varMap.containsKey("InputVariable_args") && varMap.get("InputVariable_args") != null){
            argsList.addAll((List<String>)varMap.get("InputVariable_args"));
        }
        Map<String,String> kwargs = Maps.newHashMap();
        if (varMap.containsKey("InputVariable_kwargs") && varMap.get("InputVariable_kwargs") != null){
            kwargs = (Map<String,String>)varMap.get("InputVariable_kwargs");
        }
        for (Map.Entry<String, String> entry : kwargs.entrySet()) {
            argsList.add("--"+entry.getKey()+"="+entry.getValue());
        }
        param.put("args",JSONObject.toJSONString(argsList));
        param.put("status-server", nodeIp + ":" + nodePort);
        if (CommonUtils.getStringOrDefault(varMap,"InputVariable_interpreter",null) != null){
            param.put("interpreter",String.valueOf(varMap.get("InputVariable_interpreter")));
        }
        return Collections.singletonList(offlineTask);
    }

    public static void main(String[] args) {
        Map<String,Object> map = Maps.newHashMap();
        map.put("1",null);
        if (map.containsKey("1") && map.get("1") != null){
            System.out.println(11);
        }
//        System.out.println(map.getOrDefault("1", "ddd"));
    }


}
