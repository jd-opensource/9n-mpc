package com.jd.mpc.service;

import java.io.InputStream;
import java.util.*;
import java.util.stream.Collectors;

import jakarta.annotation.Resource;

import cn.hutool.crypto.digest.DigestUtil;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.google.common.collect.Lists;
import com.jd.mpc.common.enums.LogLevelEnum;
import com.jd.mpc.common.enums.StoreTypeEnum;
import com.jd.mpc.common.enums.TaskTypeEnum;
import com.jd.mpc.domain.form.EsQueryForm;
import com.jd.mpc.domain.param.ExistParam;
import com.jd.mpc.domain.vo.*;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import org.apache.commons.codec.binary.Base64;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.jd.mpc.common.response.CommonException;
import com.jd.mpc.common.util.GsonUtil;
import com.jd.mpc.common.util.HttpUtil;
import com.jd.mpc.domain.task.ChildrenTask;
import com.jd.mpc.grpc.GrpcPredictClient;
import com.jd.mpc.redis.RedisService;

import io.fabric8.kubernetes.api.model.Pod;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

/**
 * 
 * @date 2022/1/11 6:36 下午
 */

@Component
@Slf4j
public class OuterSupport {

    @Resource
    private RedisService redisService;

    @Resource
    private TaskPersistenceService taskPersistenceService;

    @Resource
    private K8sService k8sService;

    @Resource
    private GrpcPredictClient grpcPredictClient;

    @Value("${portal.url}")
    private String portalUrl;

    @Value("${target}")
    private String localTarget;
    @Value("${k8s.name.prefix}")
    String k8sPrefix;
    @Value("${zeebe.monitor-address}")
    private String monitorAddress;

    @Value("${user.es.url}")
    private String userESUrl;

    @Value("${coordinate.redis.key}")
    private String coordinateRedisKey;

    @Value("${es.user}")
    private String esUser;

    @Value("${es.pwd}")
    private String esPassword;

    @Value("${k8s.namespace}")
    private String namespace;


    @Value("${target.token}")
    private String token;

    @Value("${target.token.str}")
    private String headerToken;
    private final static String LOG_LEVEL_ALL = "ALL";

    /**
     * 同步数据
     *
     * @param map 数据
     * @return 结果
     */
    public String syncDataInfo(Map<String, String> map) {
        String res = HttpUtil.post(portalUrl + map.get("url"), map.get("data"), null,
                Collections.singletonMap(headerToken, token));
        log.info("返回结果{}", res);
        return res;
    }

    /**
     * 上传文件
     *
     * @param originalFilename 上传的文件名
     * @param inputStream 上传的文件流
     * @return 文件路径
     */
    public String uploadFile(String originalFilename, InputStream inputStream, String projectId, StoreTypeEnum storeType, String bdpAccount) {
        String fileSvcKey = "file-service-addr";
        if (storeType == StoreTypeEnum.HDFS){
            if (!StringUtils.hasText(bdpAccount)){
                throw new CommonException("bdpAccount is null!");
            }
            fileSvcKey = "file-service-addr::"+bdpAccount;
        }
        String url = "http://" + redisService.get(fileSvcKey) + "/api/file/upload/"+storeType.name().toLowerCase()+"/"+localTarget+"/"+projectId;
        String res = HttpUtil.uploadFile(originalFilename, inputStream, url);
        log.info("[upload-url] "+url+"\n[upload-res] "+res);
        return res;
    }

    /**
     * 获取文件大小信息
     *
     * @param filePath 文件
     * @return 结果
     */
    public String getFileSizeInfo(String filePath,String bdpAccount,StoreTypeEnum storeType) {
        String fileSvcKey = "file-service-addr";
        if (storeType == StoreTypeEnum.HDFS){
            if (!StringUtils.hasText(bdpAccount)){
                throw new CommonException("bdpAccount is null!");
            }
            fileSvcKey = "file-service-addr::"+bdpAccount;
        }
        String url = "http://" + redisService.get(fileSvcKey)
                + "/api/file/getTableInfo?path=" + filePath;
        // String url = "http://11.54.56.189/api/file/getTableSize?path=" + filePath;
        log.info("获取文件大小url:{}", url);
        String response = HttpUtil.get(url, 5000, 10 * 60 * 1000);
        log.info("获取文件大小response:{}", response);
        return response;
    }


    public boolean deployIsExist(ExistParam existParam) {
        switch (TaskTypeEnum.getByValue(existParam.getType())) {
            case FILE_SERVICE:
                return this.fileServiceExist(existParam);
            default:
                return false;
        }
    }


    private Boolean fileServiceExist(ExistParam existParam){
        String name = k8sPrefix + "-" + existParam.getType() + "-"+ DigestUtil.md5Hex16(existParam.getParameters().get("bdpAccount"));
        List<Deployment> deployments = k8sService.getDeploymentByName(name);
        return deployments.size()>0;
    }

    /**
     * 获取文件列信息
     *
     * @param filePath 文件
     * @return 结果
     */
    public String getFileSchemaInfo(String filePath) {
        String url = "http://" + redisService.get("file-service-addr") + "/api/mysql?path="
                + filePath;
        log.info("获取文件列信息url:{}", url);
        String response = HttpUtil.get(url);
        log.info("获取文件列信息response:{}", response);
        return response;
    }


    /**
     * 查询
     *
     * @param path 数据库信息
     * @return 返回信息
     */
    public String getFileHeader(String path) {
        String url = "http://" + redisService.get("file-service-addr")
                + "/api/file/getFileHeader?path=" + path;
        log.info("获得文件表头url:{}", url);
        String response = HttpUtil.get(url, 5000, 10 * 60 * 1000);
        log.info("获得文件表头response:{}", response);
        return response;
    }

    /**
     * mysql转文件回调接口
     *
     * @param callbackBody 回调信息
     * @return 返回信息
     */
    public String callback(CallbackBody callbackBody) {
        return HttpUtil.post(callbackBody.getCallbackUrl(), GsonUtil.createGsonString(callbackBody),
                null, null);
    }

    /**
     * 查询用户k8s资源量
     *
     * @return k8s资源量
     */
    public String getResourcesInfo() {
        ResourcesInfo resourcesInfo = new ResourcesInfo();
        resourcesInfo.setCpu(500);
        resourcesInfo.setMemory(1200);
        return GsonUtil.createGsonString(resourcesInfo);
    }

    /**
     * 查看算子日志
     *
     * @param id 任务id
     * @return 算子日志
     */
    public String getJobLogs(String id) {
        JobInfos jobInfos = new JobInfos();
        jobInfos.setId(id);
        String messages;
        Set<String> keys = redisService.scans(id+"-", 1000);
        // 从数据库取
        if (keys.isEmpty()) {
            messages = taskPersistenceService.getChildrenTaskList(id).stream()
                    .sorted(Comparator.comparingLong(ChildrenTask::getSubId)
                            .thenComparing(ChildrenTask::getTaskIndex))
                    .map(childrenTask -> {
                        String message = childrenTask.getMessage();
                        return message == null ? "" : message;
                    }).collect(Collectors.joining("\n"));
        }
        // 从redis取
        else {
            messages = keys.stream().sorted().map(key -> {
                Object message = redisService.hget(key, "message");
                return message == null ? "" : message.toString();
            }).collect(Collectors.joining("\n"));
        }
        jobInfos.setLogs(messages);
        return GsonUtil.createGsonString(jobInfos);
    }

    /**
     * 查看算子结果
     *
     * @param id 任务id
     * @return 算子结果
     */
    public String getJobResults(String id) {
        JobInfos jobInfos = new JobInfos();
        jobInfos.setId(id);
        String results;
        Set<String> keys = redisService.scans(id + "-", 1000);
        // 从数据库取,返回最后一阶段结果
        if (keys.isEmpty()) {

            final List<ChildrenTask> taskList = taskPersistenceService.getChildrenTaskList(id);
            final Set<Integer> subIdSet = taskList.stream().map(ChildrenTask::getSubId)
                    .collect(Collectors.toSet());
            if (subIdSet.size() == 1) {
                results = taskList.stream().sorted(Comparator.comparingLong(ChildrenTask::getSubId)
                        .thenComparing(ChildrenTask::getTaskIndex)).map(childrenTask -> {
                            String result = childrenTask.getResult();
                            return result == null ? "" : result;
                        }).collect(Collectors.joining("\n"));
            }
            else {
                final List<ChildrenTask> childrenTasks = taskList.stream()
                        .sorted(Comparator.comparingLong(ChildrenTask::getSubId)
                                .thenComparing(ChildrenTask::getTaskIndex))
                        .collect(Collectors.toList());
                results = childrenTasks.get(childrenTasks.size() - 1).getResult();
            }
        }
        // 从redis取
        else {
            log.info("getJobResults-keys:" + GsonUtil.createGsonString(keys));
            results = keys.stream().sorted().map(key -> {
                Object result = redisService.hget(key, "result");
                return result == null ? "" : result.toString();
            }).collect(Collectors.joining("\n"));
        }
        jobInfos.setResults(results);
        return GsonUtil.createGsonString(jobInfos);
    }

    /**
     * 查看算子占用资源
     *
     * @param ids id列表
     * @return 占用资源
     */
    public String getUsedResources(String ids) {
        List<ResourcesInfo> resourcesInfoList = null;
        try {
            resourcesInfoList = Arrays.stream(ids.split(","))
                    .map(id -> k8sService.getUsedResources(id)).collect(Collectors.toList());
        }catch(Exception e ){
            resourcesInfoList =  Arrays.stream(ids.split(","))
                    .map(id -> {
                        ResourcesInfo info  = new ResourcesInfo();
                        info.setId(id);
                        info.setCpu(0);
                        info.setMemory(0);
                        return info;
                    }).collect(Collectors.toList());
        }
        return GsonUtil.createGsonString(resourcesInfoList);
    }

    public List<PredictResult> getPredictNum(String ids) {
        return Arrays.stream(ids.split(",")).map(id -> {
            String key = "predict-num-" + id;
            String num = redisService.get(key);
            if (num == null) {
                redisService.set(key, "0");
                num = "0";
            }
            return PredictResult.builder().predictNum(Long.parseLong(num)).id(id).build();
        }).collect(Collectors.toList());

    }

    public void addProxy(ProxyInfo proxyInfo) {
        proxyInfo.getProxyMap().forEach(
                (proxyName, proxyUrl) -> redisService.hset("mpc-proxy", proxyName, proxyUrl));

    }


    public String predict(String id, String data) {
        List<Pod> podList = k8sService.getPodList(id);
        if (podList.isEmpty()) {
            throw new CommonException("此预测服务" + id + "不存在");
        }
        Pod pod = podList.get(0);
        String podIp = pod.getStatus().getPodIP();
        return grpcPredictClient.predict(data, podIp, 8082);
    }

    public String getFileSize(String path) {
        String url = "http://" + redisService.get("file-service-addr")
                + "/api/file/getSize?path=" + path;
        return HttpUtil.get(url);
    }

    public String exist(String path,String bdpAccount,StoreTypeEnum storeType) {
        String fileSvcKey = "file-service-addr";
        if (storeType == StoreTypeEnum.HDFS){
            if (!StringUtils.hasText(bdpAccount)){
                throw new CommonException("bdpAccount is null!");
            }
            fileSvcKey = "file-service-addr::"+bdpAccount;
        }
        String url = "http://" + redisService.get(fileSvcKey)
                + "/api/file/isExist?path=" + path;
        return HttpUtil.get(url);
    }

    public String closeInstance(String instanceTag) {
        k8sService.closeJupyterPods(instanceTag);
        return "true";
    }

    public void setCustomerIdUrl(String customerId,String customerIdUrl) {
        redisService.set("target:"+customerId,customerIdUrl);
    }

    public String mkdir(List<String> paths,String bdpAccount,StoreTypeEnum storeType){
        String fileSvcKey = "file-service-addr";
        if (storeType == StoreTypeEnum.HDFS){
            if (!StringUtils.hasText(bdpAccount)){
                throw new CommonException("bdpAccount is null!");
            }
            fileSvcKey = "file-service-addr::"+bdpAccount;
        }
        if(!redisService.hasKey(fileSvcKey)){
            log.error("file-service-addr key of redis not exist!");
            return "";
        }
        String url = "http://" + redisService.get(fileSvcKey) + "/api/file/mkdirs?path="+String.join(",",paths.toArray(new String[]{}));
        log.info("url:"+url);
        return HttpUtil.get(url);
    }


    public String getInstance(String instanceID) {
        String url = "http://"+monitorAddress+"/process/instance/get?instanceID="+instanceID;
        log.info("getInstanceRequest:{}",url);
        String res = HttpUtil.get(url);
        log.info("getInstanceResponse:{}", res);
        return res;
    }


    public String getRawDataFiles(String path, List<String> fileSuffixes) {
        String fileSuff = "&fileSuffixes=";
        if (fileSuffixes.size() > 0){
            List<String> list = Lists.newArrayList();
            for (String fileSuffix : fileSuffixes) {
                list.add("fileSuffixes="+fileSuffix);
            }
            fileSuff = "&"+String.join("&",list);
        }
        String url = "http://" + redisService.get("file-service-addr") + "/api/file/getRawDataFiles?path="+path+fileSuff;
        log.info("getRawDataFile-url:{}", url);
        String response = HttpUtil.get(url);
        log.info("getRawDataFile-response:{}", response);
        return response;
    }

    /**
     * 查看联邦算子日志
     *
     * @param coordinateTaskId
     * @param logLevel
     * @param nodeId
     * @param from
     * @param size
     * @return
     */
    public String getNodeLog(String coordinateTaskId, String logLevel,Integer nodeId,Integer from,Integer size) {
        log.info("本侧es地址：" + userESUrl);
        StringBuilder indexSb = new StringBuilder();
        if (LOG_LEVEL_ALL.equals(logLevel)) {
            for (int i = 0; i < LogLevelEnum.values().length; i++) {
                String level = LogLevelEnum.values()[i].getDesc();
                indexSb.append(String.format("%s_%s_%s", "9n-mpc", coordinateTaskId, level));
                if (i < LogLevelEnum.values().length - 1) {
                    indexSb.append(",");
                }
            }
        } else {
            indexSb.append(String.format("%s_%s_%s", "9n-mpc", coordinateTaskId, logLevel.toLowerCase()));
        }
        String esIndexStr = indexSb.toString();
        String esIp = userESUrl;
        String url = "http://" + esIp + "/" + esIndexStr + "/_search";
        EsQueryForm esQueryForm = new EsQueryForm();
        JSONObject queryDTO = new JSONObject();
        queryDTO.put("match_all", new JSONObject());
        esQueryForm.setQuery(queryDTO);
        esQueryForm.setFrom(from);
        esQueryForm.setSize(size);
        log.info("获取日志url:" + url + ",body:" + JSON.toJSONString(esQueryForm));
        String input = esUser + ":" + esPassword;
        log.warn("esUser: " + esUser + ", esPassword: " + esPassword);
        String encodedPassword = null;
        try {
            encodedPassword = Base64.encodeBase64String(input.getBytes("UTF-8"));
        } catch (Exception e) {
            log.error("encodedPassword 编码错误");
        }
        Map<String, String> header = new HashMap<>();
        header.put("Authorization", "Basic " + encodedPassword);
        String res = null;
        try {
            res = HttpUtil.get(url, JSON.toJSONString(esQueryForm), header);
        } catch (Exception e) {
            log.error("getNodeLog http 查询失败: " + e.getStackTrace());
        }
        log.info("getNodeLog返回结果：" + res);
        return res;
    }



    public String getNamespace() {
        return namespace;
    }
}
