package com.jd.mpc.service.zeebe;

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
 * @Description: file-transfer
 * @Author: feiguodong
 * @Date: 2022/10/18
 */
@Service
public class FileTransferZeebeService extends AbstractZeebeService {

    @Override
    public boolean match(TaskTypeEnum taskType) {
        return TaskTypeEnum.FILE_TRANSFER.equals(taskType);
    }

    @Override
    public List<OfflineTask> compile(JobClient client, ActivatedJob job) {
        // init
        OfflineTask offlineTask = this.initTask(TaskTypeEnum.FILE_TRANSFER,DeploymentPathConstant.FILE_TRANSFER, job.getVariablesAsMap());
        //param
        Map<String, String> param = offlineTask.getParameters();
        Map<String, Object> varMap = job.getVariablesAsMap();

        String clusterId = offlineTask.getId() + "-" + offlineTask.getSubId() + "-"
                + offlineTask.getTaskIndex();
        param.put("application-id", clusterId);
        param.put("status-server", nodeIp + ":" + nodePort);
        param.put("domain", localTarget);
        param.put("local-listen", "0.0.0.0:20000");
        param.put("target", String.valueOf(varMap.get("InputVariable_target")));
        if (CommonUtils.getStringOrDefault(varMap,"InputVariable_mode",null) != null){
            param.put("mode", String.valueOf(varMap.get("InputVariable_mode")));
        }
        param.put("path", String.valueOf(varMap.get("InputVariable_path")));

        return Collections.singletonList(offlineTask);
    }
}
