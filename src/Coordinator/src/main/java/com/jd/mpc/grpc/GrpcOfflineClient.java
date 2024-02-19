package com.jd.mpc.grpc;

import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import jakarta.annotation.Resource;

import com.jd.mpc.redis.RedisService;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import com.google.gson.reflect.TypeToken;
import com.jd.mpc.common.enums.IsLocalEnum;
import com.jd.mpc.common.util.GsonUtil;
import com.jd.mpc.domain.vo.GrpcResourceLimitResult;
import com.jd.mpc.service.TaskSupport;
import com.jd.mpc.service.cert.JobCertValidateService;

import io.fabric8.kubernetes.api.model.Pod;
import lombok.extern.slf4j.Slf4j;
import offline.GrpcOfflineRequest;
import offline.GrpcOfflineResponse;

/**
 * 调用jd Grpc服务
 *
 * 
 * @date 2021/9/26 10:30 下午
 */
@Slf4j
@Component
public class GrpcOfflineClient {

    @Resource
    private TaskSupport taskSupport;

    @Resource
    private GrpcClient grpcClient;

    @Resource
    private JobCertValidateService jobCertValidateService;

    @Value("${target}")
    private String localTarget;
    @Resource
    private RedisService redisService;

    /**
     * 同步阶段任务
     *
     * @param remoteTarget 目标地址
     * @param preListStr 任务列表json
     */
    public void syncJobList(String remoteTarget, String preListStr) {
        if (Objects.equals(remoteTarget, localTarget)) {
            taskSupport.syncJobList(preListStr);
        }
        else {
            log.info("target:" + remoteTarget + "\njobList:" + preListStr);
            GrpcOfflineRequest request = GrpcOfflineRequest.newBuilder().setJobJson(preListStr)
                    .build();
            GrpcClient client = grpcClient.getClient("offline", remoteTarget);
            client.getOfflineStub().syncJobList(request);
            this.closeChannel(client);
        }
    }

    /**
     * 同步任务对象
     *
     * @param remoteTarget 目标地址
     * @param jobJson 任务json
     * @param jobDistributorSign 任务发起方签名
     * @param jobDistributorCert 任务发起方证书
     *            增加签名和证书存根处理 update by yezhenyue on 20220402
     */
    public boolean syncJobJson(String remoteTarget, String jobJson, String jobDistributorSign,
            String jobDistributorCert) {
        if (redisService.equals("target:"+localTarget,"target:"+remoteTarget)) {
            taskSupport.syncJobJson(jobJson, jobDistributorSign, jobDistributorCert, null, null,
                    IsLocalEnum.TRUE.getStatus());
            return true;
        }
        else {
            log.info("target:" + remoteTarget + "\njobJson:" + jobJson);
            GrpcOfflineRequest request = GrpcOfflineRequest.newBuilder().setJobJson(jobJson)
                    .setJobDistributorSign(jobDistributorSign)
                    .setJobDistributorCert(jobDistributorCert).build();
            GrpcClient client = grpcClient.getClient("offline", remoteTarget);
            GrpcOfflineResponse grpcOfflineResponse = client.getOfflineStub().syncJobJson(request);
            // 处理返回值签名和证书信息，通过后保存到任务存证表
            boolean b = jobCertValidateService.validateExecutorSign(remoteTarget,
                    grpcOfflineResponse, jobJson, jobDistributorSign, jobDistributorCert);
            log.info("target:" + remoteTarget + " signValidate result:" + b);
            this.closeChannel(client);
            return b;
        }
    }

    /**
     * 提交k8s任务
     *
     * @param remoteTarget 目标地址
     * @param id 父任务id
     * @param subId 子任务序号
     */
    public void commit(String remoteTarget, String id, Integer subId) {
        if (redisService.equals("target:"+localTarget,"target:"+remoteTarget)) {
            taskSupport.commit(id, subId);
        }
        else {
            GrpcClient client = grpcClient.getClient("offline", remoteTarget);
            GrpcOfflineRequest request = GrpcOfflineRequest.newBuilder().setId(id).setSubId(subId)
                    .build();
            client.getOfflineStub().commit(request);
            this.closeChannel(client);
        }
    }

    /**
     * 获取父任务下完成最大值的子任务id
     *
     * @param id 父任务id
     * @return 子任务id
     */
    public Integer getMaxCompletedTaskId(String remoteTarget, String id) {
        int res;
        if (redisService.equals("target:"+localTarget,"target:"+remoteTarget)) {
            res = taskSupport.getMaxCompletedTaskId(id);
        }
        else {
            GrpcClient client = grpcClient.getClient("offline", remoteTarget);
            GrpcOfflineRequest request = GrpcOfflineRequest.newBuilder().setId(id).build();
            GrpcOfflineResponse response = client.getOfflineStub().getMaxCompletedTaskId(request);
            this.closeChannel(client);
            res = response.getMaxIndex();
        }
        return res;
    }

    /**
     * 获取子任务数
     *
     * @param id 父任务id
     * @return 子任务id
     */
    public String getChidTasks(String remoteTarget, String id) {
        String res;
        if (redisService.equals("target:"+localTarget,"target:"+remoteTarget)) {
            res = taskSupport.getChidTasks(id);
        }
        else {
            GrpcClient client = grpcClient.getClient("offline", remoteTarget);
            GrpcOfflineRequest request = GrpcOfflineRequest.newBuilder().setId(id).build();
            GrpcOfflineResponse response = client.getOfflineStub().getChidTasks(request);
            this.closeChannel(client);
            res = response.getRes();
        }
        return res;
    }

    /**
     * 子任务是否全部结束
     *
     * @param id 父任务id
     * @param remoteTarget 目标地址
     * @return 是否结束
     */
    public boolean isFinished(String remoteTarget, String id) {
        if (redisService.equals("target:"+localTarget,"target:"+remoteTarget)) {
            return taskSupport.isFinished(id);
        }
        else {
            GrpcClient client = grpcClient.getClient("offline", remoteTarget);
            GrpcOfflineRequest request = GrpcOfflineRequest.newBuilder().setId(id).build();
            GrpcOfflineResponse response = client.getOfflineStub().isFinished(request);
            this.closeChannel(client);
            return response.getFlag();
        }
    }

    /**
     * 结束任务
     *
     * @param remoteTarget 目标地址
     * @param id 父任务id
     */
    public void finishTask(String remoteTarget, String id) {
        log.info("finishTask localTarget: " + localTarget + ", remoteTarget: " + remoteTarget);
        if (redisService.equals("target:"+localTarget,"target:"+remoteTarget)) {
            taskSupport.finishTask(id);
        }
        else {
            GrpcClient client = grpcClient.getClient("offline", remoteTarget);
            GrpcOfflineRequest request = GrpcOfflineRequest.newBuilder().setId(id).build();
            client.getOfflineStub().finishTask(request);
            this.closeChannel(client);
        }
    }

    public void errorAll(String remoteTarget, String id, Integer status) {
        if (redisService.equals("target:"+localTarget,"target:"+remoteTarget)) {
            taskSupport.errorAll(id, status);
        }
        else {
            GrpcClient client = grpcClient.getClient("offline", remoteTarget);
            GrpcOfflineRequest request = GrpcOfflineRequest.newBuilder().setId(id)
                    .setJobJson(String.valueOf(status)).build();
            client.getOfflineStub().errorAll(request);
            this.closeChannel(client);
        }
    }

    public List<Pod> getPodInfo(String remoteTarget, String pattern) {
        if (redisService.equals("target:"+localTarget,"target:"+remoteTarget)) {
            return taskSupport.getPodInfo(pattern);
        }
        else {
            GrpcClient client = grpcClient.getClient("offline", remoteTarget);
            GrpcOfflineRequest request = GrpcOfflineRequest.newBuilder().setJobJson(pattern)
                    .build();
            GrpcOfflineResponse response = client.getOfflineStub().getPodInfo(request);
            this.closeChannel(client);
            return GsonUtil.changeGsonToBean(response.getRes(), new TypeToken<List<Pod>>() {
            }.getType());
        }
    }

    public GrpcResourceLimitResult evalPodResource(String remoteTarget, String id) {
        if (redisService.equals("target:"+localTarget,"target:"+remoteTarget)) {
            return taskSupport.evalPodResource(id);
        }
        else {
            GrpcClient client = grpcClient.getClient("offline", remoteTarget);
            GrpcOfflineRequest request = GrpcOfflineRequest.newBuilder().setId(id).build();
            GrpcOfflineResponse response = client.getOfflineStub().evalPodResource(request);
            this.closeChannel(client);
            return StringUtils.isBlank(response.getRes()) ? null
                    : GsonUtil.changeGsonToBean(response.getRes(), GrpcResourceLimitResult.class);
        }
    }

    public void handlePodResource(String remoteTarget, GrpcResourceLimitResult limitResult) {
        if (redisService.equals("target:"+localTarget,"target:"+remoteTarget)) {
            taskSupport.handlePodResource(limitResult);
        }
        else {
            GrpcClient client = grpcClient.getClient("offline", remoteTarget);
            GrpcOfflineRequest request = GrpcOfflineRequest.newBuilder()
                    .setJobJson(GsonUtil.createGsonString(limitResult)).build();
            GrpcOfflineResponse response = client.getOfflineStub().handlePodResource(request);
            this.closeChannel(client);
        }
    }

    /**
     * 停止任务
     *
     * @param remoteTarget 目标地址
     * @param id 父任务id
     */
    public Boolean stopTask(String remoteTarget, String id) {
        if (redisService.equals("target:"+localTarget,"target:"+remoteTarget)) {
            return taskSupport.stopTask(id);
        }
        else {
            GrpcClient client = grpcClient.getClient("offline", remoteTarget);
            GrpcOfflineRequest request = GrpcOfflineRequest.newBuilder().setId(id).build();
            client.getOfflineStub().stopTask(request);
            this.closeChannel(client);
        }
        return true;
    }



    @Resource
    private ThreadPoolTaskExecutor taskExecutor;

    public String test(String targets,Boolean isCloseChannel,Integer count,Integer threadCount,String body) {
        String[] split = targets.split(",");
        LocalDateTime startDateTime = LocalDateTime.now();
        List<Future> futures = new ArrayList<>();
        if (isCloseChannel){
            for (int m = 0; m < threadCount; m++) {
                Future<?> future = taskExecutor.submit(() -> {
                    for (String s : split) {
                        for (int i = 0; i < count / threadCount; i++) {
                            GrpcClient offline = grpcClient.getClient("offline", s);
                            this.benchmarkReq(offline, body, i);
                            this.closeChannel(offline);
                        }
                    }
                });
                futures.add(future);
            }
        }else {
            for (int m = 0; m < threadCount; m++) {
                Future<?> future = taskExecutor.submit(() -> {
                    Map<String, GrpcClient> tempClientMap = new HashMap<>();
                    for (String s : split) {
                        tempClientMap.put(s, grpcClient.getClient("offline", s));
                    }
                    for (int i = 0; i < count / threadCount; i++) {
                        for (String s : split) {
                            this.benchmarkReq(tempClientMap.get(s), body, i);
                        }
                    }
                    for (String s : split) {
                        this.closeChannel(tempClientMap.get(s));
                    }
                });
                futures.add(future);
            }
        }
        for (Future future : futures) {
            try {
                future.get();
            }catch (Exception e){
                e.printStackTrace();
            }
        }
        log.info("join finished");
        LocalDateTime endDateTime = LocalDateTime.now();

        Map<String,Object> resultMap = new HashMap<>();
        resultMap.put("costTime", Duration.between(startDateTime,endDateTime).toMillis());
        return GsonUtil.createGsonString(resultMap);
    }

    private void benchmarkReq(GrpcClient client,String body,int i){
        try {
            GrpcOfflineRequest request = GrpcOfflineRequest.newBuilder().setTest(body).build();
            client.getOfflineStub().test(request).getTest();
        } catch (Exception e) {
            log.error("index:"+i,e);
        }
    }

    public void heartBeat(Collection<String> collection) {
        for (String target : collection) {
            if (!localTarget.equals(target)) {
                GrpcClient client = grpcClient.getClient("offline", target);
                GrpcOfflineRequest request = GrpcOfflineRequest.newBuilder().build();
                client.getOfflineStub().heartBeat(request);
                this.closeChannel(client);
            }
        }
    }

    private void closeChannel(GrpcClient client) {
        try {
            client.getChannel().shutdown().awaitTermination(5, TimeUnit.SECONDS);
        }
        catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public String queryTask(String remoteTarget, String id) {
        String res;
        if (Objects.equals(remoteTarget, localTarget)) {
            res = taskSupport.queryTask(id);
        }
        else {
            GrpcClient client = grpcClient.getClient("offline", remoteTarget);
            GrpcOfflineRequest request = GrpcOfflineRequest.newBuilder().setId(id).build();
            res = client.getOfflineStub().queryTask(request).getRes();
            this.closeChannel(client);
        }
        return res;
    }

}
