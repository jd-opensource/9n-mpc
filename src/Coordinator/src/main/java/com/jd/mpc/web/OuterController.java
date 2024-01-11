package com.jd.mpc.web;

import java.util.*;

import javax.annotation.Resource;

import cn.hutool.core.lang.id.NanoId;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.RandomUtil;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.google.common.collect.Maps;
import com.jd.mpc.common.enums.StoreTypeEnum;
import com.jd.mpc.common.response.ProcessCommonResponse;
import com.jd.mpc.domain.param.ExistParam;
import com.jd.mpc.domain.param.GetConfigParam;
import com.jd.mpc.domain.vo.*;
import com.jd.mpc.service.zeebe.Zeebes;
import com.jd.mpc.service.zeebe.domain.param.ProcessResultParam;
import io.camunda.zeebe.client.api.response.ProcessInstanceEvent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import com.jd.mpc.common.response.CommonResponse;
import com.jd.mpc.grpc.GrpcOuterClient;
import com.jd.mpc.service.OuterService;

import lombok.extern.slf4j.Slf4j;

/**
 * 外部接口层
 *
 * @author luoyuyufei1
 * @date 2021/9/22 6:26 下午
 */
@Slf4j
@RestController
public class OuterController {

    @Resource
    private OuterService outerService;

    @Resource
    private GrpcOuterClient grpcOuterClient;

    @Resource
    private Zeebes zeebes;


    @GetMapping(value = "/coordinator/outer/test/grpc")
    public CommonResponse<String> test(@RequestParam String test,@RequestParam("target") String target) {
        return CommonResponse.ok(grpcOuterClient.test(test,target));
    }

    /**
     * 查询预测任务的调用次数
     *
     * @return k8s资源量
     */
    @RequestMapping(value = "/coordinator/outer/get-predict-nums", method = RequestMethod.POST)
    public CommonResponse<List<PredictResult>> getPredictNum(@RequestBody PredictQuery query) {

        return CommonResponse.ok(outerService.getPredictNum(query));
    }

    /**
     * 查询用户k8s资源量
     *
     * @param customerId 客户id
     * @return k8s资源量
     */
    @RequestMapping(value = "/coordinator/outer/get-resources-info", method = RequestMethod.GET)
    public CommonResponse<ResourcesInfo> getResourcesInfo(@RequestParam String customerId,@RequestParam(required = false) String target) {

        return CommonResponse.ok(outerService.getResourcesInfo(customerId,target));
    }

    /**
     * 批量查看算子占用资源量
     *
     * @param customerId customerId 客户id
     * @param ids id列表
     * @return 占用资源
     */
    @RequestMapping(value = "/coordinator/outer/get-used-resources", method = RequestMethod.GET)
    public CommonResponse<List<ResourcesInfo>> getUsedResources(@RequestParam String customerId,
            @RequestParam String ids,@RequestParam(required = false) String target) {

        return CommonResponse.ok(outerService.getUsedResources(customerId, ids,target));
    }

    /**
     * 增加代理信息
     *
     * @param proxyInfoList 代理信息列表
     * @return 占用资源
     */
    @RequestMapping(value = "/coordinator/outer/add-proxy", method = RequestMethod.POST)
    public CommonResponse<Boolean> addProxy(@RequestBody List<ProxyInfo> proxyInfoList) {

        return CommonResponse.ok(outerService.addProxy(proxyInfoList));
    }

    /**
     * 上传文件
     *
     * @param file 文件
     * @param customerId 客户id
     * @return 文件路径
     */
    @RequestMapping(value = "/coordinator/outer/upload", method = RequestMethod.POST)
    public String uploadFile(@RequestParam(required = false) String target,@RequestPart MultipartFile file, @RequestParam String customerId, @RequestParam("projectId")String projectId, @RequestParam("storeType") StoreTypeEnum storeType,String bdpAccount) {
        return outerService.uploadFile(target,file, customerId,projectId,storeType,bdpAccount);
    }

    /**
     * 查看文件
     *
     * @param path 文件路径
     * @param customerId 客户id
     * @return 文件内容
     */
    @RequestMapping(value = "/coordinator/outer/get-file", method = RequestMethod.GET)
    public String getFile(@RequestParam(required = false) String target,@RequestParam String path, @RequestParam String customerId,@RequestParam Integer isWholeFile) {

        return outerService.getFile(target,path, customerId,isWholeFile);
    }

    /**
     * 根据文件路径获得文件大小
     *
     * @param filePath 文件路径
     * @param customerId 客户id
     * @return 文件信息
     */
    @RequestMapping(value = "/coordinator/outer/get-dataset-size", method = RequestMethod.GET)
    public String getFileSizeInfo(@RequestParam(required = false) String target,@RequestParam String filePath, @RequestParam String customerId,@RequestParam("storeType")StoreTypeEnum storeType,String bdpAccount) {

        return outerService.getFileSizeInfo(target,filePath,storeType, customerId,bdpAccount);
    }

    @RequestMapping(value = "/coordinator/outer/getNamespace", method = RequestMethod.GET)
    public String getNamespace(@RequestParam(required = true) String target) {
        return outerService.getNamespace(target);
    }


    /**
     * 根据文件路径获得文件结构
     *
     * @param filePath 文件路径
     * @param customerId 客户id
     * @return 文件信息
     */
    @RequestMapping(value = "/coordinator/outer/get-dataset-schema", method = RequestMethod.GET)
    public String getFileSchemaInfo(@RequestParam String filePath,
            @RequestParam String customerId,@RequestParam(required = false) String target) {

        return outerService.getFileSchemaInfo(filePath, customerId,target);
    }

    /**
     * 文件是否存在
     *
     * @param path 文件路径
     * @param customerId 客户id
     * @return 文件信息
     */
    @RequestMapping(value = "/coordinator/outer/isExist", method = RequestMethod.GET)
    public String exist(@RequestParam(required = false) String target,@RequestParam String path,
                                    @RequestParam String customerId,@RequestParam("storeType")StoreTypeEnum storeType,String bdpAccount) {

        return outerService.exist(target,path, customerId,bdpAccount,storeType);
    }


    @PostMapping("/coordinator/outer/deployIsExist")
    public CommonResponse<Boolean> deployIsExist(@RequestBody ExistParam existParam){
        return CommonResponse.ok(outerService.deployIsExist(existParam));
    }

    /**
     * 同步数据信息
     *
     * @param syncRequest 数据信息
     * @return 返回信息
     */
    @RequestMapping(value = "/coordinator/outer/sync-info", method = RequestMethod.POST)
    public SyncResponse syncDataInfo(@RequestBody SyncRequest syncRequest) {

        return outerService.syncDataInfo(syncRequest);
    }

    /**
     * 如果目录下不存在指定fileSuffixes的文件，不返回该目录
     * @param path
     * @param customerId
     * @param fileSuffixes
     * @return
     */
    @GetMapping(value = "/coordinator/outer/getRawDataFiles")
    public String getRawDataFile(@RequestParam(required = false) String target,@RequestParam("customerId")String customerId,@RequestParam("path") String path,@RequestParam("fileSuffixes") ArrayList<String> fileSuffixes) {
        return outerService.getRawDataFiles(target,customerId,path,fileSuffixes);
    }

    /**
     * 在redis设置pod状态信息
     *
     * @param WorkerInfo 信息
     * @return 是否同步成功
     */
    @RequestMapping(value = "/coordinator/outer/set-worker-info", method = RequestMethod.POST)
    public CommonResponse<Boolean> setWorkerInfo(@RequestBody WorkerInfo WorkerInfo) {
        return CommonResponse.ok(outerService.setWorkerInfo(WorkerInfo));
    }

    /**
     * 查看算子日志
     *
     * @param id 任务id
     * @param customerId 任务端id
     * @return 算子日志
     */
    @RequestMapping(value = "/coordinator/outer/get-job-logs", method = RequestMethod.GET)
    public CommonResponse<JobInfos> getJobLogs(@RequestParam String id,
            @RequestParam String customerId,@RequestParam(required = false) String target) {

        return CommonResponse.ok(outerService.getJobLogs(id, customerId,target));
    }

    /**
     * 查看算子结果
     *
     * @param id 集群id
     * @param customerId 任务端id
     * @return 算子结果
     */
    @RequestMapping(value = "/coordinator/outer/get-job-results", method = RequestMethod.GET)
    public CommonResponse<JobInfos> getJobResults(@RequestParam String id,
            @RequestParam String customerId,@RequestParam(required = false)String target) {

        return CommonResponse.ok(outerService.getJobResults(id, customerId,target));
    }


    /**
     * mysql转文件回调接口
     *
     * @return 返回信息
     */
    @RequestMapping(value = "/coordinator/outer/callback", method = RequestMethod.POST)
    public String callback(@RequestBody CallbackBody callbackBody) {

        return outerService.callback(callbackBody);
    }

    /**
     * 获得表头
     *
     * @return 返回信息
     */
    @RequestMapping(value = "/coordinator/outer/getFileHeader", method = RequestMethod.GET)
    public String getFileHeader(@RequestParam("path") String path,
            @RequestParam("customerId") String customerId) {

        return outerService.getFileHeader(path, customerId);
    }

    /**
     * 创建文件夹
     *
     * @return 返回信息
     */
    @RequestMapping(value = "/coordinator/outer/mkdirs", method = RequestMethod.GET)
    public String mkdirs(@RequestParam(required = false) String target,@RequestParam("path") String path,
                                @RequestParam("customerId") String customerId,@RequestParam("storeType")StoreTypeEnum storeType,String bdpAccount) {

        return outerService.mkdirs(target,path, customerId,bdpAccount,storeType);
    }

    /**
     * 调用在线预测服务
     *
     * @return 返回信息
     */
    @RequestMapping(value = "/coordinator/outer/predict", method = RequestMethod.GET)
    public String predict(@RequestParam String id, @RequestParam String customerId,
            @RequestBody Data data) {

        return outerService.predict(id, customerId, data.getData());
    }

    /**
     * 获取文件大小
     *
     * @return 返回信息
     */
    @RequestMapping(value = "/coordinator/outer/getFileSize", method = RequestMethod.GET)
    public String getFileSize(@RequestParam String path, @RequestParam String customerId,@RequestParam(required = false) String target) {

        return outerService.getFileSize(path, customerId,target);
    }
    /**
     * 关闭jupyterlab pod
     *
     * @param instanceTag 示例 9n_demo_1_10001
     * @param customerId 客户id
     * @return true or false
     */
    @RequestMapping(value = "/coordinator/outer/closeInstance", method = RequestMethod.POST)
    public CommonResponse<String> closeInstance(@RequestParam(required = false) String target,@RequestParam String instanceTag, @RequestParam String customerId) {
        return CommonResponse.ok(outerService.closeInstance(target,instanceTag, customerId));
    }

    /**
     * 同步target地址
     * @param customerIds 待同步的所有方
     * @param customerId customerId
     * @param customerIdUrl target地址
     * @return
     */
    @GetMapping("/coordinator/outer/syncTargetUrl")
    public CommonResponse<Boolean> syncTargetUrl(@RequestParam("customerIds")String customerIds,@RequestParam("customerId")String customerId,@RequestParam("customerIdUrl")String customerIdUrl){
        return CommonResponse.ok(outerService.syncTargetUrl(customerIds,customerId,customerIdUrl));
    }

    /**
     * 算子结果上报
     * @param processResultParam
     * @return
     */
    @PostMapping("/coordinator/outer/process/result")
    public ProcessCommonResponse<Boolean> processResult(@RequestBody ProcessResultParam processResultParam){
        try {
            zeebes.sendResultMsg(processResultParam.getProcessID(),processResultParam.getInstanceID(),processResultParam.getMsgID(), processResultParam.getData());
            return ProcessCommonResponse.ok();
        }catch (Exception e){
            e.printStackTrace();
            return ProcessCommonResponse.fail("error!");
        }
    }

    /**
     * 创建实例
     * curl -H "Content-Type:application/json" -X POST -d '{"processID":"TestProcess_buffalo","taskId":"1031207","appId":"ae.celling.cn","userToken":"URMc7530da3c5e82bb21d4532913381c782","appToken":"bb2caaee547dae1155be977b95195344"}' http://mpc-zeebe-monitor-test.jd.local/process/instance/create
     * @return
     */
    @PostMapping("/coordinator/outer/process/instance/create")
    public ProcessCommonResponse<Map<String,String>> createInstance(@RequestParam(value = "version",required = false) Integer version,@RequestBody Map<String,Object> map){
        try {
            String uuid = NanoId.randomNanoId(RandomUtil.getSecureRandom(),RandomUtil.BASE_CHAR_NUMBER.toCharArray(),16);
            map.put("uuid",uuid);
            ProcessInstanceEvent processInstanceEvent = zeebes.createInstance(String.valueOf(map.remove("processID")), version, map);
            Map<String,String> resultMap = Maps.newHashMap();
            resultMap.put("instanceID",String.valueOf(processInstanceEvent.getProcessInstanceKey()));
            resultMap.put("runID",uuid);
            return ProcessCommonResponse.ok(resultMap);
        }catch (Exception e){
            e.printStackTrace();
            return ProcessCommonResponse.fail("create error");
        }
    }

    @PostMapping("/coordinator/outer/process/instance/create1")
    public ProcessCommonResponse<Map<String,String>> createInstance1(@RequestParam(value = "version",required = false) Integer version,@RequestBody Map<String,Object> map){
        try {
            String runID = NanoId.randomNanoId(RandomUtil.getSecureRandom(),RandomUtil.BASE_CHAR_NUMBER.toCharArray(),16);
            map.put("instanceID",runID);
            ProcessInstanceEvent processInstanceEvent = zeebes.createInstance(String.valueOf(map.remove("processID")), version, map);
            Map<String,String> resultMap = Maps.newHashMap();
            resultMap.put("instanceID",runID);
//            resultMap.put("runID",runID);
            return ProcessCommonResponse.ok(resultMap);
        }catch (Exception e){
            e.printStackTrace();
            return ProcessCommonResponse.fail("create error");
        }
    }

    /**
     * 获得实例信息
     * @param instanceID
     * @return
     */
    @GetMapping("/coordinator/outer/process/instance/get")
    public String getInstance(@RequestParam("processTarget")String processTarget,@RequestParam("instanceID")String instanceID){
        try {
            return outerService.getInstance(processTarget,instanceID);
        }catch (Exception e){
            e.printStackTrace();
            return JSONObject.toJSONString(ProcessCommonResponse.fail("error"));
        }
    }

    @GetMapping("/coordinator/outer/getNodeLog")
    public String getNodeLog(@RequestParam(value = "target") String target,
                             @RequestParam(value = "coordinateTaskId") String coordinateTaskId,
                             @RequestParam(value = "level") String level,
                             @RequestParam(value = "nodeId") Integer nodeId,
                             @RequestParam(value = "from") Integer from,
                             @RequestParam(value = "size") Integer size) {
        return outerService.getNodeLog(target, coordinateTaskId, level, nodeId, from, size);
    }

    @GetMapping("/coordinator/outer/getFileServiceLog")
    public String getFileServiceLog(@RequestParam(value = "target") String target,
                             @RequestParam(value = "fileServiceType") Integer fileServiceType,
                             @RequestParam(value = "bdpAccount", required = false) String bdpAccount,
                             @RequestParam(value = "level") String level,
                             @RequestParam(value = "from") Integer from,
                             @RequestParam(value = "size") Integer size,
                             @RequestParam(value = "startTime", required = false) String startTime,
                             @RequestParam(value = "endTime", required = false) String endTime) {
        return outerService.getFileServiceLog(target, fileServiceType, bdpAccount, level, from, size, startTime, endTime);
    }

    @GetMapping("/coordinator/outer/getCoordinatorLog")
    public String getCoordinatorLog(@RequestParam(value = "target") String target,
                                    @RequestParam(value = "level") String level,
                                    @RequestParam(value = "from") Integer from,
                                    @RequestParam(value = "size") Integer size,
                                    @RequestParam(value = "startTime", required = false) String startTime,
                                    @RequestParam(value = "endTime", required = false) String endTime) {
        return outerService.getCoordinatorLog(target, level, from, size, startTime, endTime);
    }


}
