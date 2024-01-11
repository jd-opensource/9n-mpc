package com.jd.mpc.grpc;

import java.security.cert.X509Certificate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Resource;

import com.alibaba.fastjson.JSONObject;
import com.jd.mpc.cert.CertGenerator;
import com.jd.mpc.domain.offline.commons.PreJob;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;

import com.jd.mpc.common.enums.IsLocalEnum;
import com.jd.mpc.common.util.GsonUtil;
import com.jd.mpc.domain.cert.SignCertVo;
import com.jd.mpc.domain.vo.GrpcResourceLimitResult;
import com.jd.mpc.service.TaskSupport;
import com.jd.mpc.service.cert.JobCertValidateService;

import io.fabric8.kubernetes.api.model.Pod;
import io.grpc.stub.StreamObserver;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;
import offline.GrpcOfflineRequest;
import offline.GrpcOfflineResponse;
import offline.OfflineServiceGrpc;

/**
 * grpc离线任务服务
 *
 * @author luoyuyufei1
 * @date 2021/9/22 2:45 下午
 */
@Slf4j
@GrpcService
public class GrpcOfflineService extends OfflineServiceGrpc.OfflineServiceImplBase {

    @Resource
    private TaskSupport taskSupport;

    @Value("${target}")
    private String localTarget;

    @Resource
    private JobCertValidateService jobCertValidateService;

    @Override
    public void syncJobJson(GrpcOfflineRequest request,
            StreamObserver<GrpcOfflineResponse> observer) {
        GrpcOfflineResponse response = null;
        // 签名验证 update by yezhenyue on 20220402
        SignCertVo signCertVo = jobCertValidateService.validateDistributorSign(request);
        if (signCertVo == null) {// 验证不通过
            response = GrpcOfflineResponse.newBuilder().setSignVerify(false).build();
        }
        else {// 验证通过，保存任务以及任务签名存根
            taskSupport.syncJobJson(request.getJobJson(), request.getJobDistributorSign(),
                    request.getJobDistributorCert(), signCertVo.getSign(),
                    signCertVo.getCertContent(), IsLocalEnum.FALSE.getStatus());
            response = GrpcOfflineResponse.newBuilder().setSignVerify(true)
                    .setJobExecutorSign(signCertVo.getSign())
                    .setJobExecutorCert(signCertVo.getCertContent()).build();
        }
        // onNext()方法向客户端返回结果
        observer.onNext(response);
        // 告诉客户端这次调用已经完成
        observer.onCompleted();
    }

    @Override
    public void syncJobList(GrpcOfflineRequest request,
            StreamObserver<GrpcOfflineResponse> observer) {
        taskSupport.syncJobList(request.getJobJson());
        GrpcOfflineResponse response = GrpcOfflineResponse.newBuilder().build();
        // onNext()方法向客户端返回结果
        observer.onNext(response);
        // 告诉客户端这次调用已经完成
        observer.onCompleted();
    }

    @Override
    public void getMaxCompletedTaskId(GrpcOfflineRequest request,
            StreamObserver<GrpcOfflineResponse> observer) {
        Integer maxCompletedTaskId = taskSupport.getMaxCompletedTaskId(request.getId());
        GrpcOfflineResponse build = GrpcOfflineResponse.newBuilder().setMaxIndex(maxCompletedTaskId)
                .build();
        // onNext()方法向客户端返回结果
        observer.onNext(build);
        // 告诉客户端这次调用已经完成
        observer.onCompleted();
    }

    @Override
    public void getChidTasks(GrpcOfflineRequest request,
            StreamObserver<GrpcOfflineResponse> observer) {
        String res = taskSupport.getChidTasks(request.getId());
        GrpcOfflineResponse build = GrpcOfflineResponse.newBuilder().setRes(res).build();
        // onNext()方法向客户端返回结果
        observer.onNext(build);
        // 告诉客户端这次调用已经完成
        observer.onCompleted();
    }

    @Override
    public void commit(GrpcOfflineRequest request, StreamObserver<GrpcOfflineResponse> observer) {
        taskSupport.commit(request.getId(), request.getSubId());
        GrpcOfflineResponse build = GrpcOfflineResponse.newBuilder().setMessage("调用成功").build();
        // onNext()方法向客户端返回结果
        observer.onNext(build);
        // 告诉客户端这次调用已经完成
        observer.onCompleted();
    }

    @Override
    public void isFinished(GrpcOfflineRequest request,
            StreamObserver<GrpcOfflineResponse> observer) {

        boolean flag = taskSupport.isFinished(request.getId());
        GrpcOfflineResponse build = GrpcOfflineResponse.newBuilder().setFlag(flag).build();
        // onNext()方法向客户端返回结果
        observer.onNext(build);
        // 告诉客户端这次调用已经完成
        observer.onCompleted();
    }

    @Override
    public void finishTask(GrpcOfflineRequest request,
            StreamObserver<GrpcOfflineResponse> observer) {
        taskSupport.finishTask(request.getId());
        GrpcOfflineResponse build = GrpcOfflineResponse.newBuilder().build();
        // onNext()方法向客户端返回结果
        observer.onNext(build);
        // 告诉客户端这次调用已经完成
        observer.onCompleted();
    }

    @Override
    public void stopTask(GrpcOfflineRequest request, StreamObserver<GrpcOfflineResponse> observer) {
        taskSupport.stopTask(request.getId());

        // onNext()方法向客户端返回结果
        observer.onNext(GrpcOfflineResponse.newBuilder().build());
        // 告诉客户端这次调用已经完成
        observer.onCompleted();
    }

    @Override
    public void queryTask(GrpcOfflineRequest request,
            StreamObserver<GrpcOfflineResponse> observer) {
        String res = taskSupport.queryTask(request.getId());
        // onNext()方法向客户端返回结果
        observer.onNext(GrpcOfflineResponse.newBuilder().setRes(res).build());
        // 告诉客户端这次调用已经完成
        observer.onCompleted();
    }

    @Override
    public void errorAll(GrpcOfflineRequest request, StreamObserver<GrpcOfflineResponse> observer) {
        taskSupport.errorAll(request.getId(), Integer.valueOf(request.getJobJson()));
        // onNext()方法向客户端返回结果
        observer.onNext(GrpcOfflineResponse.newBuilder().setFlag(true).build());
        // 告诉客户端这次调用已经完成
        observer.onCompleted();
    }

    @Override
    public void getPodInfo(GrpcOfflineRequest request,
            StreamObserver<GrpcOfflineResponse> observer) {
        List<Pod> pods = taskSupport.getPodInfo(request.getJobJson());
        observer.onNext(GrpcOfflineResponse.newBuilder().setRes(GsonUtil.createGsonString(pods))
                .setFlag(true).build());
        observer.onCompleted();
    }

    @Override
    public void evalPodResource(GrpcOfflineRequest request,
            StreamObserver<GrpcOfflineResponse> observer) {
        GrpcResourceLimitResult limitResult = taskSupport.evalPodResource(request.getId());
        if (limitResult == null) {
            observer.onNext(GrpcOfflineResponse.newBuilder().setFlag(true).build());
        }
        else {
            observer.onNext(GrpcOfflineResponse.newBuilder()
                    .setRes(GsonUtil.createGsonString(limitResult)).setFlag(true).build());
        }
        observer.onCompleted();
    }

    @Override
    public void handlePodResource(GrpcOfflineRequest request,
            StreamObserver<GrpcOfflineResponse> observer) {
        GrpcResourceLimitResult limitResult = GsonUtil.changeGsonToBean(request.getJobJson(),
                GrpcResourceLimitResult.class);
        taskSupport.handlePodResource(limitResult);
        observer.onNext(GrpcOfflineResponse.newBuilder().setFlag(true).build());
        observer.onCompleted();
    }


    @Override
    public void heartBeat(GrpcOfflineRequest request,
            StreamObserver<GrpcOfflineResponse> observer) {
        observer.onNext(GrpcOfflineResponse.newBuilder().setFlag(true).build());
        observer.onCompleted();
    }

    @Override
    public void test(GrpcOfflineRequest request, StreamObserver<GrpcOfflineResponse> observer) {

        GrpcOfflineResponse build = GrpcOfflineResponse.newBuilder()
                .setTest("success").build();
        // onNext()方法向客户端返回结果
        observer.onNext(build);
        // 告诉客户端这次调用已经完成
        observer.onCompleted();
    }

}
