package com.jd.mpc.service.zeebe;

import cn.hutool.core.date.DateUtil;
import com.alibaba.fastjson.JSONObject;
import com.jd.mpc.common.constant.DeploymentPathConstant;
import com.jd.mpc.common.enums.TaskTypeEnum;
import com.jd.mpc.common.util.CommonUtils;
import com.jd.mpc.domain.offline.commons.OfflineTask;
import io.camunda.zeebe.client.api.response.ActivatedJob;
import io.camunda.zeebe.client.api.worker.JobClient;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * @Description: buffalo
 * @Author: feiguodong
 * @Date: 2022/10/17
 */
@Service
public class BuffaloZeebeService extends AbstractZeebeService{

    @Override
    public boolean match(TaskTypeEnum taskType) {
        return TaskTypeEnum.BUFFALO_WORKER.equals(taskType);
    }

    /**
     * @param client
     * @param job
     * @return
     */
    @Override
    public List<OfflineTask> compile(JobClient client, ActivatedJob job) {
        //init
        OfflineTask offlineTask = this.initTask(TaskTypeEnum.BUFFALO_WORKER,DeploymentPathConstant.BUFFALO_WORKER, job.getVariablesAsMap());
        //param
        Map<String,String> param = offlineTask.getParameters();
        Map<String, Object> varMap = job.getVariablesAsMap();

        param.put("taskId",String.valueOf(varMap.get("InputVariable_task_id")));
        param.put("appId",String.valueOf(varMap.get("InputVariable_app_id")));
        param.put("userToken",String.valueOf(varMap.get("InputVariable_user_token")));
        param.put("appToken",String.valueOf(varMap.get("InputVariable_app_token")));
        param.put("scheTime", DateUtil.formatDateTime(new Date()));
        String args = JSONObject.toJSONString(new JSONObject());
        if (varMap.containsKey("InputVariable_args") && varMap.get("InputVariable_args") != null){
            args = JSONObject.toJSONString(varMap.get("InputVariable_args"));
        }
        param.put("args", args);
        //默认超时3天
        param.put("timeout", String.valueOf(CommonUtils.getPositiveIntegerOrDefault(varMap,"InputVariable_timeout",259200)));
        param.put("statusServer",nodeIp+":"+nodePort);

        return Collections.singletonList(offlineTask);
    }
}
