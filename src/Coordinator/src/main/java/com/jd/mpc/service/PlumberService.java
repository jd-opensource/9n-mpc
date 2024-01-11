package com.jd.mpc.service;

import com.google.gson.reflect.TypeToken;
import com.jd.mpc.common.util.GsonUtil;
import com.jd.mpc.common.util.HttpUtil;
import com.jd.mpc.domain.offline.commons.OfflineTask;
import com.jd.mpc.domain.offline.commons.PreJob;
import com.jd.mpc.domain.task.ParentTask;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.Map;

/**
 * plumber相关的方法
 */
@Slf4j
@Service
public class PlumberService {

    @Resource
    private TaskPersistenceService taskPersistenceService;

    public Map<String, Object> getConfigById(String id) {
        ParentTask task = taskPersistenceService.getByTaskId(id);
        String preJson = task.getParams();
        PreJob preJob = GsonUtil.changeGsonToBean(preJson, PreJob.class);
        OfflineTask offlineTask = preJob.getTasks().get(0);

        Map<String, String> bizParameters = offlineTask.getParameters();
        Map<String, Object> srcInfo = GsonUtil.changeGsonToBean(bizParameters.get("srcInfo"), new TypeToken<Map<String, Object>>() {}.getType());
        Map<String, Object> targetInfo = GsonUtil.changeGsonToBean(bizParameters.get("targetInfo"), new TypeToken<Map<String, Object>>() {}.getType());

        Map<String, Object> objMap = new HashMap<>();
        objMap.put("srcInfo", srcInfo);
        objMap.put("targetInfo", targetInfo);
        objMap.put("jobId",Integer.parseInt(bizParameters.get("jobId")));
        objMap.put("jobName",bizParameters.get("jobName"));
        objMap.put("srcType",bizParameters.get("srcType"));
        objMap.put("targetType",bizParameters.get("targetType"));
        Map<String,Object> resultMap = new HashMap<>();
        resultMap.put("code",0);
        resultMap.put("message","处理成功");
        resultMap.put("obj",objMap);
        log.info("plumberConfigJson:"+GsonUtil.createGsonString(resultMap));
        return resultMap;
    }


    public static void main(String[] args) {
        String preJson = "{\"id\":\"plumer03236\",\"subId\":0,\"status\":0,\"tasks\":[{\"id\":\"plumer03236\",\"subId\":0,\"taskIndex\":0,\"resourcesType\":\"deployment\",\"podNum\":1,\"completedNum\":0,\"name\":\"pk-plumber-leader-plumer03236-0-0\",\"status\":0,\"deploymentPath\":\"/k8s/plumber.yaml\",\"taskType\":\"plumber\",\"role\":\"leader\",\"target\":\"9n_demo_1\",\"redis_server\":\"10.178.254.17:32382\",\"redis_password\":\"UzivwXUkA0HW4syH\",\"proxy_remote\":\"10.178.254.38:32033\",\"parameters\":{\"app-id\":\"plumer03236\",\"srcType\":\"mysql\",\"targetType\":\"hive\",\"srcInfo\":\"{\\\"dsList\\\":[{\\\"dbPort\\\":\\\"3306\\\",\\\"dbName\\\":\\\"plumber\\\",\\\"dbUser\\\":\\\"root\\\",\\\"tableList\\\":[\\\"extract\\\"],\\\"dbHost\\\":\\\"10.178.250.173\\\",\\\"dbCharset\\\":\\\"utf-8\\\",\\\"dbPassword\\\":\\\"123456\\\"}],\\\"incrementSql\\\":\\\"\\\",\\\"columnList\\\":[{\\\"colName\\\":\\\"id\\\",\\\"hType\\\":\\\"0\\\",\\\"colType\\\":\\\"bigint\\\",\\\"targetColName\\\":\\\"id\\\",\\\"targetColType\\\":\\\"bigint\\\"},{\\\"colName\\\":\\\"name\\\",\\\"hType\\\":\\\"0\\\",\\\"colType\\\":\\\"varchar\\\",\\\"targetColName\\\":\\\"name\\\",\\\"targetColType\\\":\\\"varchar\\\"},{\\\"colName\\\":\\\"create_time\\\",\\\"hType\\\":\\\"0\\\",\\\"colType\\\":\\\"datetime\\\",\\\"targetColName\\\":\\\"create_time\\\",\\\"targetColType\\\":\\\"datetime\\\"},{\\\"colName\\\":\\\"introduce\\\",\\\"hType\\\":\\\"0\\\",\\\"colType\\\":\\\"mediumtext\\\",\\\"targetColName\\\":\\\"introduce\\\",\\\"targetColType\\\":\\\"mediumtext\\\"}],\\\"tableList\\\":[\\\"extract\\\"]}\",\"targetInfo\":\"{\\\"columnList\\\":[{\\\"colName\\\":\\\"id\\\",\\\"colType\\\":\\\"string\\\"},{\\\"colName\\\":\\\"name\\\",\\\"colType\\\":\\\"string\\\"},{\\\"colName\\\":\\\"create_time\\\",\\\"colType\\\":\\\"string\\\"},{\\\"colName\\\":\\\"introduce\\\",\\\"colType\\\":\\\"string\\\"}],\\\"fileType\\\":\\\"orc\\\",\\\"fileLocation\\\":\\\"file:///mnt/data/plumber/plumer03236/output-dir\\\"}\",\"redis-host\":\"10.178.254.17\",\"redis-port\":\"32382\",\"redis-pwd\":\"UzivwXUkA0HW4syH\",\"redis-server\":\"10.178.254.17:32382\",\"redis-password\":\"UzivwXUkA0HW4syH\",\"proxy-remote\":\"10.178.254.38:32033\",\"jobId\":\"03236\",\"jobName\":\"plumer03236\"},\"extParameters\":{\"FINISH_URL\":\"http://coordinator:8080/plumber/success?id=plumer03236\",\"ERROR_URL\":\"http://coordinator:8080/plumber/error?id=plumer03236\",\"CONFIG_URL\":\"http://coordinator:8080/plumber/config?id=plumer03236\"}}]}";
        PreJob preJob = GsonUtil.changeGsonToBean(preJson, PreJob.class);
        OfflineTask offlineTask = preJob.getTasks().get(0);

        Map<String, String> bizParameters = offlineTask.getParameters();
        Map<String, Object> srcInfo = GsonUtil.changeGsonToBean(bizParameters.get("srcInfo"), new TypeToken<Map<String, Object>>() {}.getType());
        Map<String, Object> targetInfo = GsonUtil.changeGsonToBean(bizParameters.get("targetInfo"), new TypeToken<Map<String, Object>>() {}.getType());

        Map<String, Object> objMap = new HashMap<>();
        objMap.put("srcInfo", srcInfo);
        objMap.put("targetInfo", targetInfo);
        objMap.put("jobId",bizParameters.get("jobId"));
        objMap.put("jobName",bizParameters.get("jobName"));
        objMap.put("srcType",bizParameters.get("srcType"));
        objMap.put("targetType",bizParameters.get("targetType"));
        Map<String,Object> resultMap = new HashMap<>();
        resultMap.put("code",0);
        resultMap.put("message","处理成功");
        resultMap.put("obj",objMap);
        log.info("plumberConfigJson:"+GsonUtil.createGsonString(resultMap));
    }
    public boolean callback(String id) {
//        ParentTask task = taskPersistenceService.getByTaskId(id);
//        String preJson = task.getParams();
//        PreJob preJob = GsonUtil.changeGsonToBean(preJson, PreJob.class);
//        OfflineTask subTask = preJob.getTasks().get(0);

//        Map<String, String> bizParameters = subTask.getBizParameters();
//        Map<String, Object> targetInfo = GsonUtil.changeGsonToBean(bizParameters.get("targetInfo"), new TypeToken<Map<String, Object>>() {}.getType());
//        Object fileLocation = targetInfo.get("fileLocation");
//
//        String callbackUrl = bizParameters.get("callbackUrl");

        //TODO
//        String jsonData = null;
//        HttpUtil.post(callbackUrl, jsonData,60000, null);
        log.info("plumberCallback:"+id);
        return true;
    }
}
