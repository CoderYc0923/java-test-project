# `@Transactional` + Redis 锁：支付回调里怎么配合

日期：2026-08-10  
相关代码：`MockPaymentGateway.handlePayNotify` / `markPaidWithLock`  
目标：弄清「数据库事务」和「Redis 分布式锁」各自管什么、一起用时有什么缝、学习项目怎么取舍。

---

## 0. 先回答：通知失败补偿是谁做的？

**主要是渠道侧（你们的假微信）在做「有限次重试」；不是外卖商户侧的完整补偿。**

| 角色 | 现在有什么 | 没有什么 |
|------|------------|----------|
| **假微信** `MerchantNotifyClient` | notify HTTP 失败时按配置重试（如共 3 次） | 持久化重试队列、对账补推 |
| **假微信** confirm 语义 | 首次把单置 `SUCCESS` 后才 POST；已 SUCCESS **不再** POST | 通知失败后的人工/定时补推 |
| **外卖商户** | 验签、nonce、锁、CAS 入账 | 没收到回调时的主动查单、补单任务 |

真微信类似：微信会按策略重试 notify；商户还要能**查单对账**兜底。  
你们教学版：假微信有短重试；若耗尽仍失败且单已是 SUCCESS，再点确认也**不会再回调**——这是已知缺口，补偿若要做完整，应：

1. 假微信：失败可再补推 / 或 SUCCESS 但 `notifySent=false` 时允许再 notify；和/或  
2. 商户：定时用 `out_trade_no` 查假微信，发现 SUCCESS 而本地未付则走入账（对账）。

**结论：当前「通知失败补偿」≈ 假微信端的短暂重试，不是商户端补偿体系。**

---

## 1. 两个工具各管什么

```text
Redis 锁（order:pay:lock:{orderId}）
  → 分布式互斥：同一时刻尽量只有一个 JVM 线程在「处理该单入账」
  → 不管 MySQL 提交没提交；进程挂了只靠 TTL 释放

@Transactional（挂在 handlePayNotify 上）
  → 管本次方法里访问的 DB：成功一起提交，异常回滚
  → 不管 Redis；Redis 的 SET/DEL 不会进同一事务
```

面试句：**锁防并发打架，事务防库内不一致；二者不是同一个事务管理器。**

---

## 2. 你们当前调用顺序（简化）

```text
handlePayNotify  (@Transactional 开始)
  │
  ├─ 验签 / 时间窗
  ├─ Redis SET NX  nonce   ← 不在 DB 事务里
  ├─ 查单、核金额
  └─ markPaidWithLock
        ├─ Redis tryLock
        ├─ 再查单 + casMarkPaid（DB）
        └─ finally Redis unlock   ← 往往在 DB commit 之前就执行！
  │
@Transactional 提交或回滚
```

关键缝：**`finally` 里解锁时，外层事务可能还没 commit。**

---

## 3. 缝在哪里（时序）

```text
时间线（单订单，两个 notify 几乎同时）：

T1: 拿到锁 → UPDATE（行锁）→ finally 解锁 → （稍后）事务 commit
T2:          可能在 T1 解锁后、commit 前抢到锁
             → 读到未提交前的快照？或等行锁？
             → CAS WHERE status=待付款 → 等 T1 提交后通常 rows=0
             → 若已支付则幂等返回；否则抛冲突
```

在 InnoDB、`READ COMMITTED` 下：

- T1 的 `UPDATE` 会占住该行，直到 T1 **事务结束**；
- T2 的 `UPDATE` 往往会**堵住**，等 T1 提交后再执行；
- 条件已不满足 → `rows=0` → 你们代码里再查是否已支付。

所以：**最终「只入账一次」仍主要靠 CAS + 行锁，不完全靠 Redis 锁。**  
Redis 锁的价值：减少无效并发、保护「查状态 → 再更新」之间的业务逻辑窗口，并在多实例时给出应用层互斥。

若解锁过早、CAS 又没写好，才会出事。你们 CAS 条件正确时，这条缝是「低～中」风险，不是必炸。

---

## 4. 带注释的推荐理解代码（与现网对齐）

下列片段对应 `MockPaymentGateway` 的职责划分（nonce / 锁 TTL 已按正确语义）。

### 4.1 回调入口：事务包住「读库 + 入账」

```java
/**
 * 渠道 notify 入口。
 *
 * @Transactional：本方法内通过 OrderPayPort 的 DB 操作同事务。
 * Redis（nonce / 锁）不参与该事务——成功/失败要自己 finally 处理。
 */
@Transactional(rollbackFor = Exception.class)
public Order handlePayNotify(MockPayNotifyDTO dto) {
    // 1) 验签、时间窗：失败直接抛，不占 nonce、不加锁

    // 2) nonce 去重（Redis）
    //    key = order:pay:nonce:{nonce}   // 注意：是 nonce，不是订单号
    //    同一条通知重放 → SET NX 失败
    //    若库已是已支付 → 对渠道返回成功（重试友好）
    //    若库仍未支付 → 说明上次可能还在处理/失败过，返回 429 让渠道再试更稳

    // 3) 查单、核金额、已支付幂等

    // 4) markPaidWithLock：应用锁 + CAS
    return markPaidWithLock(order.getId());
}
```

### 4.2 锁 + CAS（真正防双花的是 CAS）

```java
/**
 * 同单互斥处理入账。
 *
 * Redis 锁：短 TTL（pay-lock-ttl-seconds，如 10s）+ token + Lua 解锁。
 * DB CAS ：UPDATE ... WHERE id=? AND status=待付款 AND pay_status=未支付
 *          只有一行能成功 → 支付真相在 MySQL。
 */
public Order markPaidWithLock(Long orderId) {
    String lockKey = "order:pay:lock:" + orderId;
    long ttl = payLockTtlSeconds; // 切勿用 nonceTtl（600）顶替

    String lockToken = redis.tryLock(lockKey, ttl);
    if (lockToken == null) {
        // 别人正在入账：让渠道稍后重试，不要假装已成功
        throw tooManyRequests("支付处理中");
    }

    try {
        // 锁内再读：防止锁外状态已变
        // CAS：并发下的最后一道闸
        int rows = orderPayPort.casMarkPaid(orderId);
        if (rows == 0) {
            Order latest = requireOrder(orderId);
            if (isPaid(latest)) {
                return latest; // 别人已付成功 → 幂等成功
            }
            throw conflict("状态已变");
        }
        return requireOrder(orderId);
    } finally {
        // 注意：此时外层 @Transactional 可能尚未 commit
        // 依赖 CAS/行锁保证正确性；Redis 锁主要减并发密度
        redis.unlock(lockKey, lockToken); // Lua：value==token 才删
    }
}
```

### 4.3 和「只信事务、不要锁」的对比

| 方案 | 优点 | 缺点 |
|------|------|------|
| 仅 CAS | 实现简单，正确性够 | 并发下无效 UPDATE 多；复杂校验窗口无保护 |
| 仅 Redis 锁 | 互斥直观 | 锁释放与 DB 提交不同步；不能当唯一真相 |
| **锁 + CAS（你们）** | 实用：减撞车 + 最终正确 | 要分清两者边界；TTL/nonce 配错会伤自己 |

---

## 5. 生产上常见的进化（了解即可）

1. **短事务**：验签、写「支付流水」；入账与发消息拆开。  
2. **本地消息表 / Outbox**：事务提交后再发下游，避免「库改了消息没发」。  
3. **回调幂等表**：`notify_id` / `nonce` 落库唯一索引，比纯 Redis 更抗重启。  
4. **查单对账**：商户主动问渠道「这单到底成没成」——这才是通知失败的完整补偿。

学习项目维持「假微信短重试 + 商户锁/CAS」即可；文档级知道缺口就行。

---

## 6. 检查清单（改完 nonce / 锁 TTL 后）

- [ ] nonce key = `order:pay:nonce:` + **dto.nonce**  
- [ ] 锁 TTL = **payLockTtlSeconds**（短）  
- [ ] 同 nonce 重放且已支付 → 200 成功  
- [ ] 抢锁失败 → 429 / 明确「处理中」，不要当已支付  
- [ ] CAS `rows==0` 且已支付 → 成功；未支付 → 失败  
- [ ] 假微信 notify 失败重试看渠道日志；耗尽后需查单或补推（未做完整商户补偿）

---

## 7. 一句话

**`@Transactional` 保证这次 DB 读写的原子提交；Redis 锁减轻同单并发；钱只入一次靠 CAS。两者不同步是常态，不是 bug；nonce 键和锁 TTL 配错才是 bug。通知失败的短暂重试在假微信；完整补偿要商户查单/对账。**
