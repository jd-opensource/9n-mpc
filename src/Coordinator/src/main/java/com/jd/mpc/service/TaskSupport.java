
package com.jd.mpc.service;

import java.text.ParseException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

import javax.annotation.Resource;

import com.jd.mpc.domain.vo.*;
import com.jd.mpc.redis.RedisLock;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.time.DateUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.alibaba.fastjson.JSONArray;
import com.jd.mpc.common.enums.IsDeletedEnum;
import com.jd.mpc.common.enums.OperatorStatusEnum;
import com.jd.mpc.common.enums.TaskStatusEnum;
import com.jd.mpc.common.response.CommonException;
import com.jd.mpc.common.util.CommonUtils;
import com.jd.mpc.common.util.GsonUtil;
import com.jd.mpc.domain.cert.JobTaskStub;
import com.jd.mpc.domain.offline.commons.Job;
import com.jd.mpc.domain.offline.commons.OfflineTask;
import com.jd.mpc.domain.offline.commons.PreJob;
import com.jd.mpc.domain.offline.commons.SubTask;
import com.jd.mpc.domain.task.ParentTask;
import com.jd.mpc.redis.RedisService;
import com.jd.mpc.service.cert.JobTaskStubService;
import com.jd.mpc.storage.OfflineTaskMapHolder;
import com.jd.mpc.storage.TargetMapHolder;

import io.fabric8.kubernetes.api.model.ContainerStatus;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import lombok.extern.slf4j.Slf4j;

/**
 * @author luoyuyufei1
 * @date 2022/1/11 6:36 下午
 */

@Component
@Slf4j
public class TaskSupport {

    /**
     * 离线任务列表
     */
    @Autowired
    private OfflineTaskMapHolder offlineTaskMap;

    /**
     * 目标地址 map
     */
    @Autowired
    private TargetMapHolder targetMap;

    @Resource
    private K8sService k8sService;

    @Resource
    private TaskPersistenceService taskPersistenceService;

    @Resource
    private ParaCompiler paraCompiler;

    @Resource
    private RedisService redisService;

    @Resource
    private FileService fileService;

    @Resource
    private RedisLock redisLock;

    /**
     * job签名存根服务
     */
    @Resource
    private JobTaskStubService jobTaskStubService;



    @Value("${target}")
    private String localTarget;


    @Transactional
    public void syncJobList(String listStr) {
        final List<PreJob> preJobs = JSONArray.parseArray(listStr, PreJob.class);
        log.info("任务对象{}", preJobs);

        Job job = paraCompiler.compileList(preJobs);
        if (Objects.isNull(job)) {
            throw new CommonException("任务类型不匹配");
        }
        List<SubTask> subTaskList = job.getSubTaskList();

        final String id = job.getId();
        // 存map之前判断任务是否已经存在
        final List<SubTask> subTasks = offlineTaskMap.get(id);
        if (CollectionUtils.isNotEmpty(subTasks)) {
            int startSubId = subTasks.size();
            for (SubTask subTask : subTaskList) {
                subTask.setSubId(startSubId++);
                // 处理分段任务下子任务的subId
                subTask.getTasks().stream().forEach(y -> {
                    y.setSubId(subTask.getSubId());
                    String clusterId = y.getId() + "-" + y.getSubId() + "-" + y.getTaskIndex();
                    y.getParameters().put("cluster-id", clusterId);
                    y.setName(CommonUtils.genPodName(y, null));
                });
                // 更新map
                subTasks.add(subTask);
            }
            taskPersistenceService.insertChildrenTask(subTaskList);
        }
        else {
            // 如果内存中不存在，表中存在，代表该id对应的任务已经完成
            if (taskPersistenceService.getByTaskId(id) != null) {
                throw new CommonException("id不能重复");
            }
            offlineTaskMap.put(id, subTaskList);
            taskPersistenceService.insertAllTask(job.getType(), subTaskList, null);
        }
        offlineTaskMap.persistence();
    }

    /**
     * 增加签名和证书存根
     *
     * @param preJson 任务详情，也是被签名内容
     * @param jobDistributorSign 任务发起方签名
     * @param jobDistributorCert 任务发起方证书
     * @param jobExecutorSign 任务执行方签名
     * @param jobExecutorCert 任务执行方证书
     * @param isLocal 判断任务来源，如果发起方是自身则为1，如果是接收的外部任务则为0
     */
    @Transactional
    public void syncJobJson(String preJson, String jobDistributorSign, String jobDistributorCert,
            String jobExecutorSign, String jobExecutorCert, Byte isLocal) {
        PreJob preJob = GsonUtil.changeGsonToBean(preJson, PreJob.class);
        log.info("任务对象{}", preJob);

        // 解析任务列表
        String id = preJob.getId();
        if (taskPersistenceService.getByTaskId(id) != null) {
            throw new CommonException("id不能重复");
        }
        Job job = paraCompiler.compile(preJob);
        List<SubTask> subTaskList = job.getSubTaskList();
        String taskJson = GsonUtil.createGsonString(subTaskList.get(0));
        offlineTaskMap.put(id, subTaskList);
        taskPersistenceService.insertAllTask(job.getType(), subTaskList, taskJson);

        // offlineTaskMap内容有更新，需要持久化到redis
        offlineTaskMap.persistence();

        // 保存任务存证-包含任务详情和各方签名
        JobTaskStub jobTaskStub = new JobTaskStub();
        jobTaskStub.setParentTaskId(id);
        jobTaskStub.setPreJobJson(preJson);
        jobTaskStub.setJobTarget(localTarget);
        jobTaskStub.setJobDistributorSign(jobDistributorSign);
        jobTaskStub.setJobDistributorCert(jobDistributorCert);
        jobTaskStub.setJobExecutorSign(jobExecutorSign);
        jobTaskStub.setJobExecutorCert(jobExecutorCert);
        jobTaskStub.setIsLocal(isLocal);
        jobTaskStub.setCreateAt(LocalDateTime.now());
        jobTaskStub.setUpdateAt(LocalDateTime.now());
        jobTaskStub.setIsDeleted(IsDeletedEnum.FALSE.getStatus());
        jobTaskStubService.insert(jobTaskStub);
    }

    /**
     * 构造offlineTaskMap
     *
     * @param preJson
     */
    public void putOfflineTaskMap(String preJson) {
        PreJob preJob = GsonUtil.changeGsonToBean(preJson, PreJob.class);
        log.info("putOfflineTaskMap 任务对象:{}", preJob);
        // 解析任务列表
        String id = preJob.getId();
        Job job = paraCompiler.compile(preJob);
        List<SubTask> subTaskList = job.getSubTaskList();

        offlineTaskMap.put(id, subTaskList);
        log.info("service start init offlineTaskMap size:{}", offlineTaskMap.size());
        offlineTaskMap.persistence();
    }

    /**
     * 提交任务
     *
     * @param id 父id
     * @param subId 子id
     */
    public void commit(String id, Integer subId) {
        SubTask subTask = offlineTaskMap.get(id).get(subId);
        if (Objects.equals(subTask.getStatus(), TaskStatusEnum.NEW.getStatus())
                || Objects.equals(subTask.getStatus(), TaskStatusEnum.ERROR.getStatus())) {
            k8sService.commit(subTask);
        }
    }

    /**
     * 获得最大已完成子任务id
     *
     * @param id 父id
     * @return 最大已完成子任务id
     */
    public Integer getMaxCompletedTaskId(String id) {
        int maxIndex = -1;
        if (!offlineTaskMap.isEmpty() && offlineTaskMap.containsKey(id)) {
            maxIndex = offlineTaskMap.get(id).stream()
                    .filter(subTask -> Objects.equals(subTask.getStatus(),
                            TaskStatusEnum.COMPLETED.getStatus()))
                    .map(SubTask::getSubId).max(Comparator.comparingLong(a -> a)).orElse(-1);
        }
        return maxIndex;
    }

    /**
     * 任务是否结束
     *
     * @param id 父id
     * @return 是否结束
     */
    public Boolean isFinished(String id) {
        boolean flag = true;
        if (!offlineTaskMap.isEmpty() && offlineTaskMap.containsKey(id)) {
            long count = offlineTaskMap.get(id).stream().filter(subTask -> !Objects
                    .equals(subTask.getStatus(), TaskStatusEnum.COMPLETED.getStatus())).count();
            log.info("父任务:{},未完成子任务个数:{}", id, count);
            flag = count == 0;
        }
        return flag;
    }

    /**
     * 结束任务
     *
     * @param id 父id
     */
    @Transactional
    public void finishTask(String id) {

        // 持久化并删除key
        try {
            taskPersistenceService.updateParentTaskStatus(id, TaskStatusEnum.COMPLETED.getStatus());
            this.deleteTask(id, TaskStatusEnum.COMPLETED.getStatus());
            offlineTaskMap.remove(id);
            targetMap.remove(id);
            log.info("删除父任务{}:", id);
            offlineTaskMap.persistence();
            targetMap.persistence();
        }
        catch (Exception e) {
            log.error("持久化数据报错，任务id{}", id);
        }
    }

    // lr只会发送一次完成请求
    public void errorAll(String id, Integer status) {
        // 更新数据库
        offlineTaskMap.get(id)
                .forEach(subTask -> subTask.getTasks()
                        .forEach(offlineTask -> taskPersistenceService.updateChildrenTaskResult(id,
                                subTask.getSubId(), offlineTask.getTaskIndex(),
                                GsonUtil.jackSonString(OperatorStatusEnum.getByCode(status)), null,
                                TaskStatusEnum.ERROR.getStatus())));
        taskPersistenceService.updateParentTaskStatus(id, TaskStatusEnum.ERROR.getStatus());
        // 更新内存
        offlineTaskMap.remove(id);
        targetMap.remove(id);
        // 删除pod
        k8sService.deleteDeploymentById(id);
        offlineTaskMap.persistence();
        targetMap.persistence();
    }

    public List<Pod> getPodInfo(String pattern) {
        return k8sService.getPodListByName(pattern);
    }

    public GrpcResourceLimitResult evalPodResource(String id) {
        List<Pod> pods = k8sService.getPodListByName(id);
        if (CollectionUtils.isEmpty(pods)) {
            return null;
        }
        // 不存在deployment,属于crd的方式,暂不处理
        List<Deployment> deployments = k8sService.getDeploymentList(id);
        if (CollectionUtils.isEmpty(deployments)) {
            return null;
        }
        GrpcResourceLimitResult limitResult = new GrpcResourceLimitResult();
        limitResult.setId(id);
        if (this.isPending(pods)) {
            limitResult.setPendFlag(true);
        }
        if (this.isOom(pods)) {
            limitResult.setOomFlag(true);
        }
        Deployment deployment = deployments.get(0);
        String[] split = deployment.getMetadata().getName().split("-");
        if (split.length < 3) {
            return null;
        }
        try {
            limitResult.setSubId(Integer.valueOf(split[split.length - 3]));
        }
        catch (Exception e) {
            return null;
        }
        return limitResult;
    }

    private boolean isPending(List<Pod> pods) {
        for (Pod pod : pods) {
            try {
                if (pod.getStatus().getStartTime() != null && System.currentTimeMillis() - DateUtils
                        .parseDate(pod.getStatus().getStartTime(), "yyyy-MM-dd'T'HH:mm:ssXX")
                        .getTime() < 60 * 1000) {
                    continue;
                }
            }
            catch (ParseException e) {

            }
            if (pod.getStatus().getPhase().equals("Pending")) {
                return true;
            }
        }
        return false;
    }

    private boolean isOom(List<Pod> pods) {
        for (Pod pod : pods) {
            if (CollectionUtils.isEmpty(pod.getStatus().getContainerStatuses())) {
                continue;
            }
            ContainerStatus lastContainerStatus = pod.getStatus().getContainerStatuses().get(0);
            if (lastContainerStatus.getLastState() != null
                    && lastContainerStatus.getLastState().getTerminated() != null
                    && "OOMKilled".equals(
                            lastContainerStatus.getLastState().getTerminated().getReason())) {
                return true;
            }
        }
        return false;
    }

    public void handlePodResource(GrpcResourceLimitResult limitResult) {
        List<SubTask> subTasks = offlineTaskMap.get(limitResult.getId());
        SubTask stask = null;
        for (SubTask subTask : subTasks) {
            if (subTask.getSubId().equals(limitResult.getSubId())) {
                stask = subTask;
                break;
            }
        }
        // 删除deployment
        k8sService.deleteDeploymentById(limitResult.getId());
        // 修改资源
        if (limitResult.isPendFlag()) {
            this.handlePending(stask);
        }
        if (limitResult.isOomFlag()) {
            this.handleOom(stask);
        }
        // 重启
        log.info("resourceLimit:" + GsonUtil.createGsonString(stask));
        k8sService.commit(stask);
    }

    private void handlePending(SubTask subTask) {
        // 达到最大重启次数,放弃重启
        log.info("subTask:" + GsonUtil.createGsonString(subTask));
        if (subTask.getResourceLimitPolicy().getRestartCount() >= subTask.getResourceLimitPolicy()
                .getMaxRestartCount()) {
            return;
        }
        for (OfflineTask task : subTask.getTasks()) {
            // 减半或者为最小值
            if (task.getCpu() > subTask.getResourceLimitPolicy().getMinCpu()) {
                task.setCpu(task.getCpu() / 2 > subTask.getResourceLimitPolicy().getMinCpu()
                        ? task.getCpu() / 2
                        : subTask.getResourceLimitPolicy().getMinCpu());
            }
            if (task.getMemory() > subTask.getResourceLimitPolicy().getMinMemory()) {
                task.setMemory(
                        task.getMemory() / 2 > subTask.getResourceLimitPolicy().getMinMemory()
                                ? task.getMemory() / 2
                                : subTask.getResourceLimitPolicy().getMinMemory());
            }
        }
    }

    private void handleOom(SubTask subTask) {
        // 达到最大重启次数,放弃重启
        if (subTask.getResourceLimitPolicy().getRestartCount() >= subTask.getResourceLimitPolicy()
                .getMaxRestartCount()) {
            return;
        }
        for (OfflineTask task : subTask.getTasks()) {
            // double或者为最大值
            if (task.getCpu() < subTask.getResourceLimitPolicy().getMaxCpu()) {
                task.setCpu(task.getCpu() * 2 < subTask.getResourceLimitPolicy().getMaxCpu()
                        ? task.getCpu() * 2
                        : subTask.getResourceLimitPolicy().getMaxCpu());
            }
            if (task.getMemory() < subTask.getResourceLimitPolicy().getMaxMemory()) {
                task.setMemory(
                        task.getMemory() * 2 < subTask.getResourceLimitPolicy().getMaxMemory()
                                ? task.getMemory() * 2
                                : subTask.getResourceLimitPolicy().getMaxMemory());
            }
        }
    }
    /**
     * 停止任务
     *
     * @param id 父id
     */
    @Transactional
    public Boolean stopTask(String id) {
        if (!offlineTaskMap.isEmpty() && offlineTaskMap.get(id) != null) {
            CommonUtils
                    .isEmpty(offlineTaskMap.get(id)).stream().filter(subTask -> !Objects
                            .equals(subTask.getStatus(), TaskStatusEnum.COMPLETED.getStatus()))
                    .forEach(subTask -> {
                        subTask.getTasks().forEach(offlineTask -> {
                            taskPersistenceService.updateChildrenTaskStatus(id, subTask.getSubId(),
                                    offlineTask.getTaskIndex(), TaskStatusEnum.STOPPED.getStatus());
                        });
                    });
            taskPersistenceService.updateParentTaskStatus(id, TaskStatusEnum.STOPPED.getStatus());
            offlineTaskMap.remove(id);
            // 持久化并删除key
            this.deleteTask(id, TaskStatusEnum.STOPPED.getStatus());
            // offlineTaskMap内容有更新，需要持久化到redis add by yezhenyue on 20220408
            offlineTaskMap.persistence();
        }
        return true;
    }

    /**
     * 删除任务
     *
     * @param id 任务id
     * @param status 任务状态
     */
    public void deleteTask(String id, Integer status) {
        // redis持久化
        Set<String> keys = redisService.scans(id + "-", 1000);
        log.info("id:" + id);
        log.info("keys:" + GsonUtil.createGsonString(keys));
        if (!keys.isEmpty()) {
            keys.stream()
                    .collect(Collectors.groupingBy(key -> key.substring(0, key.lastIndexOf("-"))))
                    .forEach((clusterId, value) -> {
                        String message = value.stream()
                                .filter(key -> redisService.hget(key, "message") != null)
                                .map(key -> redisService.hget(key, "message").toString())
                                .collect(Collectors.joining("\n"));
                        String result = value.stream()
                                .filter(key -> redisService.hget(key, "result") != null)
                                .map(key -> redisService.hget(key, "result").toString())
                                .collect(Collectors.joining("\n"));
                        String[] split = clusterId.split("-");
                        Integer subId = Integer.parseInt(split[1]);
                        Integer taskIndex = Integer.parseInt(split[2]);
                        taskPersistenceService.updateChildrenTaskResult(id, subId, taskIndex,
                                message, result, status);
                    });
            // 删除redis key
            redisService.delete(keys);
        }
        // 删除k8s pod
        k8sService.deleteDeploymentById(id);
    }
    /**
     * 查询任务状态
     *
     * @param id 任务id
     * @return 任务状态
     */
    public String queryTask(String id) {
        ParentTask parentTask = taskPersistenceService.getByTaskId(id);
        if (parentTask == null) {
            throw new CommonException("任务不存在！");
        }
        TaskStatusInfo info = new TaskStatusInfo();
        info.setCustomerId(localTarget);
        info.setTarget(localTarget);
        info.setTaskStatus(parentTask.getStatus());
        info.setRunTime(Duration.between(parentTask.getCreateAt(), LocalDateTime.now()).toMillis());
        Set<String> keys = redisService.scans(id, 1000);
        if (keys.isEmpty()) {
            info.setPercent(100);
        }
        else {
            info.setPercent(50);
        }
        return GsonUtil.createGsonString(info);
    }

    public String getChidTasks(String id) {
        if (!offlineTaskMap.containsKey(id)) {
            throw new CommonException("任务不存在！");
        }
        return GsonUtil.createGsonString(offlineTaskMap.get(id));
    }

}
