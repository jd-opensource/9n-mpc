package com.jd.mpc.service.task;

import com.alibaba.fastjson.JSONObject;
import com.google.common.collect.Maps;
import com.jd.mpc.common.constant.DeploymentPathConstant;
import com.jd.mpc.common.enums.TaskStatusEnum;
import com.jd.mpc.common.enums.TaskTypeEnum;
import com.jd.mpc.common.util.CommonUtils;
import com.jd.mpc.domain.offline.commons.Job;
import com.jd.mpc.domain.offline.commons.OfflineTask;
import com.jd.mpc.domain.offline.commons.PreJob;
import com.jd.mpc.domain.offline.commons.SubTask;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class BdpDecryptTaskService extends AbstractTaskService{

    @Override
    public boolean match(PreJob preJob) {
        return TaskTypeEnum.BDP_DECRYPT.equals(TaskTypeEnum.getByValue(preJob.getType()));
    }

    @Override
    public Map<String, PreJob> createTaskMap(PreJob preJob) {
        Map<String, PreJob> map = new HashMap<>();
        Map<String, List<OfflineTask>> listMap = preJob.getTasks().stream()
                .collect(Collectors.groupingBy(OfflineTask::getTarget));
        listMap.forEach((target, offlineTasks) -> {
            PreJob job = new PreJob();
            BeanUtils.copyProperties(preJob,job);
            job.setTasks(offlineTasks);
            map.put(target, job);
        });
        return map;
    }

    @Override
    public Job compile(PreJob preJob) {
        // 默认使用第一个解析,填充公共值
        OfflineTask oriTask = this.initOriTask(preJob);
        oriTask.setDeploymentPath(DeploymentPathConstant.BDP_DECRYPT);
        oriTask.setTaskIndex(0);
        oriTask.setName(CommonUtils.genPodName(oriTask, null));
        Map<String,String> extParam = Maps.newHashMap();
        JSONObject accountjson = JSONObject.parseObject(oriTask.getParameters().get("accountjson"));
        extParam.put("INTERACTIVE_CFS_ACCESSKEY",accountjson.getString("cfsAccessKey"));
        extParam.put("INTERACTIVE_CFS_SECRETKEY",accountjson.getString("cfsSeceretKey"));
        extParam.put("INTERACTIVE_CFS_BUCKET",accountjson.getString("cfsBucket"));
        extParam.put("INTERACTIVE_EXE_ENV",oriTask.getParameters().get("systemEnv"));
        extParam.put("BEE_USER",oriTask.getParameters().get("BEE_USER"));
        extParam.put("BEE_SOURCE",oriTask.getParameters().get("BEE_SOURCE"));
        extParam.put("INTERACTIVE_URL",portalUrl);
        if (StringUtils.isNotBlank(oriTask.getParameters().get("USER_TYPE"))){
            extParam.put("USER_TYPE",oriTask.getParameters().get("USER_TYPE"));
        }
        if (StringUtils.isNotBlank(oriTask.getParameters().get("BUSINESS_LINE"))){
            extParam.put("BUSINESS_LINE",oriTask.getParameters().get("BUSINESS_LINE"));
        }
        oriTask.setExtParameters(extParam);
        // 填充subTask
        SubTask subTask = SubTask.builder().id(preJob.getId()).subId(0)
                .status(TaskStatusEnum.NEW.getStatus()).tasks(Collections.singletonList(oriTask)).build();
        // 填充job
        return Job.builder().id(preJob.getId()).type(preJob.getType())
                .subTaskList(Collections.singletonList(subTask)).build();
    }

}
