# 教学：用 Redis 做 `teachFailCounter`（替代内存 Map）

日期：2026-08-14  
关联：`2026-08-14-rocketmq-p1-retry-compensate-handson.md`（F4 故意失败 N 次）  
现网工具：`RedisIdempotentHelper#incr`

---

## 0. 为啥不该用进程内 Map

P1 初稿里是：

```java
private final Map<String, AtomicInteger> teachFailCounter = new ConcurrentHashMap<>();
```

问题：

| | 内存 `ConcurrentHashMap` | Redis `INCR` |
|--|-------------------------|--------------|
| 多实例 / 多 Consumer | 各数各的，重试可能落到别的 JVM，计数对不上 | 全集群同一把钥匙 |
| 进程重启 | 计数清零，可能少失败几次 | 键还在（有 TTL） |
| 生产开关 | 教学可凑合 | 和你们支付锁/幂等同一套 Redis |

教学验收「同一 `eventId` 前 N 次故意失败」时，**应用若以后水平扩展，必须用 Redis。**

---

## 1. 大白话

```text
key = mq:teach-fail:order-paid:{eventId}

每次消费（含 MQ 重试）：
  n = INCR key          // 原子 +1
  若 n == 1：EXPIRE 1小时   // 只第一次设过期，避免键永久留下
  若 n <= kitchenFailTimes：抛异常 → 触发 MQ 重试
  否则：放过，走真正的厨房推送
```

`kitchenFailTimes` 仍来自 `TakeoutMqProperties`（`mq.kitchen-fail-times`），**不要**再用 `@Value`。

---

## 2. Helper：加一个 `incr`

`RedisIdempotentHelper`（已与仓库对齐的写法）：

```java
/**
 * 计数 +1；首次创建时设 TTL。
 * Redis：INCR；若值为 1 再 EXPIRE。
 */
public long incr(String key, long ttlSeconds) {
    Long n = redis.opsForValue().increment(key);
    long value = n == null ? 0L : n;
    if (value == 1L && ttlSeconds > 0) {
        redis.expire(key, Duration.ofSeconds(ttlSeconds));
    }
    return value;
}
```

要点：

- `INCR` 对不存在的 key 会从 0 变成 1  
- TTL 只在 `value == 1` 时设，**不要**每次 INCR 都 `expire`（否则窗口会一直被刷新，教学计数永远清不掉也说不清）  
- 键必须带 `eventId`，不要用全局一个计数器  

---

## 3. Consumer 里怎么用（对照稿）

```java
private static final String TEACH_FAIL_PREFIX = "mq:teach-fail:order-paid:";

// ... 幂等短路之后、真正 notify 之前：

int kitchenFailTimes = mqProperties.getKitchenFailTimes() == null
        ? 0
        : mqProperties.getKitchenFailTimes();

if (kitchenFailTimes > 0) {
    // 与幂等键分开：失败路径不能写成功幂等，但可以累加教学计数
    String teachKey = TEACH_FAIL_PREFIX + msg.getEventId();
    // TTL 1 小时足够验收；测完把 kitchen-fail-times 改回 0
    long failed = redis.incr(teachKey, Duration.ofHours(1).getSeconds());

    if (failed <= kitchenFailTimes) {
        log.warn("【教学】故意失败 {}/{} eventId={} orderId={}",
                failed, kitchenFailTimes, msg.getEventId(), msg.getOrderId());
        // 必须抛异常，不能 return：否则 MQ 认为成功，不会重试
        throw new IllegalStateException(
                "teach fail " + failed + "/" + kitchenFailTimes);
    }
}

kitchenNotifyService.notifyNewOrder(msg);
// 成功后再 trySetNx 幂等键 ...
```

和内存版对比：

```text
内存：AtomicInteger.incrementAndGet()
Redis：redis.incr(key, ttl)   // 返回值含义一样：第几次失败
```

---

## 4. 配置与验收

`application.yml`（或 local）：

```yaml
mq:
  kitchen-fail-times: 2   # 前 2 次故意失败；生产务必 0
```

实验：

1. 设 `kitchen-fail-times: 2`，重启，付一笔  
2. 日志应出现 `故意失败 1/2`、`2/2`，然后厨房成功  
3. Redis 查看：

```bash
redis-cli
GET mq:teach-fail:order-paid:<你的eventId>
TTL mq:teach-fail:order-paid:<你的eventId>
```

4. 测完改回 `0`，可手动 `DEL` 该 key  

---

## 5. 注意点

| 点 | 说明 |
|----|------|
| 与消费幂等键分离 | `mq:consume:order-paid:` 成功后才写；`mq:teach-fail:` 专门数失败次数 |
| 多实例 | 必须 Redis；内存 Map 会数乱 |
| `kitchen-fail-times: 0` | 整段 if 不进，生产无开销（仍建议正式环境保持 0） |
| 超过 `maxReconsumeTimes` | 会进死信；教学故意失败次数不要大于最大重试，除非你在练死信 |
| 不要用字段初始化读 `mqProperties` | 构造前为 null；在 `onMessage` 里 `getKitchenFailTimes()` |

---

## 6. 一句话

**`teachFailCounter` 用 Redis `INCR` + 首次 `EXPIRE`，按 `eventId` 计数；达到 `mq.kitchen-fail-times` 前一直抛异常触发 MQ 重试，多实例也正确。**
