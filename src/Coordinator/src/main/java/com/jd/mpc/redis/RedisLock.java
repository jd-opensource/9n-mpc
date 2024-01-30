package com.jd.mpc.redis;

import java.util.concurrent.TimeUnit;
import javax.annotation.Resource;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * redis 分布式锁
 *
 * 
 * @Date: 2022/3/10
 */
@Service
public class RedisLock {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

	/**
	 * 尝试获取时效锁
	 * @param lockKey
	 * @param value
	 * @return
	 */
	public boolean tryGetLock(String lockKey, String value) {
		return stringRedisTemplate.opsForValue().setIfAbsent(lockKey, value);
	}

	/**
	 * 尝试获取时效锁
	 * @param lockKey
	 * @param value
	 * @param expire
	 * @param timeUnit
	 * @return
	 */
	public boolean tryGetDeadlineLock(String lockKey, String value, long expire, TimeUnit timeUnit) {
		return stringRedisTemplate.opsForValue().setIfAbsent(lockKey, value, expire, timeUnit);
	}

	/**
	 * 释放锁
	 * @param lockKey
	 * @return
	 */
	public boolean unLock(String lockKey) {
		return stringRedisTemplate.delete(lockKey);
	}
}
