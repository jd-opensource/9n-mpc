package com.jd.mpc.service;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.*;

import javax.annotation.Resource;

import com.jd.mpc.common.enums.*;
import com.jd.mpc.domain.offline.commons.SubTask;
import com.jd.mpc.domain.param.ExistParam;
import com.jd.mpc.domain.vo.*;
import com.jd.mpc.service.zeebe.Zeebes;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.jd.mpc.common.constant.CommonConstant;
import com.jd.mpc.common.response.CommonException;
import com.jd.mpc.common.util.GsonUtil;
import com.jd.mpc.common.util.HttpUtil;
import com.jd.mpc.domain.offline.commons.OfflineTask;
import com.jd.mpc.grpc.GrpcOfflineClient;
import com.jd.mpc.grpc.GrpcOuterClient;
import com.jd.mpc.redis.RedisService;
import com.jd.mpc.storage.OfflineTaskMapHolder;
import com.jd.mpc.storage.TargetMapHolder;

import lombok.extern.slf4j.Slf4j;

/**
 * 任务服务层
 *
 * 
 * @date 2021/9/22 6:34 下午
 */
@Service
@Slf4j
public class OuterService {

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
    private GrpcOuterClient grpcOuterClient;

    @Resource
    private RedisService redisService;

    @Resource
    private TaskPersistenceService taskPersistenceService;

    @Resource
    private K8sService k8sService;

    @Resource
    private GrpcOfflineClient grpcOfflineClient;

    @Resource
    private OuterSupport outerSupport;

    @Resource
    private TaskSupport taskSupport;

    @Value("${mail.url}")
    private String mailUrl;

    @Value("${mail.receivers}")
    private String[] receivers;

    @Value("${target}")
    private String localTarget;

    @Resource
    private Zeebes zeebes;

    @Value("${user.es.url}")
    private String userESUrl;

    private final static String LOG_LEVEL_ALL = "ALL";


    /**
     * 根据文件路径获得文件大小信息
     *
     * @param filePath 文件路径
     * @param customerId 客户ID
     * @return 文件信息
     */
    public String getFileSizeInfo(String target,String filePath,StoreTypeEnum storeType, String customerId,String bdpAccount) {
        String res;
        if (StringUtils.isBlank(target)){
            target = customerId;
        }
        if (localTarget.equals(target)) {
            res = outerSupport.getFileSizeInfo(filePath,bdpAccount,storeType);
        }
        else {
            res = grpcOuterClient.getFileSizeInfo(filePath, target,bdpAccount,storeType);
        }
        return res;
    }

    /**
     * 根据文件路径获得文件字段信息
     *
     * @param filePath 文件路径
     * @param customerId 客户ID
     * @return 文件信息
     */
    public String getFileSchemaInfo(String filePath, String customerId,String target) {

        String res;
        if (StringUtils.isBlank(target)){
            target = customerId;
        }
        if (localTarget.equals(target)) {
            res = outerSupport.getFileSchemaInfo(filePath);
        }
        else {
            res = grpcOuterClient.getFileSchemaInfo(filePath, customerId);
        }
        return res;
    }

    /**
     * 查询用户k8s资源量
     *
     * @param customerId 客户id
     * @return k8s资源量
     */
    public ResourcesInfo getResourcesInfo(String customerId,String target) {
        String res;
        if (StringUtils.isBlank(target)){
            target = customerId;
        }
        if (localTarget.equals(target)) {
            res = outerSupport.getResourcesInfo();
        }
        else {
            res = grpcOuterClient.getResourcesInfo(target);
        }
        return GsonUtil.changeGsonToBean(res, ResourcesInfo.class);

    }

    /**
     * 上传文件
     *
     * @param file 文件
     * @param customerId 任务id
     * @return 文件路径
     */
    public String uploadFile(String target,MultipartFile file, String customerId, String projectId, StoreTypeEnum storeType, String bdpAccount) {
        String res = "";
        try {
            log.info("recv-uploadfile:" + file.getName());
            String originalFilename = file.getOriginalFilename();
            InputStream inputStream = file.getInputStream();
            if (StringUtils.isBlank(target)){
                target = customerId;
            }
            if (localTarget.equals(target)) {
                res = outerSupport.uploadFile(originalFilename, inputStream,projectId,storeType,bdpAccount);
            }
            else {
                res = grpcOuterClient.uploadFile(originalFilename, inputStream, target,projectId,storeType,bdpAccount);
            }
        }
        catch (IOException e) {
            e.printStackTrace();
        }
        return res;
    }


    /**
     * mysql转文件回调接口
     *
     * @return 返回信息
     */
    public String callback(CallbackBody callbackBody) {
        return outerSupport.callback(callbackBody);
    }

    /**
     * 同步数据信息
     *
     * @param syncRequest 请求信息
     * @return 是否同步成功
     */
    public SyncResponse syncDataInfo(SyncRequest syncRequest) {

        return grpcOuterClient.syncDataInfo(syncRequest);
    }

    /**
     * 设置worker状态信息
     *
     * @param workerInfo worker状态信息
     * @return 是否成功
     */
    public Boolean setWorkerInfo(WorkerInfo workerInfo) {
        // 准备
        Integer newStatus = workerInfo.getStatus();
        String clusterId = workerInfo.getClusterId();
        String[] split = clusterId.split("-");
        String[] idArr = new String[split.length-2];
        System.arraycopy(split,0,idArr,0,split.length-2);
        String id = String.join("-", idArr);
        Integer subId = Integer.parseInt(split[split.length-2]);
        Integer taskIndex = Integer.parseInt(split[split.length-1]);
        log.info("id:" + id + "\nsubId:" + subId + "\ntaskIndex:" + taskIndex);
        if (workerInfo.getClusterId().contains(";")){
            // 算子化分支
            if (workerInfo.getStatus() == 0){
                zeebes.sendDoneMsg(id,workerInfo.getResult(),workerInfo.getMessage());
                k8sService.deleteDeploymentById(id);
            }else if (workerInfo.getStatus() >= 500){
                String runID = id.substring(0, id.indexOf(";"));
                zeebes.sendErrorMsg(runID,workerInfo.getResult(),workerInfo.getMessage());
            }
            return true;
        }
        Integer nodeId = workerInfo.getNodeId();
        String key = clusterId + "-" + nodeId;
        // 存入redis
        if (workerInfo.getStatus() != null) {
            redisService.hset(key, "status", workerInfo.getStatus().toString());
        }
        if (workerInfo.getMessage() != null) {
            // 追加
            Object message = redisService.hget(key, "message");
            if (workerInfo.getMessage() != null) {
                if (message != null) {
                    redisService.hset(key, "message", message + "\n" + workerInfo.getMessage());
                }
                else {
                    redisService.hset(key, "message", workerInfo.getMessage());
                }
            }
        }
        if (workerInfo.getResult() != null) {
            redisService.hset(key, "result", workerInfo.getResult());
        }
        if (workerInfo.getPercent() != null) {
            redisService.hset(key, "percent", workerInfo.getPercent().toString());
        }
        redisService.hset(key, "updateTime", LocalDateTime.now().toString());
        // modify by feiguodong1 for coor's HA
        String lockKey = "SetWorkerInfo::"+id;
        try {
            if (!redisService.tryLock(lockKey,30)){
                log.error(lockKey+" lock timeout!");
                return false;
            }
        } catch (InterruptedException e) {
            log.error(lockKey+ " lock interrupted!");
            redisService.unLock(lockKey);
            return false;
        }
        try {
            SubTask subTask = offlineTaskMap.get(id).get(subId);
            List<OfflineTask> tasks = subTask.getTasks();

            OfflineTask offlineTask = tasks.get(taskIndex);
            log.info(GsonUtil.createGsonString(offlineTask));
            // 0 已完成
            if (Objects.equals(0, newStatus)) {
                // 如果当前状态为错误，则不可以改为完成状态
                String oldStatus = redisService.hget(key, "status").toString();
                if (oldStatus != null && Integer.parseInt(oldStatus) > 0) {
                    throw new CommonException("当前状态为错误，不可以改为完成状态");
                }
                // 更新任务状态
                offlineTask.setCompletedNum(offlineTask.getCompletedNum() + 1);
                log.info(GsonUtil.createGsonString(offlineTask));
                // 当pod全部完成之后
                if (Objects.equals(offlineTask.getCompletedNum(), offlineTask.getPodNum())) {
                    if (TaskTypeEnum.FEATURE_FL.getName().equals(offlineTask.getTaskType())
                            || TaskTypeEnum.LR.getName().equals(offlineTask.getTaskType())
                            || TaskTypeEnum.HRZ_FL.getName().equals(offlineTask.getTaskType())
                            || TaskTypeEnum.NN.getName().equals(offlineTask.getTaskType())) {
                        this.completeAll(subTask);
                    }
                    else {
                        offlineTask.setStatus(TaskStatusEnum.COMPLETED.getStatus());
                        taskPersistenceService.updateChildrenTaskStatus(id, subId, taskIndex,
                                TaskStatusEnum.COMPLETED.getStatus());
                    }

                    // 判断同一阶段的任务有没有完成
                    long count = tasks.stream().filter(task -> !Objects.equals(task.getStatus(),
                            TaskStatusEnum.COMPLETED.getStatus())).count();
                    // 如果同一阶段任务全部完成
                    if (count == 0) {
                        subTask.setStatus(TaskStatusEnum.COMPLETED.getStatus());
                        if (Objects.equals(K8sResourceTypeEnum.DEPLOYMENT.getName(),
                                offlineTask.getResourcesType())) {
                            k8sService.deleteDeploymentById(id, subId);
                        }
                        else if (TaskTypeEnum.XGBOOST.getName().equals(offlineTask.getTaskType())) {
                            k8sService.deleteCrdPodById(id, subId, offlineTask.getCrdName(), 4, 3);
                        }
                        else if (TaskTypeEnum.NN.getName().equals(offlineTask.getTaskType())) {
                            k8sService.deleteCrdPodById(id, subId, offlineTask.getCrdName(), 4, 3);
                            k8sService.deleteDeploymentForCrd(id, subId,
                                    CommonConstant.NN_MPC_POD_NAME_STR);
                            k8sService.deleteServiceForCrd(id, subId,
                                    CommonConstant.NN_MPC_POD_NAME_STR);
                        }
                    }
                }
            }
            // 500 直接删除任务
            else if (newStatus >= 500) {
                log.info("任务{}-{}执行失败", clusterId, nodeId);
                if (targetMap.containsKey(id)) {
                    Set<String> set = new HashSet<>(targetMap.get(id));
                    for (String target : set) {
                        grpcOfflineClient.errorAll(target, id, newStatus);
                    }
                }
                else {
                    taskSupport.deleteTask(id, TaskStatusEnum.ERROR.getStatus());
                    taskPersistenceService.updateParentTaskStatus(id, TaskStatusEnum.ERROR.getStatus());
                }
                // 发送邮件
                // this.sendMail(id, subId, taskIndex);
            }
            // 大于0 重启任务
            else if (newStatus > 0) {
                // 重启同一阶段所有任务
                // log.info("重启任务{}-{}", id, subId);
                // targetMap.get(id).forEach(target -> grpcOfflineClient.commit(target, id, 0));
            }
            // modify by feiguodong1 for coor's HA
            offlineTaskMap.put(subTask);
            // offlineTaskMap内容有更新，需要持久化到redis add by yezhenyue on 20220408
            offlineTaskMap.persistence();
        }finally {
            redisService.unLock(lockKey);
        }

        return true;
    }

    // lr只会发送一次完成请求
    // modify by feiguodong1 for coor's HA
    private void completeAll(SubTask subTask) {
        List<OfflineTask> tasks = subTask.getTasks();
        for (OfflineTask offlineTask : tasks) {
            offlineTask.setStatus(TaskStatusEnum.COMPLETED.getStatus());
            taskPersistenceService.updateChildrenTaskStatus(subTask.getId(), subTask.getSubId(), offlineTask.getTaskIndex(),
                    TaskStatusEnum.COMPLETED.getStatus());
        }
    }

    /**
     * 发送邮件
     *
     * @param id 任务id
     * @param subId 阶段id
     * @param taskIndex 子任务id
     */
    private void sendMail(String id, Integer subId, Integer taskIndex) {
        MailInfo mailInfo = new MailInfo();
        mailInfo.setMailTo(Arrays.asList(receivers));
        mailInfo.setSubject("任务失败报警");
        mailInfo.setContent("任务：" + id + "-" + subId + "-" + taskIndex + " 发生错误，请及时处理");
        HttpUtil.post(mailUrl, GsonUtil.createGsonString(mailInfo), null, null);
    }

    /**
     * 查看算子日志
     *
     * @param id 任务id
     * @param customerId 任务端id
     * @return 算子日志
     */
    public JobInfos getJobLogs(String id, String customerId,String target) {
        String res;
        if (StringUtils.isBlank(target)){
            target = customerId;
        }
        if (localTarget.equals(target)) {
            res = outerSupport.getJobLogs(id);
        }
        else {
            res = grpcOuterClient.getJobLogs(id, target);
        }
        return GsonUtil.changeGsonToBean(res, JobInfos.class);
    }

    /**
     * 查看算子结果
     *
     * @param id 任务id
     * @param customerId 任务端id
     * @return 算子结果
     */
    public JobInfos getJobResults(String id, String customerId,String target) {
        String res;
        if (StringUtils.isBlank(target)){
            target = customerId;
        }
        if (localTarget.equals(target)) {
            res = outerSupport.getJobResults(id);
        }
        else {
            res = grpcOuterClient.getJobResults(id, target);
        }
        return GsonUtil.changeGsonToBean(res, JobInfos.class);
    }

    /**
     * 查看算子占用资源
     *
     * @param customerId customerId 客户id
     * @param ids id列表
     * @return 占用资源
     */
    public List<ResourcesInfo> getUsedResources(String customerId, String ids,String target) {
        String res;
        if (StringUtils.isBlank(target)){
            target = customerId;
        }
        if (localTarget.equals(target)) {
            res = outerSupport.getUsedResources(ids);
        }
        else {
            res = grpcOuterClient.getUsedResources(ids, target);
        }
        return GsonUtil.changeGsonToList(res, ResourcesInfo.class);
    }

    public List<PredictResult> getPredictNum(PredictQuery predictQuery) {
        List<PredictResult> res;
        if (StringUtils.isBlank(predictQuery.getTarget())){
            predictQuery.setTarget(predictQuery.getCustomerId());
        }
        String ids = predictQuery.getIds();
        if (localTarget.equals(predictQuery.getTarget())) {
            res = outerSupport.getPredictNum(ids);
        }
        else {
            res = grpcOuterClient.getPredictNum(predictQuery.getTarget(), ids);
        }
        return res;
    }

    public String getFileHeader(String path, String customerId) {
        String res;
        if (redisService.equals("target:"+localTarget,"target:"+customerId)) {
            res = outerSupport.getFileHeader(path);
        }
        else {
            res = grpcOuterClient.getFileHeader(customerId, path);
        }
        return res;
    }

    public String mkdirs(String target,String path, String customerId,String bdpAccount,StoreTypeEnum storeType) {
        String res;
        if (StringUtils.isBlank(target)){
            target = customerId;
        }
        if (localTarget.equals(target)) {
            res = outerSupport.mkdir(Arrays.asList(path.split(",")),bdpAccount,storeType);
        }
        else {
            res = grpcOuterClient.mkdir(target, path,bdpAccount,storeType);
        }
        return res;
    }

    public boolean addProxy(List<ProxyInfo> proxyInfoList) {
        proxyInfoList.forEach(proxyInfo -> {
            if (Objects.equals(localTarget, proxyInfo.getCustomerId())) {
                outerSupport.addProxy(proxyInfo);
            }
            else {
                grpcOuterClient.addProxy(proxyInfo);
            }
        });
        return true;
    }

    public String getFile(String target,String path, String customerId,Integer isWholeFile) {
        String res = "";
        return res;
    }

    /**
     * 在线预测
     *
     * @param data 预测数据
     * @param customerId 客户ID
     * @return 预测结果
     */
    public String predict(String id, String customerId, String data) {
        String res;
        if (redisService.equals("target:"+localTarget,"target:"+customerId)) {
            res = outerSupport.predict(id, data);
        }
        else {
            res = grpcOuterClient.predict(id, data, customerId);
        }
        return res;
    }

    /**
     * 获取文件大小
     *
     * @param path 文件路径
     * @param customerId 客户ID
     */
    public String getFileSize(String path, String customerId,String target) {
        String res;
        if (StringUtils.isBlank(target)){
            target = customerId;
        }
        if (localTarget.equals(target)) {
            res = outerSupport.getFileSize(path);
        }
        else {
            res = grpcOuterClient.getFileSize(path, target);
        }
        return res;
    }

    /**
     * 获取文件大小
     *
     * @param path 文件路径
     * @param customerId 客户ID
     */
    public String exist(String target,String path, String customerId,String bdpAccount,StoreTypeEnum storeType) {
        String res;
        if (StringUtils.isBlank(target)){
            target = customerId;
        }
        if (localTarget.equals(target)) {
            res = outerSupport.exist(path,bdpAccount,storeType);
        }
        else {
            res = grpcOuterClient.exist(path, target,bdpAccount,storeType);
        }
        return res;
    }

    public String closeInstance(String target,String instanceTag, String customerId) {
        String res;
        if (StringUtils.isBlank(target)){
            target = customerId;
        }
        if (localTarget.equals(target)) {
            res = outerSupport.closeInstance(instanceTag);
        }
        else {
            res = grpcOuterClient.closeInstance(instanceTag, target);
        }
        return res;
    }

    public Boolean syncTargetUrl(String customerIds,String customerId, String customerIdUrl) {
        String[] customerIdArr = customerIds.split(",");
        for (String item : customerIdArr) {
            if (localTarget.equals(item)){
                outerSupport.setCustomerIdUrl(customerId,customerIdUrl);
            }else {
                grpcOuterClient.setCustomerIdUrl(item,customerId,customerIdUrl);
            }
        }
        return Boolean.TRUE;
    }


    public boolean deployIsExist(ExistParam existParam) {
        if (localTarget.equals(existParam.getTarget())){
            return outerSupport.deployIsExist(existParam);
        }else {
            return Boolean.parseBoolean(grpcOuterClient.deployIsExist(existParam));
        }
    }

    public String getInstance(String processTarget, String instanceID) {
        if (redisService.equals("target:"+localTarget,"target:"+processTarget)){
            return outerSupport.getInstance(instanceID);
        }else {
            return grpcOuterClient.getInstance(processTarget,instanceID);
        }
    }

    public String getRawDataFiles(String target,String customerId,String path, List<String> fileSuffixes) {
        if (StringUtils.isBlank(target)){
            target = customerId;
        }
        if (localTarget.equals(target)){
            return outerSupport.getRawDataFiles(path,fileSuffixes);
        }else {
            return grpcOuterClient.getRawDataFiles(target,path,fileSuffixes);
        }
    }

    public String getNodeLog(String target, String coordinateTaskId, String logLevel,Integer nodeId,Integer from,Integer size) {
        if (localTarget.equals(target)) {
            return outerSupport.getNodeLog(coordinateTaskId, logLevel, nodeId, from, size);
        } else {
            return grpcOuterClient.getNodeLog(target, coordinateTaskId, logLevel, nodeId, from, size);
        }
    }

    public String getFileServiceLog(String target, Integer fileServiceType, String bdpAccount, String logLevel, Integer from,Integer size, String startTime, String endTime) {
        return "";
    }

    public String getCoordinatorLog(String target, String logLevel, Integer from,Integer size, String startTime, String endTime) {
        return "";
    }


    public String getNamespace(String target) {
        if (localTarget.equals(target)) {
            return outerSupport.getNamespace();
        } else {
            return grpcOuterClient.getNamespace(target);
        }
    }
}
