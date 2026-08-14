package com.sky.takeout.pay.redis;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

/**
 * Redis 幂等 / 锁 小工具
 * 
 * 支付中心里主要做两件事：
 * 1. SET NX + TTL：占坑（幂等键、分布式锁）
 * 2. 按token解锁：只删自己加的锁，避免误删别人的锁
 * 3. 解锁用 Lua 脚本，避免并发问题（Lua 原子「相等才删」，避免误删别人的锁）
 *  3.1 GET key  →  看是不是我的 token  →  DEL key 中间有缝隙，可能导致误删别人的锁
 *  3.2 Lua 原子「相等才删」，避免误删别人的锁
 * 
 * 不关心业务字段，只操作String key/value
 * RedisIdempotentHelper
 */
@Component
public class RedisIdempotentHelper {

    /**
     * 解锁脚本（类加载时初始化一次即可）。
     * 返回 Long：1=删了自己的锁，0=没删（token 不匹配或 key 不存在）
     */
    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT = new DefaultRedisScript<>();

    /**
     * 初始化解锁脚本
     * 内容含义：如果 Redis 中 key 的值等于传入的 token，则删除该 key，并返回 1；否则返回 0。
     */
    static {
        UNLOCK_SCRIPT.setResultType(Long.class);
        UNLOCK_SCRIPT.setScriptText(
                "if redis.call('get', KEYS[1]) == ARGV[1] then " + "return redis.call('del', KEYS[1]) "
                        + "else return 0 end");
    }

    private final StringRedisTemplate redis;

    public RedisIdempotentHelper(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /**
     * 尝试设置一个 NX 的 key，如果设置成功，则返回 true，否则返回 false。
     * opsForValue().setIfAbsent 是 Redis 的命令，用于设置一个 key，如果该 key 不存在，则设置成功，并返回 true；否则返回 false。
     * @param key 键
     * @param value 值
     * @param ttlSeconds 过期时间
     * @return
     */
    public boolean trySetNx(String key, String value, Long ttlSeconds) {
        Boolean ok = redis.opsForValue().setIfAbsent(key, value, Duration.ofSeconds(ttlSeconds));

        return Boolean.TRUE.equals(ok);
    }


    /**
     * 设置一个 key，如果设置成功，则返回 true，否则返回 false。
     * opsForValue().set 是 Redis 的命令，用于设置一个 key，如果该 key 存在，则设置成功，并返回 true；否则返回 false。
     * 用Duration 避免 Spring Data Redis 4.1 弃用的 TimeUnit 重载。
     * @param key
     * @param value
     * @param ttlSeconds
     */
    public void set(String key, String value, Long ttlSeconds) {
        redis.opsForValue().set(key, value, Duration.ofSeconds(ttlSeconds));
    }

    public String get(String key) {
        return redis.opsForValue().get(key);
    }

    /**
     * 计数 +1，并在首次创建时设置 TTL（教学失败次数、限流窗口等）。
     * <p>
     * 对应 Redis：{@code INCR key}；若结果为 1 再 {@code EXPIRE key ttlSeconds}。
     * 多实例并发时各自 INCR 仍原子；TTL 仅第一次设置，避免每次 INCR 都刷新过期时间。
     *
     * @return 自增后的值；异常时不应吞掉（由调用方决定）
     */
    public long incr(String key, long ttlSeconds) {
        Long n = redis.opsForValue().increment(key);
        long value = n == null ? 0L : n;
        if (value == 1L && ttlSeconds > 0) {
            redis.expire(key, Duration.ofSeconds(ttlSeconds));
        }
        return value;
    }

    public void delete(String key) {
        redis.delete(key);
    }

    /**
     * 尝试加锁
     * 1. 生成一个唯一的 token
     * 2. 尝试设置一个 NX 的 key，如果设置成功，则返回 token，否则返回 null
     * 3. 如果设置成功，则返回 token，否则返回 null
     * @param key
     * @param ttlSeconds
     * @return
     */
    public String tryLock(String key, Long ttlSeconds) {
        String token = UUID.randomUUID().toString();

        return trySetNx(key, token, ttlSeconds) ? token : null;
    }

    /**
     * 解锁
     * 1. 如果 token 为 null，则直接返回
     * 2. 执行解锁脚本，如果结果为 1，则删除成功，否则返回 0
     * @param key
     * @param token
     */
    public void unlock(String key, String token) {
        if (token == null) {
            return;
        }

        // key 列表，token 参数
        List<String> keys= Collections.singletonList(key);
        // 执行解锁脚本
        Long result = redis.execute(UNLOCK_SCRIPT, keys, token);
        if (result != null && result == 1L) {
            // result == 1 删成功；0 表示不是自己的锁（可打日志，一般可忽略）
        }
    }



}
