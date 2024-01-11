package com.jd.mpc.storage;

import com.google.gson.reflect.TypeToken;
import com.jd.mpc.common.util.GsonUtil;
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
import java.util.function.BiConsumer;
import java.util.function.BiFunction;

/**
 * @author yezhenyue1
 * @date 2022-04-08 17:28
 */
@Component
@Slf4j
public class TargetMapHolder {
    @Autowired
    private RedisService redisService;
    /**
     * 存储任务执行端的Map结构
     */
//    private ConcurrentHashMap<String, Set<String>> targetMap;
    @Value("${target}")
    private String localTarget;
    private String redisKey;

    /**
     * 程序启动后，优先从redis恢复数据到本地targetMap，如果redis不存在，再执行new操作
     */
    @PostConstruct
    protected void init() {
        redisKey = "target_map:"+localTarget;
        DataType type = redisService.type(redisKey);
        if (type == DataType.STRING){
            //convert
            String s = redisService.get(redisKey);
            redisService.delete(redisKey);
            Type typeToken = new TypeToken<ConcurrentHashMap<String, Set<String>>>(){}.getType();
            ConcurrentHashMap<String, Set<String>> map = GsonUtil.changeGsonToBean(s, typeToken);
            for (Map.Entry<String, Set<String>> entry : map.entrySet()) {
                redisService.hset(redisKey,entry.getKey(),GsonUtil.createGsonString(entry.getValue()));
            }
        }
    }
    /**
     * 当外部程序修改了taskMap的内容需要手动调用该方法进行数据持久化
     */
    public void persistence(){
    }

    
    public long size() {
        return redisService.hsize(redisKey);
    }

    
    public boolean isEmpty() {
        return !redisService.hasKey(redisKey) || (redisService.hsize(redisKey) <= 0);
    }

    
    public Set<String> get(String key) {
        String value = (String)redisService.hget(redisKey, key);
        if (StringUtils.isBlank(value)){
            return null;
        }
        Type typeToken = new TypeToken<Set<String>>(){}.getType();
        return GsonUtil.changeGsonToBean(value, typeToken);
    }

    
    public boolean containsKey(String key) {
        return redisService.hhas(redisKey,key);
    }


    public void put(String key, Set<String> value) {
        redisService.hset(redisKey,key,GsonUtil.createGsonString(value));
    }

    
    public void remove(String key) {
        redisService.hdel(redisKey,key);
    }

    
    public void clear() {
        redisService.delete(redisKey);
    }

    
    public void forEach(BiConsumer<? super String, ? super Set<String>> action) {
        Type typeToken = new TypeToken<Set<String>>(){}.getType();
        Map<Object, Object> map = redisService.hgetall(redisKey);
        for (Map.Entry<Object, Object> entry : map.entrySet()) {
            String key = (String) entry.getKey();
            Set<String> value =  GsonUtil.changeGsonToBean((String) entry.getValue(), typeToken);
            action.accept(key,value);
        }
    }
    
    public Enumeration<String> keys() {
        Set<String> hkeys = redisService.hkeys(redisKey);
        return Collections.enumeration(hkeys);
    }

    public Set<String> keySet() {
        return redisService.hkeys(redisKey);
    }
    
}
