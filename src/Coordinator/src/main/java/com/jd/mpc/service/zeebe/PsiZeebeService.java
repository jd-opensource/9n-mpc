package com.jd.mpc.service.zeebe;

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

/**
 * @Description: psi
 * 
 * @Date: 2022/10/17
 */
@Service
public class PsiZeebeService extends AbstractZeebeService{

    @Override
    public boolean match(TaskTypeEnum taskType) {
        return TaskTypeEnum.PSI.equals(taskType);
    }

    @Override
    public List<OfflineTask> compile(JobClient client, ActivatedJob job) {
        //init
        OfflineTask offlineTask = this.initTask(TaskTypeEnum.PSI,DeploymentPathConstant.PSI, job.getVariablesAsMap());
        //param
        Map<String,String> param = offlineTask.getParameters();
        Map<String, Object> varMap = job.getVariablesAsMap();

        param.put("output",(String) varMap.get("InputVariable_output_path"));
        param.put("input-col",(String) varMap.get("InputVariable_input_col"));
        param.put("input",(String) varMap.get("InputVariable_input_path"));
        param.put("target",(String) varMap.get("InputVariable_participants"));
        param.put("send-back",String.valueOf(CommonUtils.getBooleanOrDefault(varMap,"InputVariable_send_back",false)));
        param.put("status-server", nodeIp + ":" + nodePort);
        String clusterId = offlineTask.getId() + "-" + offlineTask.getSubId() + "-"
                + offlineTask.getTaskIndex();
        param.put("cluster-id", clusterId);
        param.put("party-id", localTarget);
        if (varMap.containsKey("InputVariable_extra_configs") && varMap.get("InputVariable_extra_configs") != null){
            Map<String,Object> extraMap =(Map<String,Object>) varMap.get("InputVariable_extra_configs");
            for (Map.Entry<String, Object> entry : extraMap.entrySet()) {
                param.put(entry.getKey().replaceAll("_","-"),String.valueOf(entry.getValue()));
            }
        }

        return Collections.singletonList(offlineTask);
    }
}
