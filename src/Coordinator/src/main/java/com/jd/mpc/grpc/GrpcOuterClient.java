package com.jd.mpc.grpc;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import jakarta.annotation.Resource;

import cn.hutool.core.io.IoUtil;
import com.alibaba.fastjson.JSONObject;
import com.google.protobuf.ByteString;
import com.jd.mpc.common.enums.StoreTypeEnum;
import com.jd.mpc.domain.param.ExistParam;
import com.jd.mpc.domain.param.GetConfigParam;
import com.jd.mpc.domain.vo.*;
import com.jd.mpc.redis.RedisService;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.jd.mpc.common.util.GsonUtil;
import com.jd.mpc.service.OuterSupport;

import lombok.extern.slf4j.Slf4j;
import outer.GrpcOuterRequest;
import outer.GrpcOuterResponse;

/**
 * 调用jd Grpc服务
 *
 * 
 * @date 2021/9/26 10:30 下午
 */
@Component
@Slf4j
public class GrpcOuterClient {

    @Resource
    private GrpcClient grpcClient;

    @Resource
    private OuterSupport outerSupport;

    @Value("${target}")
    private String localTarget;
    @Resource
    private RedisService redisService;

    /**
     * 同步数据信息
     *
     * @param syncRequest 数据信息
     * @return 是否同步成功
     */
    public SyncResponse syncDataInfo(SyncRequest syncRequest) {
        SyncResponse syncResponse = new SyncResponse();
        List<SyncResInfo> list = new ArrayList<>();

        syncRequest.getInfoList().forEach(syncInfo -> {
            SyncResInfo syncResInfo = new SyncResInfo();
            syncResInfo.setCustomerId(syncInfo.getCustomerId());
            if (StringUtils.isBlank(syncInfo.getTarget())){
                syncInfo.setTarget(syncInfo.getCustomerId());
            }
            syncResInfo.setTarget(syncInfo.getTarget());
            if (localTarget.equals(syncInfo.getTarget())){
                syncResInfo.setResult(outerSupport.syncDataInfo(syncInfo.getDataInfo()));
            } else {
                GrpcOuterRequest request = GrpcOuterRequest.newBuilder()
                        .putAllDataInfo(syncInfo.getDataInfo()).build();
                GrpcClient client = grpcClient.getClient("outer", syncInfo.getTarget());
                GrpcOuterResponse response = client.getOuterStub().syncDataInfo(request);
                syncResInfo.setResult(response.getRes());
                this.closeChannel(client);
            }
            list.add(syncResInfo);
        });
        syncResponse.setResInfoList(list);

        return syncResponse;
    }

    /**
     * 上传文件
     *
     * @param originalFilename 上传的文件名
     * @param inputStream 上传的文件流
     * @param target 任务id
     * @return 文件路径
     */
    public String uploadFile(String originalFilename, InputStream inputStream, String target, String projectId, StoreTypeEnum storeType, String bdpAccount) {
        String res = "";
        try {
            GrpcOuterRequest request = GrpcOuterRequest.newBuilder().setFileName(originalFilename).setId(projectId).setStoreType(storeType.name())
                    .setBdpAccount(bdpAccount)
                    .setByteArr(ByteString.copyFrom(IoUtil.readBytes(inputStream))).build();
            log.info("reqsend-uploadfile:" + originalFilename);
            GrpcClient client = grpcClient.getClient("outer", target);
            res = client.getOuterStub().uploadFile(request).getRes();
            this.closeChannel(client);
        }
        catch (Exception e) {
            e.printStackTrace();
        }
        return res;
    }

    /**
     * 获取文件大小
     *
     * @param filePath 文件路径
     * @param target 任务id
     * @return 结果
     */
    public String getFileSizeInfo(String filePath, String target,String bdpAccount,StoreTypeEnum storeType) {
        GrpcOuterRequest request = GrpcOuterRequest.newBuilder().setFilePath(filePath).setBdpAccount(bdpAccount).setStoreType(storeType.name()).build();
        GrpcClient client = grpcClient.getClient("outer", target);
        String res = client.getOuterStub().getFileSizeInfo(request).getRes();
        this.closeChannel(client);
        return res;
    }

    /**
     * 获取文件列信息
     *
     * @param filePath 文件路径
     * @param target 任务id
     * @return 结果
     */
    public String getFileSchemaInfo(String filePath, String target) {
        GrpcOuterRequest request = GrpcOuterRequest.newBuilder().setFilePath(filePath).build();
        GrpcClient client = grpcClient.getClient("outer", target);
        String res = client.getOuterStub().getFileSchemaInfo(request).getRes();
        this.closeChannel(client);
        return res;
    }



    /**
     * 查询用户k8s资源量
     *
     * @param target 客户id
     * @return k8s资源量
     */
    public String getResourcesInfo(String target) {
        GrpcOuterRequest request = GrpcOuterRequest.newBuilder().build();
        GrpcClient client = grpcClient.getClient("outer", target);
        String res = client.getOuterStub().getResourcesInfo(request).getRes();
        this.closeChannel(client);
        return res;
    }

    /**
     * 查看算子日志
     *
     * @param id 任务id
     * @param target 任务id
     * @return 算子日志
     */
    public String getJobLogs(String id, String target) {
        GrpcOuterRequest request = GrpcOuterRequest.newBuilder().setId(id).build();
        GrpcClient client = grpcClient.getClient("outer", target);
        String res = client.getOuterStub().getJobLogs(request).getRes();
        this.closeChannel(client);
        return res;
    }

    /**
     * 查看算子结果
     *
     * @param id 任务id
     * @param target 任务id
     * @return 算子结果
     */
    public String getJobResults(String id, String target) {
        GrpcOuterRequest request = GrpcOuterRequest.newBuilder().setId(id).build();
        GrpcClient client = grpcClient.getClient("outer", target);
        String res = client.getOuterStub().getJobResults(request).getRes();
        this.closeChannel(client);
        return res;
    }

    /**
     * 查看算子占用资源
     *
     * @param target target 客户id
     * @param ids id列表
     * @return 占用资源
     */
    public String getUsedResources(String ids, String target) {
        GrpcOuterRequest request = GrpcOuterRequest.newBuilder().setId(ids).build();
        GrpcClient client = grpcClient.getClient("outer", target);
        String res = client.getOuterStub().getUsedResources(request).getRes();
        this.closeChannel(client);
        return res;
    }

    public List<PredictResult> getPredictNum(String target, String ids) {
        GrpcOuterRequest request = GrpcOuterRequest.newBuilder().setId(ids).build();
        GrpcClient client = grpcClient.getClient("outer", target);
        String res = client.getOuterStub().getPredictNum(request).getRes();
        this.closeChannel(client);
        return GsonUtil.changeGsonToList(res, PredictResult.class);
    }

    public String getFileHeader(String target, String path) {
        GrpcOuterRequest request = GrpcOuterRequest.newBuilder().setFilePath(path).build();
        GrpcClient client = grpcClient.getClient("outer", target);
        String res = client.getOuterStub().getFileHeader(request).getRes();
        this.closeChannel(client);
        return res;
    }

    public String mkdir(String target, String path,String bdpAccount,StoreTypeEnum storeType){
        GrpcOuterRequest request = GrpcOuterRequest.newBuilder().setFilePath(path).setBdpAccount(bdpAccount).setStoreType(storeType.name()).build();
        GrpcClient client = grpcClient.getClient("outer", target);
        String res = client.getOuterStub().mkdir(request).getRes();
        this.closeChannel(client);
        return res;
    }

    public String getFile(String target, String path,Integer isWholeFile) {
        GrpcOuterRequest request = GrpcOuterRequest.newBuilder().setFilePath(path).setData(String.valueOf(isWholeFile)).build();
        GrpcClient client = grpcClient.getClient("outer", target);
        String res = client.getOuterStub().getFile(request).getRes();
        this.closeChannel(client);
        return res;
    }

    public void addProxy(ProxyInfo proxyInfo) {
        GrpcOuterRequest request = GrpcOuterRequest.newBuilder()
                .setJson(GsonUtil.createGsonString(proxyInfo)).build();
        GrpcClient client = grpcClient.getClient("outer", proxyInfo.getCustomerId());
        client.getOuterStub().addProxy(request);
        this.closeChannel(client);
    }

    public String test(String test,String target) {
        GrpcOuterRequest request = GrpcOuterRequest.newBuilder().setTest(test).build();
        GrpcClient client = grpcClient.getClient("outer", target);
        GrpcOuterResponse response = client.getOuterStub().test(request);
        this.closeChannel(client);
        return response.getTest();
    }

    private void closeChannel(GrpcClient client) {
        try {
            client.getChannel().shutdown().awaitTermination(5, TimeUnit.SECONDS);
        }
        catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public String predict(String id, String data, String target) {
        GrpcOuterRequest request = GrpcOuterRequest.newBuilder().setId(id).setData(data).build();
        GrpcClient client = grpcClient.getClient("outer", target);
        GrpcOuterResponse response = client.getOuterStub().predict(request);
        this.closeChannel(client);
        return response.getRes();
    }

    public String getFileSize(String path, String target) {
        GrpcOuterRequest request = GrpcOuterRequest.newBuilder().setFilePath(path).build();
        GrpcClient client = grpcClient.getClient("outer", target);
        String res = client.getOuterStub().getFileSize(request).getRes();
        this.closeChannel(client);
        return res;
    }

    public String exist(String path, String target,String bdpAccount,StoreTypeEnum storeType) {
        GrpcOuterRequest request = GrpcOuterRequest.newBuilder().setFilePath(path).setBdpAccount(bdpAccount).setStoreType(storeType.name()).build();
        GrpcClient client = grpcClient.getClient("outer", target);
        String res = client.getOuterStub().exist(request).getRes();
        this.closeChannel(client);
        return res;
    }

    public String closeInstance(String instanceTag, String customerId) {
        GrpcOuterRequest request = GrpcOuterRequest.newBuilder().setId(instanceTag).build();
        GrpcClient client = grpcClient.getClient("outer", customerId);
        String res = client.getOuterStub().closeInstance(request).getRes();
        this.closeChannel(client);
        return res;
    }

    public String setCustomerIdUrl(String target,String customerId,String customerIdUrl){
        GrpcOuterRequest request = GrpcOuterRequest.newBuilder().setCustomerId(customerId).setCustomerIdUrl(customerIdUrl).build();
        GrpcClient client = grpcClient.getClient("outer", target);
        String res = client.getOuterStub().setCustomerIdUrl(request).getRes();
        this.closeChannel(client);
        return res;
    }


    public String deployIsExist(ExistParam existParam){
        GrpcOuterRequest request = GrpcOuterRequest.newBuilder().setJson(JSONObject.toJSONString(existParam)).build();
        GrpcClient client = grpcClient.getClient("outer", existParam.getTarget());
        String res = client.getOuterStub().deployIsExist(request).getRes();
        this.closeChannel(client);
        return res;
    }

    public String getInstance(String processTarget,String instanceID){
        GrpcOuterRequest request = GrpcOuterRequest.newBuilder().setData(instanceID).build();
        GrpcClient client = grpcClient.getClient("outer", processTarget);
        String res = client.getOuterStub().getInstance(request).getRes();
        this.closeChannel(client);
        return res;
    }

    public String getRawDataFiles(String customerId,String path,List<String> fileSuffixes){
        GrpcOuterRequest request = GrpcOuterRequest.newBuilder().setFilePath(path).setJson(JSONObject.toJSONString(fileSuffixes)).build();
        GrpcClient client = grpcClient.getClient("outer", customerId);
        String res = client.getOuterStub().getRawDataFiles(request).getRes();
        this.closeChannel(client);
        return res;
    }

    /**
     * 获取联邦算子日志
     *
     * @param target
     * @param coordinateTaskId
     * @param logLevel
     * @param nodeId
     * @param from
     * @param size
     * @return
     */
    public String getNodeLog(String target, String coordinateTaskId, String logLevel, Integer nodeId, Integer from, Integer size) {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("coordinateTaskId", coordinateTaskId);
        jsonObject.put("logLevel", logLevel);
        jsonObject.put("nodeId", nodeId);
        jsonObject.put("from", from);
        jsonObject.put("size", size);
        log.info("GrpcOuterClient getNodeLog 参数：" + jsonObject.toJSONString());

        GrpcOuterRequest request = GrpcOuterRequest.newBuilder().setJson(jsonObject.toJSONString()).build();
        GrpcClient client = grpcClient.getClient("outer", target);
        String res = client.getOuterStub().getNodeLog(request).getRes();
        this.closeChannel(client);
        return res;
    }

    /**
     * 获取fileservice日志
     *
     * @param target
     * @param fileServiceType
     * @param bdpAccount
     * @param logLevel
     * @param from
     * @param size
     * @return
     */
    public String getFileServiceLog(String target, Integer fileServiceType, String bdpAccount, String logLevel, Integer from,Integer size, String startTime, String endTime) {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("fileServiceType", fileServiceType);
        jsonObject.put("logLevel", logLevel);
        jsonObject.put("bdpAccount", bdpAccount);
        jsonObject.put("from", from);
        jsonObject.put("size", size);
        jsonObject.put("startTime", startTime);
        jsonObject.put("endTime", endTime);
        log.info("GrpcOuterClient getFileServiceLog 参数：" + jsonObject.toJSONString());

        GrpcOuterRequest request = GrpcOuterRequest.newBuilder().setJson(jsonObject.toJSONString()).build();
        GrpcClient client = grpcClient.getClient("outer", target);
        String res = client.getOuterStub().getFileServiceLog(request).getRes();
        this.closeChannel(client);
        return res;
    }

    public String getCoordinatorLog(String target, String logLevel, Integer from,Integer size, String startTime, String endTime) {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("logLevel", logLevel);
        jsonObject.put("from", from);
        jsonObject.put("size", size);
        jsonObject.put("startTime", startTime);
        jsonObject.put("endTime", endTime);
        log.info("GrpcOuterClient getCoordinatorLog 参数：" + jsonObject.toJSONString());

        GrpcOuterRequest request = GrpcOuterRequest.newBuilder().setJson(jsonObject.toJSONString()).build();
        GrpcClient client = grpcClient.getClient("outer", target);
        String res = client.getOuterStub().getCoordinatorLog(request).getRes();
        this.closeChannel(client);
        return res;
    }



    public String getNamespace(String target) {
        GrpcOuterRequest request = GrpcOuterRequest.newBuilder().build();
        GrpcClient client = grpcClient.getClient("outer", target);
        String res = client.getOuterStub().getNamespace(request).getRes();
        this.closeChannel(client);
        return res;
    }
}
