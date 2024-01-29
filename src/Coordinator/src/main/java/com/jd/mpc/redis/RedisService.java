package com.jd.mpc.redis;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.DataType;
import org.springframework.data.redis.core.*;
import org.springframework.data.redis.core.ScanOptions.ScanOptionsBuilder;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.TimeUnit;

@Service
public class RedisService {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    /**
     * key's type
     * @param key
     * @return
     */
    public DataType type(String key){
        return stringRedisTemplate.type(key);
    }

    /**
     * 常规操作
     */
    public void set(String key, String value, long timeout, TimeUnit unit) {
        stringRedisTemplate.opsForValue().set(key, value, timeout, unit);
    }

    public void set(String key, String value) {
        stringRedisTemplate.opsForValue().set(key, value);
    }

    public String get(String key) {
        return stringRedisTemplate.opsForValue().get(key);
    }

    public Long ttl(String key) {
        return stringRedisTemplate.getExpire(key);
    }

    public Boolean setExpire(String key, long timeout, TimeUnit unit) {
        return stringRedisTemplate.expire(key, timeout, unit);
    }

    public Long getExpire(String key) {
        return stringRedisTemplate.getExpire(key);
    }

    public Boolean setExpireAt(String key, Date expireAt) {
        return stringRedisTemplate.expireAt(key, expireAt);
    }

    public void delete(String key) {
        stringRedisTemplate.delete(key);
    }

    public void delete(Set<String> keys) {
        stringRedisTemplate.delete(keys);
    }

    public Boolean setIfNotExist(String key, String value) {
        return stringRedisTemplate.opsForValue().setIfAbsent(key, value);
    }

    public Boolean hasKey(String key) {
        return stringRedisTemplate.hasKey(key);
    }

    public Set<String> scans(String pattern, Integer size) {
        return stringRedisTemplate.execute((RedisCallback<Set<String>>) connection -> {
            Set<String> keysTmp = new HashSet<>();
            Cursor<byte[]> cursor = connection.scan(ScanOptions.scanOptions().match(pattern + "*").count(size).build());
            while (cursor.hasNext()) {
                keysTmp.add(new String(cursor.next()));
            }
            return keysTmp;
        });
    }

    public boolean equals(String sourceKey,String dstKey){
        String value = get(sourceKey);
        if (value ==  null){
            return false;
        }
        return value.equals(get(dstKey));
    }


    public Long increment(String key, long val) {
        return stringRedisTemplate.opsForValue().increment(key, val);
    }

    /**
     * hash表操作
     */
    public void hset(String key, Object field, Object value) { //将哈希表key中的域field的值设为value
        stringRedisTemplate.opsForHash().put(key, field, value);
    }

    public Object hget(String key, Object field) { //返回哈希表key中给定域field的值
        return stringRedisTemplate.opsForHash().get(key, field);
    }

    public Boolean hhas(String key, Object field) { //返回哈希表key中给定域field的值
        return stringRedisTemplate.opsForHash().hasKey(key, field);
    }
    public void hdel(String key, Object... fields) {
        stringRedisTemplate.opsForHash().delete(key, fields);
    }

    public Map<Object, Object> hgetall(String key){
        return stringRedisTemplate.opsForHash().entries(key);
    }

    public long hsize(String key){
        return stringRedisTemplate.opsForHash().size(key);
    }

    public Set<String> hkeys(String key){
        HashOperations<String, String, String> hashOperations = stringRedisTemplate.opsForHash();
        return hashOperations.keys(key);
    }

    public Map<Object, Object> entries(String key) { //返回哈希表键值对
        return stringRedisTemplate.opsForHash().entries(key);
    }

    public Long hincrement(String key, Object field, long var3) {
        return stringRedisTemplate.opsForHash().increment(key, field, var3);
    }

    public double hincrement(String key, Object field, double var3) {
        return stringRedisTemplate.opsForHash().increment(key, field, var3);
    }

    /**
     * List操作
     */
    public Long lpush(String key, String value) {
        return stringRedisTemplate.opsForList().leftPush(key, value);
    }

    public Long rpush(String key, String value) {
        return stringRedisTemplate.opsForList().rightPush(key, value);
    }

    public String lpop(String key) {
        return stringRedisTemplate.opsForList().leftPop(key);
    }

    public String rpop(String key) {
        return stringRedisTemplate.opsForList().rightPop(key);
    }

    public List<String> lrange(String key, long start, long end) { //取得List的范围，0代表第一位，-1代表最后一位
        return stringRedisTemplate.opsForList().range(key, start, end);
    }

    /**
     * SortedSet有序集合操作
     */
    public void zadd(String key, String value, double score) { //向名称为key的zset中添加元素member,score用于排序。如果该元素存在，则更新其顺序
        stringRedisTemplate.opsForZSet().add(key, value, score);
    }

    public Long zcount(String key, double score1, double score2) { //返回集合中score在给定区间的数量
        return stringRedisTemplate.opsForZSet().count(key, score1, score2);
    }

    public Long zcard(String key) { //返回集合中元素个数
        return stringRedisTemplate.opsForZSet().zCard(key);
    }

    public Set<String> zrangeByScore(String key, double score1, double score2) {
        return stringRedisTemplate.opsForZSet().rangeByScore(key, score1, score2);
    }

    public Long zremRangeByScore(String key, double score1, double score2) {
        return stringRedisTemplate.opsForZSet().removeRangeByScore(key, score1, score2);
    }

    public Set<String> zrange(String key, long start, long end) {
        return stringRedisTemplate.opsForZSet().range(key, start, end);
    }

    /**
     * Set
     */
    public Long sadd(String key, String value) {
        return stringRedisTemplate.opsForSet().add(key, value);
    }

    public Long srem(String key, String value) {
        return stringRedisTemplate.opsForSet().remove(key, value);
    }

    public Long incrBy(String key) {
        return stringRedisTemplate.opsForValue().increment(key, 1L);
    }

    public Long incr(String key, Long num) {
        return stringRedisTemplate.opsForValue().increment(key, num);
    }

    public Set<String> members(String key) {
        return stringRedisTemplate.opsForSet().members(key);
    }


    /**
     * redis distributed tryLock
     * 200ms per loop
     * @param lockKey
     * @param retryCount
     * @return
     * @throws InterruptedException
     */
    public boolean tryLock(String lockKey,Integer retryCount) throws InterruptedException {
        while (retryCount >= 0){
            if (setIfNotExist(lockKey, "0")){
                return true;
            }
            TimeUnit.MILLISECONDS.sleep(200L);
            retryCount--;
        }
        return false;
    }

    /**
     * redis distributed unLock
     * @param lockKey
     */
    public void unLock(String lockKey){
        delete(lockKey);
    }

    /**
     * 基于scan命令模糊查询key
     *
     * @param pattern
     * @return
     */
    public List<String> fuzzyGet(String pattern) {
        List<String> result = new ArrayList<>();
        Set<String> keySet = scans(pattern, 10000);
        for (String key : keySet) {
            result.add(key + ":" + get(key));
        }
        return result;
    }


}
