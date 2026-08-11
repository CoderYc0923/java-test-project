# 支付回调优化：短事务、commit 后解锁、nonce、副作用、同单互斥

日期：2026-08-11  
前置阅读：`2026-08-10-transactional-redis-lock.md`  
相关现网代码：`MockPaymentGateway` / `RedisIdempotentHelper` / `OrderPayPort`  
文档性质：**目标形态教程**——文中完整代码是「推荐写法」，**不是**当前仓库已落地的代码。学习项目可先读懂，再择机改。

---

## 0. 现网问题一览

| # | 主题 | 现网 | 风险 / 味道 |
|---|------|------|-------------|
| 1 | 事务边界 | `@Transactional` 包住整段 `handlePayNotify` | 验签、Redis nonce/锁都占着 DB 连接；事务偏长 |
| 2 | 解锁时机 | `markPaidWithLock` 的 `finally` 立刻 `unlock` | 常在 DB **commit 之前**释放锁；靠 CAS/行锁兜底 |
| 4 | nonce | 只 Redis SET NX；业务失败就 `delete` | Redis 丢 key 后幂等变弱；失败删 nonce 丢「已见过」痕迹 |
| 5 | 入账后副作用 | 暂无 MQ/推送 | 以后若塞进同一事务 → 长事务 +「库成了消息没发」 |
| 6 | 同单互斥 | 回调有锁；`requestPay` 无锁 | 并发点支付可能多次向假微信下单 |

**正确性底线不变：钱只入一次靠 `casMarkPaid`（DB CAS）。**  
本文优化的是：边界更干净、并发更省、以后加副作用不踩坑。

---

## 1. 事务确实偏长 → 拆成「无事务外壳 + 短事务入账」

### 1.1 原则

```text
不要进事务：验签、时间窗、Redis nonce、Redis 加锁/注册解锁
必须进事务：查单校验金额、CAS 改订单、（可选）写支付流水 / Outbox
事务要短：只包住「读订单 + 条件更新」这几下 DB
```

### 1.2 推荐调用顺序

```text
handlePayNotify（无 @Transactional）
  ├─ 验签 / 时间窗
  ├─ Redis nonce SET NX（失败则查库幂等 / 429）
  ├─ Redis tryLock（失败 → 429）
  ├─ 注册 afterCommit / afterCompletion：解锁 + 投递副作用
  └─ markPaidInShortTx（@Transactional）  ← 唯一短事务
        ├─ 查单、核金额、已付幂等
        ├─ casMarkPaid
        └─ （可选）insert Outbox
     （方法返回后 Spring 才 commit）
  → afterCommit：unlock + 发 MQ / 推厨房 …
```

### 1.3 和现网对比

```text
现网：@Transactional 从验签一直包到 markPaidWithLock 结束
推荐：事务只包 markPaidInShortTx；外壳自己管 Redis
```

面试句：**事务只管 DB 原子；Redis 自己 finally / afterCompletion 管。**

---

## 2. 解锁时机：提到 commit 之后

### 2.1 现网缝

```text
T1: 拿到锁 → CAS UPDATE（行锁）→ finally 解锁 → （稍后）事务 commit
T2:          可能在 T1 解锁后、commit 前抢到锁
```

InnoDB 下 T2 的 UPDATE 往往会等 T1 行锁；CAS `rows=0` 后你们再查是否已付——**通常仍正确**，但 Redis 锁提前放掉，等于少了一层应用互斥。

### 2.2 推荐：有事务时用 Synchronization

```text
加锁成功后立刻 registerSynchronization：
  afterCommit     → unlock（成功路径）
  afterCompletion → 若未提交也 unlock（回滚 / 异常路径）
不要在业务 finally 里立刻 unlock（否则又提前放锁）
```

注意：

- 若当前线程**没有**活跃事务，`registerSynchronization` 会失败 → 退回普通 `try/finally` 解锁。
- 短事务方案里：外壳加锁 → 调短事务方法 → commit 后才 unlock，正好对上。

---

## 4. nonce：不要「只靠 Redis + 失败就删」

### 4.1 nonce 管什么、不管什么

| | nonce | 支付锁 `order:pay:lock:{orderId}` | CAS |
|--|-------|-----------------------------------|-----|
| 防什么 | **同一条通知重放**（同一 nonce） | 同单一时刻只跑一个入账线程 | 最终只改成功一次 |
| key | `order:pay:nonce:{nonce}` | 带订单号 | `WHERE status=待付款 AND pay_status=未付` |

**错误：**把 nonce key 做成订单号 —— 同单第二次合法回调也会被挡死。

### 4.2 现网「失败就删」的取舍

```text
现网：业务失败 → delete(nonce) → 渠道用同一条再试还能进来
优点：教学友好，重试好走通
缺点：丢了「这条通知我见过」；和真生产「验签通过就落幂等表」不一致
```

### 4.3 推荐分层（教学可只做 Redis 留坑 + 注释 DB）

```text
L1 Redis SET NX + TTL
   - 挡热路径重复 POST
   - 成功占坑后：业务失败也不删（让渠道换新通知或走查单）；
     或：仅「验签前失败」不占坑，「验签后」一律留坑 + 记失败流水

L2 DB 唯一索引（notify_id / nonce）
   - Redis 丢了也不重复入账副作用；抗重启
   - insert 冲突 → 查订单是否已付 → 幂等成功 / 处理中

L3 CAS
   - 无论如何钱只入一次
```

本文完整代码采用：**验签通过后 nonce 占坑成功则不因业务失败删除**；重复 nonce 且未付 → 429；已付 → 成功。并注释预留「DB 幂等表」扩展点。

---

## 5. 入账后副作用 → 拆事务（Outbox / afterCommit）

### 5.1 反例

```text
@Transactional
  CAS 入账
  httpClient.pushKitchen(...)   ← 网络进事务 = 事务又变长，且难回滚 HTTP
  mq.send("ORDER_PAID")         ← 可能库提交了消息没发，或消息发了库回滚
```

### 5.2 推荐

```text
短事务内：
  casMarkPaid
  insert outbox(event=ORDER_PAID, orderId, status=NEW)   // 和订单同事务

afterCommit：
  读 outbox → 发 MQ / 推厨房 → 标记 SENT
  （发失败留给定时扫描 outbox，不要堵在回调线程里无限重试）
```

没有 Outbox 表时的**最小可教版本**：`afterCommit` 里直接调本地事件 / 异步任务，并在注释里写明生产应上 Outbox。

---

## 6. 同单互斥完整梳理

三层各管一段窗口，不要互相替代。

```text
┌─────────────────────────────────────────────────────────────┐
│  requestPay（用户点支付）                                      │
│    Redis 短锁 order:pay:request:{orderId}                     │
│    → 防并发多次 createNativePay                                │
│    → 锁 TTL 短（如 5～10s）；下单成功即可释放                     │
└───────────────────────────┬─────────────────────────────────┘
                            │ 假微信 confirm 后 HTTP notify
                            ▼
┌─────────────────────────────────────────────────────────────┐
│  handlePayNotify（渠道回调）                                   │
│    nonce：挡同一条通知重放                                      │
│    order:pay:lock:{orderId}：挡同单并发入账                     │
│    短事务 + CAS：真相在 MySQL                                   │
│    afterCommit：解锁 + 副作用                                   │
└─────────────────────────────────────────────────────────────┘
```

| 场景 | 谁挡 | 期望行为 |
|------|------|----------|
| 用户连点「去支付」 | `request` 锁 | 第二次拿不到锁 → 429 或返回已有下单结果 |
| 渠道同一 nonce 重试 | nonce NX | 已付 → 200；未付处理中 → 429 |
| 渠道两路不同 nonce 同单并发 | pay 锁 + CAS | 一个入账成功；另一个锁失败或 CAS=0 后幂等 |
| 进程宕机锁未删 | 锁 TTL | TTL 到期自动可再入；CAS 保证不双花 |

---

## 7. 目标时序（对照背）

```text
handlePayNotify
  验签 / 时间窗                         （无事务）
  SET NX nonce                         （无事务；成功后不因业务失败删除）
  tryLock order:pay:lock:{id}          （无事务）
  registerSynchronization(unlock+副作用)
  markPaidInShortTx                    （短事务）
      查单 / 金额 / 状态
      casMarkPaid
      insertOutbox（可选）
  ← 事务 commit
  afterCommit: unlock + publishOutbox
```

---

## 8. 完整推荐代码（目标形态，非现网）

下列代码可直接当「改代码时的对照稿」。依赖仍是现有 `OrderPayPort` / `RedisIdempotentHelper` / `PayProperties`；Outbox 用接口占位，学习项目可先 no-op。

### 8.1 辅助：事务提交后再跑（解锁 / 副作用）

```java
package com.sky.takeout.pay.support;

import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 把「必须等 DB commit 之后」的动作挂到当前事务上。
 * 若当前没有事务（例如单测直接调），则立刻执行 runAfterCommit，
 * 并用 runAlways 模拟 afterCompletion。
 */
public final class AfterCommitActions {

    private AfterCommitActions() {
    }

    /**
     * @param runAfterCommit 仅事务成功提交后执行（解锁的「成功路径」、发 MQ）
     * @param runAlways      事务结束必执行（含回滚）；用于「无论成败都解锁」
     */
    public static void register(Runnable runAfterCommit, Runnable runAlways) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            // 没有事务：无法 afterCommit，退化为立刻执行
            try {
                if (runAfterCommit != null) {
                    runAfterCommit.run();
                }
            } finally {
                if (runAlways != null) {
                    runAlways.run();
                }
            }
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            private boolean committed;

            @Override
            public void afterCommit() {
                committed = true;
                if (runAfterCommit != null) {
                    runAfterCommit.run();
                }
            }

            @Override
            public void afterCompletion(int status) {
                // 回滚或未知结束：也要释放锁；提交路径若解锁已在 afterCommit 做，这里用标记避免解两次
                if (status != STATUS_COMMITTED && runAlways != null) {
                    runAlways.run();
                }
                // 提交成功时：解锁放在 afterCommit；若你希望解锁「只放一处」，
                // 也可以把解锁只写在 runAlways，并在 afterCompletion 无条件调用（见网关注释版）。
            }
        });
    }
}
```

> 实战更简单的解锁策略见 8.3：`unlock` **只**挂在 `afterCompletion`（无论 commit/rollback 都解），副作用只挂在 `afterCommit`。下面网关采用该简化版。

### 8.2 Outbox 端口（占位）

```java
package com.sky.takeout.pay.port;

/**
 * 本地消息表端口：与入账同事务写入，commit 后再投递。
 * 学习项目可提供 NoopPayOutboxPort（insert/publish 空实现）。
 */
public interface PayOutboxPort {

    /** 短事务内调用：写入 ORDER_PAID 事件，未发送 */
    void insertOrderPaid(Long orderId, String orderNumber);

    /** afterCommit 调用：发送并标记；失败由定时任务扫表重试 */
    void publishPendingForOrder(Long orderId);
}
```

### 8.3 推荐网关（完整注释）

```java
package com.sky.takeout.pay.gateway;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import com.sky.takeout.common.exception.BusinessException;
import com.sky.takeout.common.result.ErrorCode;
import com.sky.takeout.pay.client.MockWechatHttpClient;
import com.sky.takeout.pay.client.dto.TransactionResponse;
import com.sky.takeout.pay.config.PayProperties;
import com.sky.takeout.pay.port.OrderPayPort;
import com.sky.takeout.pay.port.PayOutboxPort;
import com.sky.takeout.pay.redis.RedisIdempotentHelper;
import com.sky.takeout.pay.sign.HmacPaySignUtil;
import com.sky.takeout.pojo.dto.order.MockPayNotifyDTO;
import com.sky.takeout.pojo.entity.Order;
import com.sky.takeout.pojo.enums.OrderStatus;
import com.sky.takeout.pojo.enums.PayStatus;

/**
 * 【目标形态 · 非现网】支付网关推荐写法。
 *
 * 相对现网 MockPaymentGateway 的关键变化：
 * 1) handlePayNotify 不再整方法 @Transactional → 短事务只包 markPaidInShortTx
 * 2) Redis 锁在 commit/rollback 之后的 afterCompletion 再 unlock
 * 3) nonce 占坑成功后业务失败不删除（重复回调：已付成功 / 未付 429）
 * 4) 入账同事务写 Outbox；afterCommit 再 publish
 * 5) requestPay 增加同单短锁，避免并发重复向假微信下单
 */
@Component
public class MockPaymentGatewayRecommended {

    private static final Logger log = LoggerFactory.getLogger(MockPaymentGatewayRecommended.class);

    /** 回调入账互斥 */
    private static final String PAY_LOCK_PREFIX = "order:pay:lock:";
    /** 用户点支付互斥（与回调锁分开，职责不同） */
    private static final String REQUEST_LOCK_PREFIX = "order:pay:request:";
    /** 渠道通知 nonce 去重 */
    private static final String NONCE_KEY_PREFIX = "order:pay:nonce:";

    private final OrderPayPort orderPayPort;
    private final PayOutboxPort payOutboxPort;
    private final PayProperties payProperties;
    private final RedisIdempotentHelper redis;
    private final MockWechatHttpClient mockWechatHttpClient;

    public MockPaymentGatewayRecommended(OrderPayPort orderPayPort,
                                         PayOutboxPort payOutboxPort,
                                         PayProperties payProperties,
                                         RedisIdempotentHelper redis,
                                         MockWechatHttpClient mockWechatHttpClient) {
        this.orderPayPort = orderPayPort;
        this.payOutboxPort = payOutboxPort;
        this.payProperties = payProperties;
        this.redis = redis;
        this.mockWechatHttpClient = mockWechatHttpClient;
    }

    // -------------------------------------------------------------------------
    // 6) 同单互斥 · 用户点支付
    // -------------------------------------------------------------------------

    /**
     * 用户发起支付：同单短锁，避免连点导致多次 createNativePay。
     * 这里不加长事务：最多一次查库 + 一次 HTTP 下单。
     */
    public Order requestPay(Long orderId) {
        if (orderId == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "订单id不能为空");
        }

        Order order = requireOrderById(orderId);
        if (isPaid(order)) {
            return order;
        }
        if (order.getStatus() != OrderStatus.PENDING_PAYMENT
                || order.getPayStatus() != PayStatus.UNPAID) {
            throw new BusinessException(ErrorCode.CONFLICT, "当前订单不可支付");
        }

        String lockKey = REQUEST_LOCK_PREFIX + orderId;
        long ttl = resolvePayLockTtl(); // 可与入账锁共用配置，或单独 request-lock-ttl
        String token = redis.tryLock(lockKey, ttl);
        if (token == null) {
            throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS, "支付发起中，请勿重复点击");
        }

        try {
            // 锁内再读：防止刚被别人付掉 / 状态变了
            order = requireOrderById(orderId);
            if (isPaid(order)) {
                return order;
            }
            if (order.getStatus() != OrderStatus.PENDING_PAYMENT
                    || order.getPayStatus() != PayStatus.UNPAID) {
                throw new BusinessException(ErrorCode.CONFLICT, "当前订单不可支付");
            }

            TransactionResponse response = mockWechatHttpClient.createNativePay(order);
            log.info("用户请求微信支付 orderId={} number={} prepayId={}",
                    orderId, order.getNumber(), response.getPrepayId());
            return order;
        } finally {
            // 下单 HTTP 与 DB 入账无关：这里用普通 finally 即可，不必 afterCommit
            redis.unlock(lockKey, token);
        }
    }

    // -------------------------------------------------------------------------
    // 1) 短事务外壳 · 回调入口（无 @Transactional）
    // -------------------------------------------------------------------------

    /**
     * 渠道 notify 入口。
     * <p>
     * 无类级/方法级长事务：验签、nonce、加锁都在事务外。
     * 真正改库只走 {@link #markPaidInShortTx}。
     */
    public Order handlePayNotify(MockPayNotifyDTO dto) {
        if (dto == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "参数不能为空");
        }

        String secret = payProperties.getMockSecret();
        if (!StringUtils.hasText(secret)) {
            throw new BusinessException(ErrorCode.ERROR, "pay.mock-secret 未配置，无法验签");
        }

        // ---------- 验签（失败不占 nonce）----------
        boolean ok = HmacPaySignUtil.verify(
                dto.getOrderNumber(),
                dto.getAmount(),
                dto.getTimestamp(),
                dto.getNonce(),
                secret,
                dto.getSign());
        if (!ok) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "验签失败");
        }

        // ---------- 时间窗（防过期重放）----------
        long skew = payProperties.getTimestampSkewSeconds() == null
                ? 300L : payProperties.getTimestampSkewSeconds();
        long now = System.currentTimeMillis() / 1000;
        if (Math.abs(now - dto.getTimestamp()) > skew) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "时间窗口过期");
        }

        // ---------- 4) nonce：挡「同一条通知」；key 必须是 nonce 本身 ----------
        long nonceTtl = payProperties.getNonceTtlSeconds() == null
                ? 600L : payProperties.getNonceTtlSeconds();
        String nonceKey = NONCE_KEY_PREFIX + dto.getNonce();
        boolean firstNonce = redis.trySetNx(nonceKey, dto.getOrderNumber(), nonceTtl);

        if (!firstNonce) {
            // 见过这条通知：已付 → 对渠道成功；未付 → 可能仍在处理 / 上次失败，让渠道稍后重试
            log.info("回调 nonce 重复 orderNumber={} nonce={}", dto.getOrderNumber(), dto.getNonce());
            Order existed = orderPayPort.findOrderByNumber(dto.getOrderNumber());
            if (existed == null) {
                throw new BusinessException(ErrorCode.CONFLICT, "订单不存在");
            }
            if (!isPaid(existed)) {
                throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS, "支付处理中，请稍后重试");
            }
            return existed;
        }

        // 生产增强（本文不实现）：此处再 insert pay_notify_log(nonce) 唯一索引；
        // 冲突则同样走「已付成功 / 未付 429」。Redis 只是热路径加速。

        // ---------- 6) 同单入账锁 ----------
        // 先解析订单 id（锁 key 需要 id；也可用 number，但要和 CAS 用的主键一致）
        Order preview = orderPayPort.findOrderByNumber(dto.getOrderNumber());
        if (preview == null) {
            // 占了 nonce 但单不存在：教学上可删 nonce 以便修数据后重试；生产更常留坑 + 告警
            redis.delete(nonceKey);
            throw new BusinessException(ErrorCode.CONFLICT, "订单不存在");
        }

        String lockKey = PAY_LOCK_PREFIX + preview.getId();
        String lockToken = redis.tryLock(lockKey, resolvePayLockTtl());
        if (lockToken == null) {
            // 注意：nonce 已占坑且故意不删 → 渠道带同一 nonce 再来会走上面「重复 nonce」分支
            // 若渠道每次换 nonce，则会卡在本锁；429 促其退避后重试即可
            throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS, "支付处理中，请勿重复回调");
        }

        // ---------- 2) 解锁挂到事务结束之后；5) 副作用挂到 afterCommit ----------
        registerUnlockAndSideEffects(lockKey, lockToken, preview.getId());

        try {
            // ---------- 1) 唯一短事务：校验 + CAS + Outbox ----------
            return markPaidInShortTx(dto);
        } catch (RuntimeException e) {
            // 4) 业务失败：默认不删 nonce（与现网 delete 相反）
            //    原因：验签已通过，留下「见过这条通知」；渠道应换策略（查单/新通知）或等 429 退避
            //    仅「明显可安全重放」的数据错误（如订单不存在）才在上面删过 nonce
            throw e;
        }
        // 故意没有 finally unlock：解锁在 TransactionSynchronization.afterCompletion
    }

    // -------------------------------------------------------------------------
    // 1) 短事务：只碰 DB
    // -------------------------------------------------------------------------

    /**
     * 短事务：查单、核金额、CAS、写 Outbox。
     * <p>
     * 必须由 Spring 代理调用（同类 self 调用会让 @Transactional 失效）。
     * 若网关自调用，请拆到独立 @Component（如 PayNotifyTxService）或注入 self。
     */
    @Transactional(rollbackFor = Exception.class)
    public Order markPaidInShortTx(MockPayNotifyDTO dto) {
        Order order = orderPayPort.findOrderByNumber(dto.getOrderNumber());
        if (order == null) {
            throw new BusinessException(ErrorCode.CONFLICT, "订单不存在");
        }

        if (order.getAmount() == null || order.getAmount().compareTo(dto.getAmount()) != 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "支付金额与订单不一致");
        }

        if (isPaid(order)) {
            return order; // 业务幂等
        }

        if (order.getStatus() != OrderStatus.PENDING_PAYMENT
                || order.getPayStatus() != PayStatus.UNPAID) {
            throw new BusinessException(ErrorCode.CONFLICT, "当前订单不可支付");
        }

        int rows = orderPayPort.casMarkPaid(order.getId());
        if (rows == 0) {
            Order latest = requireOrderById(order.getId());
            if (isPaid(latest)) {
                return latest; // 并发下别人已付成功
            }
            throw new BusinessException(ErrorCode.CONFLICT, "订单状态已变更，支付失败");
        }

        // 5) 与入账同事务写入 Outbox；真正发送在 afterCommit
        payOutboxPort.insertOrderPaid(order.getId(), order.getNumber());

        log.info("回调支付成功（待 commit） orderId={}", order.getId());
        return requireOrderById(order.getId());
    }

    // -------------------------------------------------------------------------
    // 2) + 5) Synchronization：解锁与副作用
    // -------------------------------------------------------------------------

    /**
     * 在「即将进入短事务」之前注册回调：
     * - afterCommit：投递 Outbox（仅成功入账并提交后）
     * - afterCompletion：无论提交/回滚都 unlock（避免锁泄漏；也避免 commit 前放锁）
     * <p>
     * 若当前没有同步管理器（极端：短事务注解失效），退化为立刻 unlock，并尽量 publish。
     */
    private void registerUnlockAndSideEffects(String lockKey, String lockToken, Long orderId) {
        Runnable unlock = () -> redis.unlock(lockKey, lockToken);
        Runnable publish = () -> {
            try {
                payOutboxPort.publishPendingForOrder(orderId);
            } catch (Exception ex) {
                // 不回滚已提交的入账；留给对账/定时扫 Outbox
                log.warn("afterCommit 投递 Outbox 失败 orderId={}", orderId, ex);
            }
        };

        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            // 还没有事务同步：先起短事务时 Spring 会开启同步。
            // 若此处仍 inactive，说明调用链有问题——保守：调用方 try/finally 解锁。
            // 推荐写法是：先开启事务再锁，或把「加锁+注册」放进一个带事务的门面。
            //
            // 本推荐结构是「锁在事务外，短事务在锁内」：
            // 进入 markPaidInShortTx 时才会 activate synchronization。
            // 因此这里用「延迟到短事务方法开头再注册」更稳——见下方 markPaid 内注册的变体说明。
            log.warn("无事务同步，回退为立即解锁 orderId={}", orderId);
            unlock.run();
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                publish.run();
            }

            @Override
            public void afterCompletion(int status) {
                unlock.run();
            }
        });
    }

    /*
     * 【实现注意 · 必读】
     *
     * 「锁在事务外 + 在加锁处 registerSynchronization」有一个坑：
     * 加锁那一刻可能还没有活跃事务，isSynchronizationActive()==false。
     *
     * 两种稳妥修法（选一）：
     *
     * A. 推荐教学版：把 register 挪到 markPaidInShortTx 的第一行
     *    （此时 @Transactional 已开启，同步一定 active），
     *    lockKey/token 通过方法参数或 ThreadLocal 传入。
     *
     * B. 生产常见：TransactionTemplate 在外壳显式开启事务，
     *    事务内：加锁 → register → CAS → Outbox；afterCompletion 解锁。
     *    事务稍长一点（含 Redis），但时序最简单。
     *
     * 下文 8.4 给出 A 的完整可运行拼法（避免坑）。
     */

    // ---- 小工具 ----

    private long resolvePayLockTtl() {
        Long ttl = payProperties.getPayLockTtlSeconds();
        return (ttl == null || ttl <= 0) ? 10L : ttl;
    }

    private boolean isPaid(Order order) {
        return order.getStatus() == OrderStatus.TO_BE_CONFIRMED
                && order.getPayStatus() == PayStatus.PAID;
    }

    private Order requireOrderById(Long orderId) {
        Order order = orderPayPort.findOrderById(orderId);
        if (order == null) {
            throw new BusinessException(ErrorCode.CONFLICT, "订单不存在");
        }
        return order;
    }
}
```

### 8.4 可落地拼法 A（避免「无事务同步」坑）——推荐照这个抄

核心：**加锁在事务外；`registerSynchronization` 必须在短事务方法体内第一行做。**  
因此把「入账服务」拆成独立 Bean，避免自调用导致 `@Transactional` 失效。

```java
package com.sky.takeout.pay.gateway;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import com.sky.takeout.common.exception.BusinessException;
import com.sky.takeout.common.result.ErrorCode;
import com.sky.takeout.pay.client.MockWechatHttpClient;
import com.sky.takeout.pay.client.dto.TransactionResponse;
import com.sky.takeout.pay.config.PayProperties;
import com.sky.takeout.pay.port.OrderPayPort;
import com.sky.takeout.pay.port.PayOutboxPort;
import com.sky.takeout.pay.redis.RedisIdempotentHelper;
import com.sky.takeout.pay.sign.HmacPaySignUtil;
import com.sky.takeout.pojo.dto.order.MockPayNotifyDTO;
import com.sky.takeout.pojo.entity.Order;
import com.sky.takeout.pojo.enums.OrderStatus;
import com.sky.takeout.pojo.enums.PayStatus;

/**
 * 【目标形态 · 可落地对照稿】
 * 外壳：验签 / nonce / 加锁 / 调短事务 Bean
 * 短事务 Bean：注册 afterCommit·afterCompletion → 校验 → CAS → Outbox
 */
@Component
public class MockPaymentGatewayTarget {

    private static final Logger log = LoggerFactory.getLogger(MockPaymentGatewayTarget.class);
    private static final String PAY_LOCK_PREFIX = "order:pay:lock:";
    private static final String REQUEST_LOCK_PREFIX = "order:pay:request:";
    private static final String NONCE_KEY_PREFIX = "order:pay:nonce:";

    private final OrderPayPort orderPayPort;
    private final PayProperties payProperties;
    private final RedisIdempotentHelper redis;
    private final MockWechatHttpClient mockWechatHttpClient;
    private final PayNotifyTxService payNotifyTxService;

    public MockPaymentGatewayTarget(OrderPayPort orderPayPort,
                                    PayProperties payProperties,
                                    RedisIdempotentHelper redis,
                                    MockWechatHttpClient mockWechatHttpClient,
                                    PayNotifyTxService payNotifyTxService) {
        this.orderPayPort = orderPayPort;
        this.payProperties = payProperties;
        this.redis = redis;
        this.mockWechatHttpClient = mockWechatHttpClient;
        this.payNotifyTxService = payNotifyTxService;
    }

    /** 6) 点支付：同单短锁 + HTTP 下单（无 DB 事务） */
    public Order requestPay(Long orderId) {
        if (orderId == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "订单id不能为空");
        }
        Order order = requireById(orderId);
        if (isPaid(order)) {
            return order;
        }
        assertPayable(order);

        String lockKey = REQUEST_LOCK_PREFIX + orderId;
        String token = redis.tryLock(lockKey, lockTtl());
        if (token == null) {
            throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS, "支付发起中，请勿重复点击");
        }
        try {
            order = requireById(orderId);
            if (isPaid(order)) {
                return order;
            }
            assertPayable(order);
            TransactionResponse resp = mockWechatHttpClient.createNativePay(order);
            log.info("requestPay ok orderId={} prepayId={}", orderId, resp.getPrepayId());
            return order;
        } finally {
            redis.unlock(lockKey, token);
        }
    }

    /**
     * 回调外壳：无 @Transactional。
     * 顺序：验签 → 时间窗 → nonce → 加锁 → 短事务（内注册解锁/副作用）。
     */
    public Order handlePayNotify(MockPayNotifyDTO dto) {
        if (dto == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "参数不能为空");
        }
        verifySignAndTimeWindow(dto);

        // 4) nonce：成功占坑后，默认业务失败也不删
        String nonceKey = NONCE_KEY_PREFIX + dto.getNonce();
        long nonceTtl = payProperties.getNonceTtlSeconds() == null
                ? 600L : payProperties.getNonceTtlSeconds();
        if (!redis.trySetNx(nonceKey, dto.getOrderNumber(), nonceTtl)) {
            Order existed = orderPayPort.findOrderByNumber(dto.getOrderNumber());
            if (existed == null) {
                throw new BusinessException(ErrorCode.CONFLICT, "订单不存在");
            }
            if (!isPaid(existed)) {
                throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS, "支付处理中，请稍后重试");
            }
            return existed;
        }

        Order preview = orderPayPort.findOrderByNumber(dto.getOrderNumber());
        if (preview == null) {
            redis.delete(nonceKey); // 唯一建议删 nonce 的情况：单根本不存在，允许修好后再用同通知试
            throw new BusinessException(ErrorCode.CONFLICT, "订单不存在");
        }

        String lockKey = PAY_LOCK_PREFIX + preview.getId();
        String lockToken = redis.tryLock(lockKey, lockTtl());
        if (lockToken == null) {
            throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS, "支付处理中，请勿重复回调");
        }

        // 进入独立 Bean 的短事务；解锁在事务 afterCompletion
        try {
            return payNotifyTxService.markPaidInShortTx(dto, lockKey, lockToken);
        } catch (RuntimeException e) {
            // 若短事务根本没开起来就失败，Synchronization 可能没注册 → 这里兜底解锁
            // 正常路径：TxService 第一行已注册 afterCompletion，这里再 unlock 会因 token 校验变 no-op 或解两次需幂等
            // 更干净：仅在「确认未注册」时解锁。简化起见 TxService 用 try/finally 保证注册失败也解锁。
            throw e;
        }
    }

    private void verifySignAndTimeWindow(MockPayNotifyDTO dto) {
        String secret = payProperties.getMockSecret();
        if (!StringUtils.hasText(secret)) {
            throw new BusinessException(ErrorCode.ERROR, "pay.mock-secret 未配置，无法验签");
        }
        boolean ok = HmacPaySignUtil.verify(
                dto.getOrderNumber(), dto.getAmount(), dto.getTimestamp(),
                dto.getNonce(), secret, dto.getSign());
        if (!ok) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "验签失败");
        }
        long skew = payProperties.getTimestampSkewSeconds() == null
                ? 300L : payProperties.getTimestampSkewSeconds();
        long now = System.currentTimeMillis() / 1000;
        if (Math.abs(now - dto.getTimestamp()) > skew) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "时间窗口过期");
        }
    }

    private long lockTtl() {
        Long ttl = payProperties.getPayLockTtlSeconds();
        return (ttl == null || ttl <= 0) ? 10L : ttl;
    }

    private static boolean isPaid(Order o) {
        return o.getStatus() == OrderStatus.TO_BE_CONFIRMED && o.getPayStatus() == PayStatus.PAID;
    }

    private static void assertPayable(Order o) {
        if (o.getStatus() != OrderStatus.PENDING_PAYMENT || o.getPayStatus() != PayStatus.UNPAID) {
            throw new BusinessException(ErrorCode.CONFLICT, "当前订单不可支付");
        }
    }

    private Order requireById(Long id) {
        Order o = orderPayPort.findOrderById(id);
        if (o == null) {
            throw new BusinessException(ErrorCode.CONFLICT, "订单不存在");
        }
        return o;
    }
}
```

```java
package com.sky.takeout.pay.gateway;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.sky.takeout.common.exception.BusinessException;
import com.sky.takeout.common.result.ErrorCode;
import com.sky.takeout.pay.port.OrderPayPort;
import com.sky.takeout.pay.port.PayOutboxPort;
import com.sky.takeout.pay.redis.RedisIdempotentHelper;
import com.sky.takeout.pojo.dto.order.MockPayNotifyDTO;
import com.sky.takeout.pojo.entity.Order;
import com.sky.takeout.pojo.enums.OrderStatus;
import com.sky.takeout.pojo.enums.PayStatus;

/**
 * 【目标形态】唯一带 @Transactional 的入账服务。
 * 独立 Bean，保证网关通过 Spring 代理调用，事务生效。
 */
@Component
public class PayNotifyTxService {

    private static final Logger log = LoggerFactory.getLogger(PayNotifyTxService.class);

    private final OrderPayPort orderPayPort;
    private final PayOutboxPort payOutboxPort;
    private final RedisIdempotentHelper redis;

    public PayNotifyTxService(OrderPayPort orderPayPort,
                              PayOutboxPort payOutboxPort,
                              RedisIdempotentHelper redis) {
        this.orderPayPort = orderPayPort;
        this.payOutboxPort = payOutboxPort;
        this.redis = redis;
    }

    /**
     * 短事务：第一行注册「commit 后副作用 / completion 后解锁」，再做 CAS。
     *
     * @param lockKey   外壳已加的锁
     * @param lockToken 外壳持有的 token；afterCompletion 用 Lua 安全解锁
     */
    @Transactional(rollbackFor = Exception.class)
    public Order markPaidInShortTx(MockPayNotifyDTO dto, String lockKey, String lockToken) {
        // ----- 2) 解锁放到事务结束之后；5) 副作用放到 afterCommit -----
        // 此时 Synchronization 一定 active（本方法已开事务）
        final boolean[] unlockRegistered = {false};
        // afterCommit 闭包要用的 orderId：先占位，查单成功后写入
        final Long[] paidOrderId = {null};

        try {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    Long id = paidOrderId[0];
                    if (id == null) {
                        return; // 幂等直接返回已付、或未走到 CAS 成功：无需投递
                    }
                    try {
                        payOutboxPort.publishPendingForOrder(id);
                    } catch (Exception ex) {
                        log.warn("Outbox 投递失败，留待补偿 orderId={}", id, ex);
                    }
                }

                @Override
                public void afterCompletion(int status) {
                    // COMMITTED / ROLLED_BACK / UNKNOWN：都释放锁
                    // 这样 T2 不会在 T1 未 commit 时抢到应用锁（仍可能等行锁，但密度更低）
                    redis.unlock(lockKey, lockToken);
                }
            });
            unlockRegistered[0] = true;

            Order order = orderPayPort.findOrderByNumber(dto.getOrderNumber());
            if (order == null) {
                throw new BusinessException(ErrorCode.CONFLICT, "订单不存在");
            }
            if (order.getAmount() == null || order.getAmount().compareTo(dto.getAmount()) != 0) {
                throw new BusinessException(ErrorCode.CONFLICT, "支付金额与订单不一致");
            }
            if (isPaid(order)) {
                return order;
            }
            if (order.getStatus() != OrderStatus.PENDING_PAYMENT
                    || order.getPayStatus() != PayStatus.UNPAID) {
                throw new BusinessException(ErrorCode.CONFLICT, "当前订单不可支付");
            }

            int rows = orderPayPort.casMarkPaid(order.getId());
            if (rows == 0) {
                Order latest = orderPayPort.findOrderById(order.getId());
                if (latest != null && isPaid(latest)) {
                    return latest;
                }
                throw new BusinessException(ErrorCode.CONFLICT, "订单状态已变更，支付失败");
            }

            // 5) 与 CAS 同事务写 Outbox
            payOutboxPort.insertOrderPaid(order.getId(), order.getNumber());
            paidOrderId[0] = order.getId(); // 仅 CAS 成功才在 afterCommit 投递
            log.info("CAS 入账成功 orderId={}", order.getId());
            return orderPayPort.findOrderById(order.getId());
        } finally {
            // 注册 Synchronization 之前就炸了：必须手动解锁，否则锁到 TTL
            if (!unlockRegistered[0]) {
                redis.unlock(lockKey, lockToken);
            }
        }
    }

    private static boolean isPaid(Order o) {
        return o.getStatus() == OrderStatus.TO_BE_CONFIRMED && o.getPayStatus() == PayStatus.PAID;
    }
}
```
```java
package com.sky.takeout.pay.port;

/** Outbox 端口；学习项目用 Noop 实现即可。 */
public interface PayOutboxPort {
    void insertOrderPaid(Long orderId, String orderNumber);
    void publishPendingForOrder(Long orderId);
}
```

```java
package com.sky.takeout.pay.port;

import org.springframework.stereotype.Component;

/** 教学占位：不落表、不发消息，只保证调用链能编译跑通。 */
@Component
public class NoopPayOutboxPort implements PayOutboxPort {
    @Override
    public void insertOrderPaid(Long orderId, String orderNumber) {
        // no-op：以后改成 insert into pay_outbox (...)
    }

    @Override
    public void publishPendingForOrder(Long orderId) {
        // no-op：以后改成发 MQ / 推厨房，并 update status=SENT
    }
}
```

---

## 9. 和现网差异清单（改代码时用）

| 项 | 现网 | 目标 |
|----|------|------|
| `handlePayNotify` 事务 | 整方法 `@Transactional` | 无；只 `PayNotifyTxService` 短事务 |
| 解锁 | `finally` 立即 unlock | `afterCompletion` unlock |
| nonce 失败 | `delete(nonceKey)` | 默认不删；仅订单不存在等可删 |
| 副作用 | 无 | 同事务 `insertOutbox` + `afterCommit publish` |
| `requestPay` | 无锁 | `order:pay:request:{id}` 短锁 |
| Bean 拆分 | 单类 | 网关外壳 + `PayNotifyTxService`（事务代理） |

---

## 10. 检查清单

- [ ] 验签失败不占 nonce  
- [ ] nonce key = `order:pay:nonce:` + **dto.nonce**（不是订单号）  
- [ ] 业务失败默认不删 nonce；重复且未付 → 429；已付 → 成功  
- [ ] 入账锁 TTL = `payLockTtlSeconds`（短）  
- [ ] `@Transactional` 只包 CAS（+ Outbox），且经 Spring 代理调用  
- [ ] unlock 在 `afterCompletion`，不在业务 `finally` 抢跑  
- [ ] publish 只在 `afterCommit`；失败不回滚已支付  
- [ ] `requestPay` 与回调锁前缀不同、职责不同  
- [ ] 最终仍靠 `casMarkPaid` 防双花  

---

## 11. 一句话

**短事务只改库；锁在 commit 后放；nonce 认「通知」、锁认「订单」；副作用走 Outbox + afterCommit；点支付与回调各一把短锁——钱只入一次永远靠 CAS。**

前篇讲「锁和事务为什么不是一回事」；本篇讲「怎么把边界收干净」。现网可继续用 CAS 兜底；要演进时以 **§8.4** 为对照稿即可。
