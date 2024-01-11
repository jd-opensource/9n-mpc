package com.jd.mpc.service;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.jd.mpc.common.enums.TaskStatusEnum;
import com.jd.mpc.common.enums.TaskTypeEnum;
import com.jd.mpc.common.response.CommonException;
import com.jd.mpc.common.util.GsonUtil;
import com.jd.mpc.domain.cert.SignCertVo;
import com.jd.mpc.domain.offline.commons.OfflineTask;
import com.jd.mpc.domain.offline.commons.PreJob;
import com.jd.mpc.domain.offline.commons.SubTask;
import com.jd.mpc.domain.vo.*;
import com.jd.mpc.grpc.GrpcOfflineClient;
import com.jd.mpc.redis.RedisLock;
import com.jd.mpc.redis.RedisService;
import com.jd.mpc.service.cert.JobCertValidateService;
import com.jd.mpc.storage.OfflineTaskMapHolder;
import com.jd.mpc.storage.TargetMapHolder;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.util.Strings;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 任务服务层
 *
 * @author luoyuyufei1
 * @date 2021/9/22 6:34 下午
 */
@Service
@Slf4j
public class OfflineService {

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
    private GrpcOfflineClient grpcOfflineClient;

    @Resource
    private TaskFactory taskFactory;

    @Resource
    private ParaCompiler paraCompiler;



    @Value("${target}")
    private String localTarget;


    @Autowired
    private TaskPersistenceService taskPersistenceService;

    @Resource
    private RedisService redisService;

    @Resource
    private RedisLock redisLock;

    /**
     * 证书签名相关服务
     */
    @Resource
    private JobCertValidateService jobCertValidateService;
    @Resource
    private K8sService k8sService;
    @Value("${k8s.name.prefix}")
    String k8sPrefix;

    @Value("${jdTarget}")
    private String jdTarget;

    /**
     * 连续性任务回调
     *
     * @param str
     * @return
     */
    public boolean callback(String str) {
        if (StringUtils.isBlank(str)) {
            throw new CommonException("回调任务列表不能为空");
        }
        final TaskInfo taskInfo = JSONObject.parseObject(str, TaskInfo.class);
        log.info("callback taskInfo:{}", JSONObject.toJSONString(taskInfo));
        // 处理原始数据
        taskInfo.getSubTasks().forEach(x -> {
            x.setId(taskInfo.getId());
            x.setType(taskInfo.getType());
        });
        // 提交任务
        this.commitTask(taskInfo.getSubTasks(), taskInfo.getId());
        return true;
    }

    /**
     * 提交任务
     *
     * @param subTasks 阶段任务列表
     * @param id 任务id
     * @return
     */
    private boolean commitTask(List<PreJob> subTasks, String id) {
        // 构建各自任务列表
        final Map<String, List<PreJob>> targetTasks = this.buildTargetTasks(subTasks);

        Set<String> targetSet = targetTasks.keySet();
        // 将各方任务列表构建成分段任务
        targetSet.forEach(target -> grpcOfflineClient.syncJobList(target,
                JSONArray.toJSONString(targetTasks.get(target))));
        targetSet.forEach(target -> grpcOfflineClient.commit(target, id, 0));

        // 存入目标列表
        targetMap.put(id, targetSet);
        // targetMap内容有更新，需要持久化到redis add by yezhenyue on 20220408
        targetMap.persistence();
        return true;
    }

    /**
     * 构建各方阶段任务列表
     *
     * @param subTasks
     * @return
     */
    private Map<String, List<PreJob>> buildTargetTasks(List<PreJob> subTasks) {
        Map<String, List<PreJob>> targetMap = new HashMap<>();
        for (PreJob preJob : subTasks) {
            Map<String, PreJob> map = new HashMap<>();
            preJob.getTasks().stream().collect(Collectors.groupingBy(OfflineTask::getTarget))
                    .forEach((k, v) -> {
                        PreJob job = new PreJob();
                        job.setId(preJob.getId());
                        job.setType(preJob.getType());
                        job.setPrefix(preJob.getPrefix());
                        job.setTasks(v);
                        map.put(k, job);
                    });
            for (String target : map.keySet()) {
                List<PreJob> preJobs = targetMap.get(target);
                if (CollectionUtils.isNotEmpty(preJobs)) {
                    preJobs.add(map.get(target));
                    targetMap.put(target, preJobs);
                }
                else {
                    List<PreJob> list = new ArrayList<>();
                    list.add(map.get(target));
                    targetMap.put(target, list);
                }
            }
        }
        return targetMap;
    }

    /**
     * client提交并开始任务【离线】
     *
     * @param preJson 任务json
     */
    // @Retryable(exceptionExpression = "@grpcRetryExceptionHandler.shouldRetry(#root)",backoff =
    // @Backoff(10000),maxAttempts = 10000)
    public boolean commitTask(String preJson) {

        // TODO pre 探测一下网络通信给出反馈 是否同一个任务
        PreJob preJob = GsonUtil.changeGsonToBean(preJson, PreJob.class);
        return commitK8sTask(preJob);
    }



    /**
     * 提交k8s任務
     * @param preJob
     * @return
     */
    private boolean commitK8sTask(PreJob preJob) {
        // vif需特殊解析
        if (Objects.equals(preJob.getType(), TaskTypeEnum.VIF.getName())) {
            preJob = paraCompiler.preCompileVif(preJob);
        }

        String id = preJob.getId();
        log.info("commitTask-id:" + preJob.getId());
        // 目标端列表
        Map<String, PreJob> taskMap = taskFactory.createTaskMap(preJob);
        log.info(GsonUtil.createGsonString(taskMap));
        Set<String> targetSet = taskMap.keySet();
        if(!preJob.getIsCustomer()) {
            // 添加发起方签名和证书 update by yezhenyue on 20220402 如果是京腾这种对侧为自己的coordinator 下面一步同步任务取消
            Map<String, SignCertVo> signCertVoMap = jobCertValidateService.genJobSignBatch(taskMap);
            if (signCertVoMap == null) {
                log.error("commitTask 给任务添加签名发生异常！");
                return false;
            }
            // 同步任务
            for (String target : targetSet) {
                String jobStr = GsonUtil.createGsonString(taskMap.get(target));
                boolean result = grpcOfflineClient.syncJobJson(target, jobStr,
                        signCertVoMap.get(target).getSign(),
                        signCertVoMap.get(target).getCertContent());
                if (!result) {
                    log.error("commitTask 同步任务发生错误！target:{},jobStr:{}", target, jobStr);
                    return false;
                }
            }
            // 同步成功后，提交第一个任务
            targetSet.forEach(target -> grpcOfflineClient.commit(target, id, 0));
        }


        // 存入目标列表
        targetMap.put(id, targetSet);
        // targetMap内容有更新，需要持久化到redis add by yezhenyue on 20220408
        targetMap.persistence();
        return true;
    }

    /**
     * 停止任务
     *
     * @param id 父任务id
     * @return 是否停止成功
     */
    public Boolean stopTask(String id,String customerId,String target) {
        if (StringUtils.isBlank(target)){
            target = customerId;
        }
        if (!targetMap.isEmpty() && targetMap.get(id) != null) {
            for(String item : targetMap.get(id)){
                boolean result = grpcOfflineClient.stopTask(item, id);
                if(!result){
                    log.error("target:"+item+"\tid:"+id+" stop failed!");
                    return false;
                }
            }
        }else if(!Strings.isEmpty(target)){
            boolean result = grpcOfflineClient.stopTask(target, id);
            if(!result){
                log.error("customerId:"+target+"\tid:"+id+" stop failed!");
                return false;
            }
        }
        return true;
    }

    /**
     * 根据任务id查询任务状态信息
     *
     * @param id 任务id
     * @param customerIds 任务端id列表
     * @return 任务状态信息
     */
    public List<TaskStatusInfo> queryTask(String id, String customerIds,String targets) {
        List<TaskStatusInfo> list = new ArrayList<>();
        if (StringUtils.isBlank(targets)){
            targets = customerIds;
        }
        for (String target : targets.split(",")) {
            String res = grpcOfflineClient.queryTask(target, id);
            list.add(GsonUtil.changeGsonToBean(res, TaskStatusInfo.class));
        }
        return list;
    }

    /**
     * 根据任务id查询任务信息
     *
     * @param id 任务id
     * @return 任务状态信息
     */
    public String queryTaskInfo(String id, boolean isDb) {
        if (isDb) {
            return GsonUtil.createGsonString(taskPersistenceService.getChildrenTaskList(id));
        }
        return GsonUtil.createGsonString(offlineTaskMap.get(id));
    }

    /**
     * 同步任务状态 启动剩余任务
     */
    public void startTask() {
        log.info("### Scheduled startTask begin ...");
        long start = System.currentTimeMillis();
        // 只在jd侧轮训
        if (!jdTarget.equals(localTarget) || targetMap.isEmpty()) {
            log.warn("{} not match in startTask", localTarget);
            return;
        }
        targetMap.forEach((id, targets) -> {
            try {
                // 每一侧已完成的最大子任务id
                HashSet<Integer> completedTaskIdSet = new HashSet<>();
                if (targetMap.containsKey(id)) {
                    log.info("check status targets:{}", targets);
                    targets.forEach(target -> completedTaskIdSet
                            .add(grpcOfflineClient.getMaxCompletedTaskId(target, id)));
                    List<Integer> maxCompletedTaskIdList = new ArrayList<>(completedTaskIdSet);
                    Integer maxCompletedTaskId = maxCompletedTaskIdList.get(0);
                    log.info("check status taskId:{}, maxCompletedTaskId:{}, list:{}", id,
                            maxCompletedTaskId, maxCompletedTaskIdList);
                    String target_one = targets.iterator().next();
                    String res = grpcOfflineClient.getChidTasks(target_one, id);
                    if (StringUtils.isBlank(res)) {
                        log.error("{}任务信息不存在", id);
                        throw new CommonException("任务不存在！");
                    }
                    JSONArray taskList = GsonUtil.changeGsonToBean(res, JSONArray.class);
                    // 如果子任务id相等并且还有下一个子任务未提交情况，提交子任务
                    if (maxCompletedTaskIdList.size() == 1 && maxCompletedTaskId >= 0
                            && maxCompletedTaskId < taskList.size() - 1
                            && Objects.equals(taskList.getJSONObject(maxCompletedTaskId + 1)
                                    .getInteger("status"), TaskStatusEnum.NEW.getStatus())) {
                        log.info("commit next subTask taskId:{}, subId:{}", id,
                                maxCompletedTaskId + 1);
                        targets.forEach(target -> grpcOfflineClient.commit(target, id,
                                maxCompletedTaskId + 1));
                    }
                }
            }
            catch (Exception e) {
                log.error("{}任务下阶段启动失败,错误日志{}", id, e);
            }

        });
        // offlineTaskMap.forEach((id, taskList) -> {
        // try {
        // // 每一侧已完成的最大子任务id
        // HashSet<Integer> completedTaskIdSet = new HashSet<>();
        // if (targetMap.containsKey(id)) {
        // log.info("check status targets:{}", targetMap.get(id));
        // targetMap.get(id).forEach(target -> completedTaskIdSet
        // .add(grpcOfflineClient.getMaxCompletedTaskId(target, id)));
        // List<Integer> maxCompletedTaskIdList = new ArrayList<>(completedTaskIdSet);
        // Integer maxCompletedTaskId = maxCompletedTaskIdList.get(0);
        // log.info("check status taskId:{}, maxCompletedTaskId:{}, list:{}", id,
        // maxCompletedTaskId, maxCompletedTaskIdList);
        // // 如果子任务id相等并且还有下一个子任务未提交情况，提交子任务
        // if (maxCompletedTaskIdList.size() == 1 && maxCompletedTaskId >= 0
        // && maxCompletedTaskId < taskList.size() - 1
        // && Objects.equals(taskList.get(maxCompletedTaskId + 1).getStatus(),
        // TaskStatusEnum.NEW.getStatus())) {
        // log.info("commit next subTask taskId:{}, subId:{}", id,
        // maxCompletedTaskId + 1);
        // targetMap.get(id).forEach(target -> grpcOfflineClient.commit(target, id,
        // maxCompletedTaskId + 1));
        // }
        // }
        // }
        // catch (Exception e) {
        //
        // }
        // });
        long end = System.currentTimeMillis();
        log.info("### Scheduled startTask end ... 耗时：" + (end - start) + "ms");
    }

    /**
     * 删除内存map父任务
     */
    public void finishTask() {
        // 只在jd侧轮训
        log.info("### Scheduled finishTask begin ...");
        long start = System.currentTimeMillis();
        // 只在jd侧轮训
        if (!jdTarget.equals(localTarget) || targetMap.isEmpty()) {
            log.warn("{} not match in startTask", localTarget);
            return;
        }
        List<String> idClearList = new ArrayList<>();
        for (String id : targetMap.keySet()) {
            try {
                boolean flag = true;
                for (String target : targetMap.get(id)) {
                    flag &= grpcOfflineClient.isFinished(target, id);
                    log.info("finishTask id:{}\ttarget:{}\tflag:{}", id, target, flag);
                }
                // 只有全部完成才结束任务
                if (flag) {
                    // 判断是否为循环任务，并且终止标记是否为 1
                    final Integer stopFlag = this.getStopFlag(id);
                    log.info("finishTask id:{}, stopFlag:{}", id, stopFlag);
                    if (Objects.isNull(stopFlag) || stopFlag.equals(1)) {
                        for (String target : targetMap.get(id)) {
                            grpcOfflineClient.finishTask(target, id);
                        }
                        idClearList.add(id);
                    }
                }
            }
            catch (Exception e) {
                log.error(e.getMessage(), e);
            }
        }

        for (String id : idClearList) {
            targetMap.remove(id);
        }
        if (idClearList.size() > 0) {
            // targetMap内容有更新，需要持久化到redis add by yezhenyue on 20220408
            targetMap.persistence();
        }
        long end = System.currentTimeMillis();
        log.info("### Scheduled finishTask end ... 耗时：" + (end - start) + "ms");
    }

    // pending config
    // oom config
    // retry 算子运行中断网---由算子侧上报重启状态
    // 添加过滤条件,拉取各方pod状态
    // pending oom处理 --> 不同的postHandle
    // 控制与逻辑分离
    // @Scheduled(cron = "*/60 * * * * ?")
    public void resourceHandleTask() {
        // 只在jd侧轮训
        if (!jdTarget.equals(localTarget) || targetMap.isEmpty()) {
            log.warn("{} not match in startTask", localTarget);
            return;
        }
        log.info("调起资源处理任务");
        targetMap.forEach((id, value) -> {
            // 1.获得状态
            try {
                boolean restartFlag = false;
                boolean delFlag = false;
                Map<String, GrpcResourceLimitResult> map = new HashMap<>();
                for (String target : value) {
                    try {
                        GrpcResourceLimitResult limitResult = grpcOfflineClient
                                .evalPodResource(target, id);
                        if (limitResult != null && (limitResult.isOomFlag())) {
                            restartFlag = true;
                        }
                        if (limitResult != null && (limitResult.isPendFlag())) {
                            delFlag = true;
                        }
                        map.put(target, limitResult);
                    }
                    catch (Exception e) {

                    }
                }
                // pending删除上报失败
                if (delFlag) {
                    for (String target : value) {
                        grpcOfflineClient.errorAll(target, id, 999);
                    }
                }
                // 2.删除并重启任务
                if (!restartFlag) {
                    return;
                }
                for (String target : value) {
                    if (map.get(target) != null) {
                        log.info("limitResult:" + GsonUtil.createGsonString(map.get(target)));
                        grpcOfflineClient.handlePodResource(target, map.get(target));
                    }
                }
            }
            catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    /**
     * 获取最后一个阶段任务的最后一个任务的终止标记
     */
    private Integer getStopFlag(String id) {
        // 获取阶段任务
        final List<SubTask> subTasks = offlineTaskMap.get(id);
        if (CollectionUtils.isEmpty(subTasks)) {
            return null;
        }
        final int maxSubId = subTasks.stream().mapToInt(x -> x.getSubId()).max().getAsInt();
        final Map<Integer, SubTask> subTaskMap = subTasks.stream()
                .collect(Collectors.toMap(x -> x.getSubId(), x -> x));
        // 获取最后一个阶段任务
        final SubTask subTask = subTaskMap.get(maxSubId);
        final List<OfflineTask> tasks = subTask.getTasks();
        final int maxTaskIndex = tasks.stream().mapToInt(x -> x.getTaskIndex()).max().getAsInt();
        final Map<Integer, OfflineTask> taskMap = tasks.stream()
                .collect(Collectors.toMap(x -> x.getTaskIndex(), x -> x));
        // 获取阶段任务的最后一个任务
        final OfflineTask offlineTask = taskMap.get(maxTaskIndex);
        return offlineTask.getStopFlag();
    }

}
