package com.jd.mpc.storage;

import com.google.gson.reflect.TypeToken;
import com.jd.mpc.common.util.GsonUtil;
import com.jd.mpc.domain.offline.commons.OfflineTask;
import com.jd.mpc.domain.offline.commons.SubTask;
import com.jd.mpc.redis.RedisService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.DataType;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.lang.reflect.Type;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.*;

/**
 * @author yezhenyue1
 * @date 2022-04-08 11:51
 */
@Component
@Slf4j
public class OfflineTaskMapHolder {
    /**
     * Redis实例
     */
    @Autowired
    private RedisService redisService;
    @Value("${target}")
    private String localTarget;
    private String redisKey;

    /**
     * 程序启动后，优先从redis恢复数据到本地taskMap，如果redis不存在，再执行new操作
     */
    @PostConstruct
    protected void init() {
        redisKey = "offline_task_map:"+localTarget;
        DataType type = redisService.type(redisKey);
        if (type == DataType.STRING){
            String s = redisService.get(redisKey);
            redisService.delete(redisKey);
            if (StringUtils.isNotBlank(s)){
                Type typeToken = new TypeToken<ConcurrentHashMap<String, List<SubTask>>>(){}.getType();
                ConcurrentHashMap<String, List<SubTask>> taskMap = GsonUtil.changeGsonToBean(s, typeToken);
                for (Map.Entry<String, List<SubTask>> entry : taskMap.entrySet()) {
                    redisService.hset(redisKey,entry.getKey(),GsonUtil.createGsonString(entry.getValue()));
                }
                log.info("### OfflineTaskMapHolder loaded from redis ... taskMap:{}",GsonUtil.createGsonString(taskMap));
            }
        }
    }

    /**
     * 当外部程序修改了taskMap的内容需要手动调用该方法进行数据持久化
     */
    public void persistence(){
    }

    public void put(SubTask task){
        List<SubTask> subTasks = get(task.getId());
        if (subTasks != null){
            subTasks.set(task.getSubId(),task);
        }
        put(task.getId(),subTasks);
    }

    public long size() {
        return redisService.hsize(redisKey);
    }

    
    public boolean isEmpty() {
        return !redisService.hasKey(redisKey) || (redisService.hsize(redisKey) <= 0);
    }

    
    public List<SubTask> get(String key) {
        String value = (String)redisService.hget(redisKey, key);
        if (StringUtils.isBlank(value)){
            return null;
        }
        Type typeToken = new TypeToken<List<SubTask>>(){}.getType();
        return GsonUtil.changeGsonToBean(value, typeToken);
    }

    
    public boolean containsKey(String key) {
        return redisService.hhas(redisKey, key);
    }
    
    public void put(String key, List<SubTask> value) {
        redisService.hset(redisKey,key,GsonUtil.createGsonString(value));
    }

    
    public void remove(String key) {
        redisService.hdel(redisKey,key);
    }

    
    public void clear() {
        redisService.delete(redisKey);
    }

    
    public Enumeration<String> keys() {
        Set<String> keys = redisService.hkeys(redisKey);
        return Collections.enumeration(keys);
    }

    
    public void forEach(BiConsumer<? super String, ? super List<SubTask>> action) {
        Map<Object, Object> map = redisService.hgetall(redisKey);
        Type typeToken = new TypeToken<List<SubTask>>(){}.getType();
        for (Map.Entry<Object, Object> entry : map.entrySet()) {
            String key = (String) entry.getKey();
            List<SubTask> value = GsonUtil.changeGsonToBean((String)entry.getValue(),typeToken);
            action.accept(key,value);
        }
    }

}
