package com.jd.mpc.grpc;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.annotation.Resource;

import cn.hutool.core.io.IoUtil;
import com.alibaba.fastjson.JSONObject;
import com.jd.mpc.common.enums.StoreTypeEnum;
import com.jd.mpc.domain.param.*;
import com.jd.mpc.domain.vo.EtlHeaderParam;
import com.jd.mpc.service.FileService;
import org.apache.commons.io.IOUtils;

import com.jd.mpc.common.util.GsonUtil;
import com.jd.mpc.domain.vo.PredictResult;
import com.jd.mpc.domain.vo.ProxyInfo;
import com.jd.mpc.service.OuterSupport;

import io.grpc.stub.StreamObserver;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;
import outer.GrpcOuterRequest;
import outer.GrpcOuterResponse;
import outer.OuterServiceGrpc;

/**
 * grpc外部任务服务端
 *
 * @author luoyuyufei1
 * @date 2021/9/22 2:45 下午
 */
@Slf4j
@GrpcService
public class GrpcOuterService extends OuterServiceGrpc.OuterServiceImplBase {

    @Resource
    private OuterSupport outerSupport;
    @Resource
    private FileService fileService;

    @Override
    public void syncDataInfo(GrpcOuterRequest request, StreamObserver<GrpcOuterResponse> observer) {
        GrpcOuterResponse build = GrpcOuterResponse.newBuilder()
                .setRes(outerSupport.syncDataInfo(request.getDataInfoMap())).build();
        // onNext()方法向客户端返回结果
        observer.onNext(build);
        // 告诉客户端这次调用已经完成
        observer.onCompleted();
    }

    @Override
    public void uploadFile(GrpcOuterRequest request, StreamObserver<GrpcOuterResponse> observer) {
        log.info("reqrecv-uploadfile:" + request.getFileName());
        String res = outerSupport.uploadFile(request.getFileName(),
                IoUtil.toStream(request.getByteArr().toByteArray()),request.getId(), StoreTypeEnum.valueOf(request.getStoreType()), request.getBdpAccount());
        log.info("uploadend-uploadfile:" + request.getFileName());
        GrpcOuterResponse build = GrpcOuterResponse.newBuilder().setRes(res).build();
        // onNext()方法向客户端返回结果
        observer.onNext(build);
        // 告诉客户端这次调用已经完成
        observer.onCompleted();
    }

    @Override
    public void getFileSizeInfo(GrpcOuterRequest request,
            StreamObserver<GrpcOuterResponse> observer) {
        String res = outerSupport.getFileSizeInfo(request.getFilePath(),request.getBdpAccount(),StoreTypeEnum.valueOf(request.getStoreType()));
        GrpcOuterResponse build = GrpcOuterResponse.newBuilder().setRes(res).build();
        // onNext()方法向客户端返回结果
        observer.onNext(build);
        // 告诉客户端这次调用已经完成
        observer.onCompleted();
    }

    @Override
    public void getFileSchemaInfo(GrpcOuterRequest request,
            StreamObserver<GrpcOuterResponse> observer) {
        String res = outerSupport.getFileSchemaInfo(request.getFilePath());
        GrpcOuterResponse build = GrpcOuterResponse.newBuilder().setRes(res).build();
        // onNext()方法向客户端返回结果
        observer.onNext(build);
        // 告诉客户端这次调用已经完成
        observer.onCompleted();
    }


    @Override
    public void getJobLogs(GrpcOuterRequest request, StreamObserver<GrpcOuterResponse> observer) {
        String res = outerSupport.getJobLogs(request.getId());
        GrpcOuterResponse build = GrpcOuterResponse.newBuilder().setRes(res).build();
        // onNext()方法向客户端返回结果
        observer.onNext(build);
        // 告诉客户端这次调用已经完成
        observer.onCompleted();
    }

    @Override
    public void getJobResults(GrpcOuterRequest request,
            StreamObserver<GrpcOuterResponse> observer) {
        String res = outerSupport.getJobResults(request.getId());
        GrpcOuterResponse build = GrpcOuterResponse.newBuilder().setRes(res).build();
        // onNext()方法向客户端返回结果
        observer.onNext(build);
        // 告诉客户端这次调用已经完成
        observer.onCompleted();
    }

    @Override
    public void getUsedResources(GrpcOuterRequest request,
            StreamObserver<GrpcOuterResponse> observer) {
        String res = outerSupport.getUsedResources(request.getId());
        GrpcOuterResponse build = GrpcOuterResponse.newBuilder().setRes(res).build();
        // onNext()方法向客户端返回结果
        observer.onNext(build);
        // 告诉客户端这次调用已经完成
        observer.onCompleted();
    }

    @Override
    public void getResourcesInfo(GrpcOuterRequest request,
            StreamObserver<GrpcOuterResponse> observer) {
        String res = outerSupport.getResourcesInfo();
        GrpcOuterResponse build = GrpcOuterResponse.newBuilder().setRes(res).build();
        // onNext()方法向客户端返回结果
        observer.onNext(build);
        // 告诉客户端这次调用已经完成
        observer.onCompleted();
    }

    @Override
    public void getPredictNum(GrpcOuterRequest request,
            StreamObserver<GrpcOuterResponse> observer) {
        List<PredictResult> res = outerSupport.getPredictNum(request.getId());
        GrpcOuterResponse build = GrpcOuterResponse.newBuilder()
                .setRes(GsonUtil.createGsonString(res)).build();
        // onNext()方法向客户端返回结果
        observer.onNext(build);
        // 告诉客户端这次调用已经完成
        observer.onCompleted();
    }

    @Override
    public void addProxy(GrpcOuterRequest request, StreamObserver<GrpcOuterResponse> observer) {
        outerSupport.addProxy(GsonUtil.changeGsonToBean(request.getJson(), ProxyInfo.class));
        GrpcOuterResponse build = GrpcOuterResponse.newBuilder().build();
        // onNext()方法向客户端返回结果
        observer.onNext(build);
        // 告诉客户端这次调用已经完成
        observer.onCompleted();
    }

    @Override
    public void getFileHeader(GrpcOuterRequest request,
            StreamObserver<GrpcOuterResponse> observer) {
        String res = outerSupport.getFileHeader(request.getFilePath());
        GrpcOuterResponse build = GrpcOuterResponse.newBuilder().setRes(res).build();
        // onNext()方法向客户端返回结果
        observer.onNext(build);
        // 告诉客户端这次调用已经完成
        observer.onCompleted();
    }

    @Override
    public void getFile(GrpcOuterRequest request, StreamObserver<GrpcOuterResponse> observer) {
        String res = "";
        GrpcOuterResponse build = GrpcOuterResponse.newBuilder().setRes(res).build();
        // onNext()方法向客户端返回结果
        observer.onNext(build);
        // 告诉客户端这次调用已经完成
        observer.onCompleted();
    }

    @Override
    public void predict(GrpcOuterRequest request, StreamObserver<GrpcOuterResponse> observer) {
        String res = outerSupport.predict(request.getId(), request.getData());
        GrpcOuterResponse build = GrpcOuterResponse.newBuilder().setRes(res).build();
        // onNext()方法向客户端返回结果
        observer.onNext(build);
        // 告诉客户端这次调用已经完成
        observer.onCompleted();
    }

    @Override
    public void test(GrpcOuterRequest request, StreamObserver<GrpcOuterResponse> observer) {
        GrpcOuterResponse build = GrpcOuterResponse.newBuilder()
                .setTest("outer grpc success,response: " + request.getTest()).build();
        // onNext()方法向客户端返回结果
        observer.onNext(build);
        // 告诉客户端这次调用已经完成
        observer.onCompleted();
    }

    @Override
    public void getFileSize(GrpcOuterRequest request, StreamObserver<GrpcOuterResponse> observer) {
        String res = outerSupport.getFileSize(request.getFilePath());
        GrpcOuterResponse build = GrpcOuterResponse.newBuilder().setRes(res).build();
        // onNext()方法向客户端返回结果
        observer.onNext(build);
        // 告诉客户端这次调用已经完成
        observer.onCompleted();
    }

    @Override
    public void mkdir(GrpcOuterRequest request, StreamObserver<GrpcOuterResponse> observer) {
        String res = outerSupport.mkdir(Arrays.asList(request.getFilePath().split(",")),request.getBdpAccount(),StoreTypeEnum.valueOf(request.getStoreType()));
        GrpcOuterResponse build = GrpcOuterResponse.newBuilder().setRes(res).build();
        // onNext()方法向客户端返回结果
        observer.onNext(build);
        // 告诉客户端这次调用已经完成
        observer.onCompleted();
    }

    @Override
    public void exist(GrpcOuterRequest request, StreamObserver<GrpcOuterResponse> observer) {
        String res = outerSupport.exist(request.getFilePath(),request.getBdpAccount(),StoreTypeEnum.valueOf(request.getStoreType()));
        GrpcOuterResponse build = GrpcOuterResponse.newBuilder().setRes(res).build();
        // onNext()方法向客户端返回结果
        observer.onNext(build);
        // 告诉客户端这次调用已经完成
        observer.onCompleted();
    }

    @Override
    public void closeInstance(GrpcOuterRequest request, StreamObserver<GrpcOuterResponse> responseObserver) {
        String res = outerSupport.closeInstance(request.getId());
        GrpcOuterResponse build = GrpcOuterResponse.newBuilder().setRes(res).build();
        // onNext()方法向客户端返回结果
        responseObserver.onNext(build);
        // 告诉客户端这次调用已经完成
        responseObserver.onCompleted();
    }

    @Override
    public void setCustomerIdUrl(GrpcOuterRequest request, StreamObserver<GrpcOuterResponse> responseObserver) {
        outerSupport.setCustomerIdUrl(request.getCustomerId(),request.getCustomerIdUrl());
        GrpcOuterResponse build = GrpcOuterResponse.newBuilder().build();
        // onNext()方法向客户端返回结果
        responseObserver.onNext(build);
        // 告诉客户端这次调用已经完成
        responseObserver.onCompleted();
    }


    @Override
    public void deployIsExist(GrpcOuterRequest request, StreamObserver<GrpcOuterResponse> responseObserver) {
        boolean res = outerSupport.deployIsExist(JSONObject.parseObject(request.getJson(), ExistParam.class));
        GrpcOuterResponse build = GrpcOuterResponse.newBuilder().setRes(String.valueOf(res)).build();
        // onNext()方法向客户端返回结果
        responseObserver.onNext(build);
        // 告诉客户端这次调用已经完成
        responseObserver.onCompleted();
    }

    @Override
    public void getInstance(GrpcOuterRequest request, StreamObserver<GrpcOuterResponse> responseObserver) {
        String response = outerSupport.getInstance(request.getData());
        GrpcOuterResponse build = GrpcOuterResponse.newBuilder().setRes(response).build();
        // onNext()方法向客户端返回结果
        responseObserver.onNext(build);
        // 告诉客户端这次调用已经完成
        responseObserver.onCompleted();
    }

    @Override
    public void getRawDataFiles(GrpcOuterRequest request, StreamObserver<GrpcOuterResponse> responseObserver) {
        String response = outerSupport.getRawDataFiles(request.getFilePath(), JSONObject.parseArray(request.getJson()).toJavaList(String.class));
        GrpcOuterResponse build = GrpcOuterResponse.newBuilder().setRes(response).build();
        // onNext()方法向客户端返回结果
        responseObserver.onNext(build);
        // 告诉客户端这次调用已经完成
        responseObserver.onCompleted();
    }

    @Override
    public void getNodeLog(GrpcOuterRequest request, StreamObserver<GrpcOuterResponse> observer) {
        GetNodeLogParam getNodeLogParam = JSONObject.parseObject(request.getJson(), GetNodeLogParam.class);
        String res = outerSupport.getNodeLog(getNodeLogParam.getCoordinateTaskId(), getNodeLogParam.getLogLevel(), getNodeLogParam.getNodeId(), getNodeLogParam.getFrom(), getNodeLogParam.getSize());
        GrpcOuterResponse build = GrpcOuterResponse.newBuilder().setRes(res).build();
        // onNext()方法向客户端返回结果
        observer.onNext(build);
        // 告诉客户端这次调用已经完成
        observer.onCompleted();
    }

    public void getFileServiceLog(GrpcOuterRequest request, StreamObserver<GrpcOuterResponse> observer) {
        GetFileServiceLogParam getFileServiceLogParam = JSONObject.parseObject(request.getJson(), GetFileServiceLogParam.class);
        String res = "";
        GrpcOuterResponse build = GrpcOuterResponse.newBuilder().setRes(res).build();
        // onNext()方法向客户端返回结果
        observer.onNext(build);
        // 告诉客户端这次调用已经完成
        observer.onCompleted();
    }

    public void getCoordinatorLog(GrpcOuterRequest request, StreamObserver<GrpcOuterResponse> observer) {
        GetCoordinatorLogParam getCoordinatorLogParam = JSONObject.parseObject(request.getJson(), GetCoordinatorLogParam.class);
        String res = "";
        GrpcOuterResponse build = GrpcOuterResponse.newBuilder().setRes(res).build();
        // onNext()方法向客户端返回结果
        observer.onNext(build);
        // 告诉客户端这次调用已经完成
        observer.onCompleted();
    }

    public void getNamespace(GrpcOuterRequest request, StreamObserver<GrpcOuterResponse> observer) {
        String nameSpace = outerSupport.getNamespace();
        GrpcOuterResponse build = GrpcOuterResponse.newBuilder().setRes(nameSpace).build();
        // onNext()方法向客户端返回结果
        observer.onNext(build);
        // 告诉客户端这次调用已经完成
        observer.onCompleted();
    }


}
