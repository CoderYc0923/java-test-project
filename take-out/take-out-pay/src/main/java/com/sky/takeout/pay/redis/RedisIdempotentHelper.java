package com.sky.takeout.pay.redis;

import java.time.Duration;
import java.util.UUID;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Redis 幂等 / 锁 小工具
 * 
 * 支付中心里主要做两件事：
 * 1. SET NX + TTL：占坑（幂等键、分布式锁）
 * 2. 按token解锁：只删自己加的锁，避免误删别人的锁
 * 
 * 不关心业务字段，只操作String key/value
 * RedisIdempotentHelper
 */
@Component
public class RedisIdempotentHelper {

    private final StringRedisTemplate redis;

    public RedisIdempotentHelper(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /**
     *  尝试写入，不重复写入（幂等占坑）
     *  SET key value NX EX ttl
     *  只有 key 不存在时才写入；成功返回 true，已被占用返回 false。
     *  用途：幂等占坑、抢锁
     *  opsForValue的作用是操作字符串类型的数据
     *  opsForValue().setIfAbsent() 是 Redis 的命令，用于设置键值对，如果键不存在则设置，如果键存在则不设置。
     */
    public boolean trySetNx(String key, String value, Long ttlSeconds) {
        Boolean ok = redis.opsForValue().setIfAbsent(key, value, Duration.ofSeconds(ttlSeconds));

        // setIfAbsent 可能返回null, 只认true为成功
        return Boolean.TRUE.equals(ok);
    }

    /**
     * 覆盖写入，并设置过期时间（秒）
     */
    public void set(String key, String value, Long ttlSeconds) {
        redis.opsForValue().set(key, value, Duration.ofSeconds(ttlSeconds) );
    }

    /**
     * 读，不存在返回null
     * @param key
     * @return
     */
    public String get(String key) {
        return redis.opsForValue().get(key);
    }

    /**
     * 删除key，不存在的key不会报错
     * @param key
     */
    public void delete(String key) {
        redis.delete(key);
    }

    /**
     * 尝试加锁
     * @param key
     * @param ttlSeconds
     * @return 拿到锁返回随机token，没拿到返回null
     * 
     * token： 锁过期后别人可能又加了锁，解锁时对比value，避免误删别人的锁
     */
    public String tryLock(String key, Long ttlSeconds) {
        String token  = UUID.randomUUID().toString();
        return trySetNx(key, token, ttlSeconds) ? token : null;
    }

    public void unlock(String key, String token) {
        if (token == null) {
            return;
        }
        String current = redis.opsForValue().get(key);
        if(token.equals(current)) {
            redis.delete(key);
        }
    }
}
