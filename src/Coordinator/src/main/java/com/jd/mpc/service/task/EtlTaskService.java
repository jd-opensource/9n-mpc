package com.jd.mpc.service.task;

import cn.hutool.core.codec.Base64;
import com.google.common.collect.Maps;
import com.jd.mpc.common.constant.DeploymentPathConstant;
import com.jd.mpc.common.enums.TaskStatusEnum;
import com.jd.mpc.common.enums.TaskTypeEnum;
import com.jd.mpc.common.util.CommonUtils;
import com.jd.mpc.domain.offline.commons.Job;
import com.jd.mpc.domain.offline.commons.OfflineTask;
import com.jd.mpc.domain.offline.commons.PreJob;
import com.jd.mpc.domain.offline.commons.SubTask;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * @Description: etl
 * 
 * @Date: 2022/6/28
 */
@Slf4j
@Service
public class EtlTaskService extends AbstractTaskService{

    @Override
    public boolean match(PreJob preJob) {
        return TaskTypeEnum.ETL.equals(TaskTypeEnum.getByValue(preJob.getType()));
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
        oriTask.setDeploymentPath(DeploymentPathConstant.ETL);
        oriTask.setTaskIndex(0);
        oriTask.setName(CommonUtils.genPodName(oriTask, null));
        String mountPath = CommonUtils.genPath(oriTask, null);
        String s3aPath = CommonUtils.genS3aPath(oriTask,null);
        oriTask.getParameters().put("mountPath",mountPath);
        oriTask.getParameters().put("s3aPath",s3aPath);
        oriTask.getParameters().put("sql", Base64.encode(oriTask.getParameters().get("sql")));
        oriTask.getParameters().put("maskSettings", Base64.encode(oriTask.getParameters().get("maskSettings")));
        oriTask.getParameters().put("application-id", oriTask.getId());
        oriTask.getParameters().put("target", localTarget);
        oriTask.getParameters().put("logPath", "/mnt/logs");
        Map<String,String> extParam = Maps.newHashMap();
        extParam.put("HADOOP_USER_CERTIFICATE",oriTask.getParameters().get("bdpAccountCert"));
        oriTask.setExtParameters(extParam);
        // 填充subTask
        SubTask subTask = SubTask.builder().id(preJob.getId()).subId(0)
                .status(TaskStatusEnum.NEW.getStatus()).tasks(Collections.singletonList(oriTask)).build();
        // 填充job
        return Job.builder().id(preJob.getId()).type(preJob.getType())
                .subTaskList(Collections.singletonList(subTask)).build();
    }
}
