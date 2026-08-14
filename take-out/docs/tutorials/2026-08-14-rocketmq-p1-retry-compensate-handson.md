# P1：消费失败可恢复 + 支付补偿消息化（生产向）

日期：2026-08-14  
前置：P0 已跑通（Outbox 写 `ORDER_PAID` → 发 MQ → 消费幂等）  
总览：`2026-08-13-rocketmq-pay-feature-lesson.md`（F4 / F5）  
配置前缀：`mq.*`（`TakeoutMqProperties`）

本文说明两件事：**为什么要做**，以及**生产上怎么落地**。对照改仓库即可；不含「故意失败开关」一类演示代码。

---

## 1. 现网问题（为什么要有 P1）

P0 解决了「入账成功后事件怎么发出去」。P1 解决 P0 之后仍存在的两类生产风险。

### 1.1 消费失败被当成成功

RocketMQ 约定（`RocketMQListener`）：

| `onMessage` 行为 | Broker 理解 |
|------------------|-------------|
| 正常 return | 消费成功，不再投递 |
| **抛异常** | 消费失败，按策略重试 |

若下游（厨房推送、HTTP）失败时只 `log.warn` 然后 return：

- 消息从队列消失  
- 业务没做完  
- 没有自动重试，只能靠人翻日志  

现网 `afterCommit` 里「失败留待补偿」的 warn，本质就是这个问题。

### 1.2 回调线程里同步调渠道 HTTP

当前骨架：

```text
短事务提交
  → afterCommit
        → 同步 close / refund（假微信 HTTP）
        → 同步 publish ORDER_PAID
```

问题：

- 微信回调线程被 HTTP 拖慢，超时风险上升  
- HTTP 失败往往只打日志，**没有可靠重试队列**  
- 支付入账已成功，副作用失败与入账无法再用同一事务回滚，必须另有补偿通道  

因此生产上把「关单 / 退款」改成：**提交后发补偿命令消息，由独立消费者调渠道并改本地状态；失败靠 MQ 重试 + 死信可观测。**

---

## 2. 目标形态（做完长什么样）

```text
假微信 notify
  → 短事务：CAS 入账 + attempt + Outbox(ORDER_PAID)     ← P0，不变
  → afterCommit
        ├─ publishPendingForOrder（发 ORDER_PAID）      ← P0
        ├─ 发 CLOSE_CHANNEL × N（关其它渠道单）         ← P1
        └─ 或发 REFUND × 1（重复支付退款）               ← P1

OrderPaidKitchenConsumer
  → 副作用成功 → 再写 Redis 幂等
  → 暂时失败 → 抛异常 → MQ 重试
  → 超过次数 → %DLQ% +（推荐）落 mq_fail 表

PayCompensateConsumer
  → CLOSE_CHANNEL：渠道 close + 本地 CLOSED
  → REFUND：渠道 refund + 本地 REFUNDED
  → 同样：失败抛异常 / 成功写幂等 / 最终死信可查
```

**不变的铁律：**

- 短事务内不发 MQ、不调渠道 HTTP  
- 入账已提交后，副作用失败不回滚支付  
- 消费至少一次 → **必须幂等**  

---

## 3. 落地顺序

1. 统一消费失败语义（抛异常才重试）——厨房消费者先改对  
2. 配置最大重试 + 死信可观测（`%DLQ%` 和/或 `mq_fail` 表）  
3. 引入补偿 Topic / 消息体 / Producer / Consumer  
4. 改 `PayNotifyTxService.afterCommit`：发补偿消息，去掉同步 HTTP  
5. 用真实失败场景验收（下游挂掉、重投、死信），不要造「假失败开关」  

---

## 4. F4：消费失败、重试、死信

### 4.1 为什么这样分类处理

| 情况 | 做法 | 原因 |
|------|------|------|
| 暂时失败（下游超时、连接断） | **抛异常** | 让 Broker 重投，自动恢复 |
| 永久坏消息（JSON 无法解析、缺 eventId） | 打 error 后 **return** | 再试一万次也没用；避免占满重试配额。可另投人工/死信归档 |
| 已成功处理过 | 幂等短路 **return** | 至少一次投递下的正常路径，不要当失败重试 |
| 副作用成功后写幂等 | `trySetNx` 放在成功之后 | 若先写幂等再做事，中间失败会导致「以为成功、实际没做」且重试被挡掉 |

### 4.2 重试与死信（生产约定）

- 在 `@RocketMQMessageListener` 上配置合理的 `maxReconsumeTimes`（按下游 SLA，例如 3～16；以你用的 starter 是否支持该属性为准）  
- 超过次数后消息进入死信 Topic，名称一般为：

```text
%DLQ%{consumerGroup}
```

例：厨房组 `take-kitchen-consumer` → `%DLQ%take-kitchen-consumer`

**可观测最低标准（生产要能答「失败去哪了」）：**

1. 应用日志：每次失败带 `eventId` / `orderId`（便于告警）  
2. Broker 死信可查，**或** 业务表 `mq_fail` 有行（推荐，SQL 比翻 Broker 更适合值班）  

### 4.3 厨房消费者（生产写法）

```java
@Component
@RocketMQMessageListener(
        topic = "${mq.order-paid-topic:takeout-order-paid}",
        consumerGroup = "${mq.order-paid-consumer-group:take-kitchen-consumer}",
        selectorExpression = "${mq.order-paid-tag:ORDER_PAID}",
        maxReconsumeTimes = 16
)
public class OrderPaidKitchenConsumer implements RocketMQListener<String> {

    private static final String IDEMPOTENT_PREFIX = "mq:consume:order-paid:";

    private final ObjectMapper objectMapper;
    private final RedisIdempotentHelper redis;
    private final KitchenNotifyService kitchenNotifyService;

    // 构造器注入省略

    @Override
    public void onMessage(String body) {
        OrderPaidMessage msg;
        try {
            msg = objectMapper.readValue(body, OrderPaidMessage.class);
        } catch (Exception e) {
            log.error("ORDER_PAID 载荷无法解析，丢弃 body={}", body, e);
            return; // 毒消息不重试
        }

        if (!StringUtils.hasText(msg.getEventId())) {
            log.error("ORDER_PAID 缺少 eventId，丢弃");
            return;
        }

        String idemKey = IDEMPOTENT_PREFIX + msg.getEventId();
        if (StringUtils.hasText(redis.get(idemKey))) {
            return; // 已成功，ack
        }

        try {
            kitchenNotifyService.notifyNewOrder(msg);
        } catch (RuntimeException e) {
            log.error("厨房通知失败，将重试 eventId={} orderId={}",
                    msg.getEventId(), msg.getOrderId(), e);
            throw e; // 暂时失败：必须抛
        }

        redis.trySetNx(idemKey, "SUCCESS", Duration.ofDays(7).getSeconds());
    }
}
```

### 4.4 失败表 + 死信归档（推荐）

仅依赖 `%DLQ%` 时，值班要上 Broker/控制台。支付侧更常见再落库：

```sql
CREATE TABLE IF NOT EXISTS mq_fail (
  id              BIGINT        NOT NULL PRIMARY KEY AUTO_INCREMENT,
  consumer_group  VARCHAR(128)  NOT NULL,
  topic           VARCHAR(128)  NOT NULL,
  tag             VARCHAR(64)   NULL,
  event_id        VARCHAR(64)   NULL,
  biz_key         VARCHAR(64)   NULL COMMENT 'orderId / outTradeNo',
  payload         TEXT          NOT NULL,
  error_message   VARCHAR(512)  NULL,
  reconsume_times INT           NOT NULL DEFAULT 0,
  created_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_created (created_at),
  KEY idx_event_id (event_id)
) COMMENT='MQ 消费最终失败归档';
```

订阅死信 Topic，入库后便于 SQL / 工单：

```java
@Component
@RocketMQMessageListener(
        topic = "%DLQ%take-kitchen-consumer",
        consumerGroup = "take-kitchen-dlq-archiver"
)
public class OrderPaidDlqArchiver implements RocketMQListener<String> {

    @Override
    public void onMessage(String body) {
        // insert mq_fail；入库失败则抛异常，避免归档丢失
        log.error("ORDER_PAID 进入死信，已归档 payload={}", body);
    }
}
```

补偿消费者组同理挂 `%DLQ%{compensate-consumer-group}`。

### 4.5 F4 如何验收（生产手段）

不要用配置开关「假装失败」。用真实故障：

1. 临时让下游不可用（停 WebSocket 网关 / mock 抛错）  
2. 付一笔 → 日志持续「将重试」  
3. 恢复下游 → 自动成功并写幂等  
4. 或保持下游挂死至超过 `maxReconsumeTimes` → `%DLQ%` / `mq_fail` 有记录  

对比：若改成 catch 后 return，消息不会重试——这就是错误语义。

---

## 5. F5：关单 / 退款补偿消息化

### 5.1 为什么要从 afterCommit 挪走 HTTP

| | afterCommit 同步 HTTP | 补偿消息 |
|--|----------------------|----------|
| 回调耗时 | 受渠道 RT 绑定 | 发消息后即可结束 |
| HTTP 失败 | 易变成「warn 后无人处理」 | MQ 重试 + 死信 |
| 扩展 | 逻辑堆在支付回调里 | 消费者可独立扩容、限流 |
| 幂等 | 仍依赖渠道 | 渠道幂等 + **命令级** Redis/DB 幂等 |

前置条件（你们已具备）：

- 渠道 `close` / `refund` 对终态可重复调用  
- 本地 `pay_attempt` 用 CAS `updateStatus(from → to)`，重复消费不会乱改终态  

### 5.2 Topic / Tag / 消息体

| | 值 | 含义 |
|--|-----|------|
| Topic | `takeout-pay-compensate` | 支付补偿**命令**（不是领域事件） |
| Tag | `CLOSE_CHANNEL` | 关闭某个 `outTradeNo` |
| Tag | `REFUND` | 对某个 `outTradeNo` 退款 |

```java
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PayCompensateMessage {
    /** 命令唯一 ID：消费幂等键 */
    private String commandId;
    /** CLOSE_CHANNEL / REFUND */
    private String action;
    private Long orderId;
    private String outTradeNo;
    private Long payAttemptId;
    /** 期望迁出状态，供 CAS；能带则带 */
    private String statusFrom;
    private String reason;
    private String occurredAt;
}
```

`commandId` 为什么单独要：同一业务可能重发补偿；用命令 ID 做 Redis 幂等，避免「渠道可幂等但本地逻辑重复打点/告警」。

### 5.3 Producer

放 `take-out-pay`，只序列化 + `syncSend`，不写业务库：

```java
@Component
public class PayCompensateProducer {

    public void sendClose(PayCompensateMessage msg) { /* topic:CLOSE_CHANNEL */ }

    public void sendRefund(PayCompensateMessage msg) { /* topic:REFUND */ }
}
```

destination 形式与 P0 相同：`topic + ":" + tag`。Message Key 建议用 `outTradeNo`，便于按单排查。

### 5.4 改造 afterCommit（完整对照）

**目标：** 回调线程只发 MQ；`close` / `refund` HTTP 与本地终态改写放到 `PayCompensateConsumer`。

**依赖：** 先实现可用的 `PayCompensateProducer`（你仓库里目前是空方法）。

#### 5.4.1 `PayCompensateProducer` 完整实现

```java
package com.sky.takeout.pay.mq;

import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

import com.sky.takeout.pay.config.TakeoutMqProperties;
import com.sky.takeout.pojo.dto.mq.PayCompensateMessage;
import com.sky.takeout.pojo.enums.PayOutboxEventType;

import tools.jackson.databind.ObjectMapper;

/**
 * 支付补偿命令生产者：CLOSE_CHANNEL / REFUND。
 * 只负责序列化 + 发送；不写业务库、不调渠道 HTTP。
 */
@Component
public class PayCompensateProducer {

    private static final Logger log = LoggerFactory.getLogger(PayCompensateProducer.class);

    private final RocketMQTemplate rocketMQTemplate;
    private final TakeoutMqProperties mqProperties;
    private final ObjectMapper objectMapper;

    public PayCompensateProducer(RocketMQTemplate rocketMQTemplate,
            TakeoutMqProperties mqProperties, ObjectMapper objectMapper) {
        this.rocketMQTemplate = rocketMQTemplate;
        this.mqProperties = mqProperties;
        this.objectMapper = objectMapper;
    }

    public void sendClose(PayCompensateMessage msg) {
        send(mqProperties.getCompensateCloseTag(), msg);
    }

    public void sendRefund(PayCompensateMessage msg) {
        send(mqProperties.getCompensateRefundTag(), msg);
    }

    private void send(String tag, PayCompensateMessage msg) {
        try {
            // 与 Tag 对齐，避免消费者按 action 分支时对不上
            if (msg.getAction() == null) {
                if (mqProperties.getCompensateCloseTag().equals(tag)) {
                    msg.setAction(PayOutboxEventType.CLOSE_CHANNEL);
                } else {
                    msg.setAction(PayOutboxEventType.REFUND);
                }
            }
            String json = objectMapper.writeValueAsString(msg);
            String destination = mqProperties.getCompensateTopic() + ":" + tag;
            rocketMQTemplate.syncSend(
                    destination,
                    MessageBuilder.withPayload(json)
                            .setHeader("KEYS", msg.getOutTradeNo())
                            .setHeader("commandId", msg.getCommandId())
                            .build());
            log.info("补偿消息已发送 tag={} commandId={} outTradeNo={}",
                    tag, msg.getCommandId(), msg.getOutTradeNo());
        } catch (Exception e) {
            throw new IllegalStateException("send compensate failed tag=" + tag, e);
        }
    }
}
```

> pay 模块需能注入 `ObjectMapper`（已有 `spring-boot-starter-json` 或运行在 admin 传递依赖下）。

#### 5.4.2 `PayNotifyTxService`：注入 Producer，改 afterCommit

构造器增加 `PayCompensateProducer`（可去掉对 `MockWechatHttpClient` 的依赖——关单/退款 HTTP 不再在这里调）：

```java
private final PayCompensateProducer payCompensateProducer;

public PayNotifyTxService(OrderPayPort orderPayPort, PayOutboxPort payOutboxPort,
        RedisIdempotentHelper redisIdempotentHelper, PayAttemptPort payAttemptPort,
        PayCompensateProducer payCompensateProducer) {
    this.orderPayPort = orderPayPort;
    this.payOutboxPort = payOutboxPort;
    this.redisIdempotentHelper = redisIdempotentHelper;
    this.payAttemptPort = payAttemptPort;
    this.payCompensateProducer = payCompensateProducer;
}
```

**替换原来的 `afterCommit` 方法体：**

```java
@Override
public void afterCommit() {
    String action = afterCommitAction.get();
    Long orderId = orderIdHolder.get();
    String outTradeNo = outTradeNoHolder.get();

    if (action == null || orderId == null || outTradeNo == null) {
        return;
    }

    if ("CLOSE_OTHERS".equals(action)) {
        // 1) P0：发 ORDER_PAID（失败不回滚入账，靠 Outbox NEW + 扫描补发）
        try {
            payOutboxPort.publishPendingForOrder(orderId);
        } catch (Exception e) {
            log.warn("Outbox 投递失败，留待扫描 orderId={}", orderId, e);
        }
        // 2) P1：关其它渠道 → 只发补偿消息，不再同步 HTTP / 本地标 CLOSED
        try {
            enqueueCloseOthers(orderId, outTradeNo);
        } catch (Exception e) {
            log.warn("投递 CLOSE_CHANNEL 失败，留待补偿 orderId={}", orderId, e);
        }
    } else if ("REFUND".equals(action)) {
        // 短事务内已把 attempt 标成 REFUNDING；这里只发 REFUND 命令
        // 渠道 refund + 本地 REFUNDED 由 PayCompensateConsumer 完成
        try {
            enqueueRefund(orderId, outTradeNo, payAttemptId);
        } catch (Exception e) {
            log.warn("投递 REFUND 失败，留待补偿 outTradeNo={}", outTradeNo, e);
        }
    }
}
```

**删除（或废弃）同步 HTTP 的 `closeOtherUnpaidAttempts`，改为只组消息：**

```java
/**
 * 把需要关闭的其它 attempt 编成多条 CLOSE_CHANNEL。
 * 仍在 afterCommit 中查库列表可以；不要在这里调假微信。
 *
 * @param winnerOutTradeNo 本次胜出的渠道商户单号（必须是 outTradeNo，不是业务 orderNumber）
 */
private void enqueueCloseOthers(Long orderId, String winnerOutTradeNo) {
    List<PayAttempt> attempts = payAttemptPort.listByOrderId(orderId);
    for (PayAttempt attempt : attempts) {
        if (winnerOutTradeNo.equals(attempt.getOutTradeNo())) {
            continue; // 赢家自己不关
        }
        if (attempt.getStatus() == PayAttemptStatus.SUCCESS
                || attempt.getStatus() == PayAttemptStatus.REFUNDED
                || attempt.getStatus() == PayAttemptStatus.REFUNDING) {
            continue; // 终态 / 退款中不关
        }

        PayCompensateMessage msg = PayCompensateMessage.builder()
                .commandId(UUID.randomUUID().toString())
                .action(PayOutboxEventType.CLOSE_CHANNEL)
                .orderId(orderId)
                .outTradeNo(attempt.getOutTradeNo())
                .payAttemptId(attempt.getId())
                .statusFrom(attempt.getStatus() == null ? null : attempt.getStatus().getCode())
                .reason("close_others")
                .occurredAt(LocalDateTime.now().toString())
                .build();
        payCompensateProducer.sendClose(msg);
    }
}

/**
 * 重复支付：短事务已 REFUNDING，提交后发退款命令。
 */
private void enqueueRefund(Long orderId, String outTradeNo, Long payAttemptId) {
    PayCompensateMessage msg = PayCompensateMessage.builder()
            .commandId(UUID.randomUUID().toString())
            .action(PayOutboxEventType.REFUND)
            .orderId(orderId)
            .outTradeNo(outTradeNo)
            .payAttemptId(payAttemptId)
            .statusFrom(PayAttemptStatus.REFUNDING.getCode())
            .reason("duplicate_pay")
            .occurredAt(LocalDateTime.now().toString())
            .build();
    payCompensateProducer.sendRefund(msg);
}
```

需要的 import：

```java
import java.time.LocalDateTime;
import java.util.UUID;

import com.sky.takeout.pay.mq.PayCompensateProducer;
import com.sky.takeout.pojo.dto.mq.PayCompensateMessage;
import com.sky.takeout.pojo.enums.PayOutboxEventType;
```

#### 5.4.3 设置 `outTradeNoHolder` 时注意

`CLOSE_OTHERS` 分支里必须放**渠道商户单号** `payAttempt.getOutTradeNo()`，不要放 `getOrderNumber()`（业务单号）。否则 `enqueueCloseOthers` 用赢家单号过滤会错：

```java
afterCommitAction.set("CLOSE_OTHERS");
orderIdHolder.set(order.getId());
outTradeNoHolder.set(payAttempt.getOutTradeNo()); // ✅
```

`REFUND` 分支继续用 `dto.getOrderNumber()` 可以——你们回调里该字段语义就是 `out_trade_no`。

#### 5.4.4 改完后 afterCommit 还做什么 / 不做什么

| 做 | 不做 |
|----|------|
| `publishPendingForOrder` | `mockWechatHttpClient.close/refund` |
| `enqueueCloseOthers` / `enqueueRefund` | `updateStatus(... REFUNDED/CLOSED)`（交给消费者） |
| 发消息失败只打日志 | 回滚已提交的入账 |

发补偿失败的可靠增强（进阶）：补偿命令也进 Outbox；本期至少保证日志可搜 + 人工补发。

#### 5.4.5 和消费者的分工（便于联调）

```text
afterCommit          → 只 sendClose / sendRefund
PayCompensateConsumer → close/refund HTTP + CAS 改本地状态 + 幂等
```

没有消费者时，消息会在 Broker 堆积，渠道单不会关——属预期；先把 Producer + afterCommit 改完，再接 §5.5 Consumer。

---

### 5.5 CompensateConsumer

```java
@Component
@RocketMQMessageListener(
        topic = "${mq.compensate-topic:takeout-pay-compensate}",
        consumerGroup = "${mq.compensate-consumer-group:take-pay-compensate-consumer}",
        maxReconsumeTimes = 16
)
public class PayCompensateConsumer implements RocketMQListener<String> {

    private static final String IDEMPOTENT_PREFIX = "mq:consume:pay-compensate:";

    @Override
    public void onMessage(String body) {
        PayCompensateMessage msg = parseOrDiscard(body); // 毒消息 return
        String idemKey = IDEMPOTENT_PREFIX + msg.getCommandId();
        if (alreadyDone(idemKey)) {
            return;
        }

        try {
            if ("CLOSE_CHANNEL".equals(msg.getAction())) {
                mockWechatHttpClient.close(msg.getOutTradeNo());
                casCloseLocal(msg);   // updateStatus → CLOSED，updated=0 可接受
            } else if ("REFUND".equals(msg.getAction())) {
                mockWechatHttpClient.refund(msg.getOutTradeNo(), msg.getReason());
                casRefundLocal(msg);  // REFUNDING → REFUNDED
            } else {
                log.error("未知 action，丢弃 commandId={}", msg.getCommandId());
                return;
            }
        } catch (RuntimeException e) {
            log.error("补偿失败将重试 commandId={} action={}",
                    msg.getCommandId(), msg.getAction(), e);
            throw e;
        }

        redis.trySetNx(idemKey, "SUCCESS", Duration.ofDays(7).getSeconds());
    }
}
```

同一套 F4 语义：暂时失败抛异常；成功后再幂等。

---

## 6. 配置（生产）

```yaml
mq:
  order-paid-topic: takeout-order-paid
  order-paid-tag: ORDER_PAID
  order-paid-consumer-group: take-kitchen-consumer
  outbox-scan-delay-ms: 15000

  compensate-topic: takeout-pay-compensate
  compensate-close-tag: CLOSE_CHANNEL
  compensate-refund-tag: REFUND
  compensate-consumer-group: take-pay-compensate-consumer

rocketmq:
  name-server: 127.0.0.1:9876   # 生产改为内网 NameServer
  producer:
    group: take-out-admin-producer
    send-message-timeout: 3000
```

`TakeoutMqProperties` 只保留真实会用到的字段（Topic / Tag / Group / 扫描间隔）。不要为演示加「故意失败次数」配置。

ConsumerGroup 命名稳定：死信 Topic、告警、扩容都依赖它；改名等于换一组消费者。

---

## 7. 验收清单

| 场景 | 期望 |
|------|------|
| 正常支付 | Outbox `SENT`；厨房成功；幂等键存在 |
| 厨房下游短暂不可用 | 日志重试；恢复后成功，无死信 |
| 厨房长期不可用 | 进入 `%DLQ%` / `mq_fail`，可人工处理 |
| 一单多渠道，一笔成功 | 其它 attempt 收到 `CLOSE_CHANNEL`，渠道关闭 + 本地 `CLOSED` |
| 重复支付 | `REFUND` 消息 → 渠道退款 + 本地 `REFUNDED` |
| 同一 `commandId` 重投 | 幂等短路，状态不被破坏 |
| 回调耗时 | afterCommit 不再同步等待渠道 HTTP |

---

## 8. 常见坑

| 现象 | 原因 | 处理 |
|------|------|------|
| 失败不重试 | catch 后 return / 只 warn | 暂时失败必须 throw |
| 重试变「假成功」 | 失败前就写了幂等键 | 成功后再 `trySetNx` |
| 死信找不到 | Group 名与 `%DLQ%` 不一致 | 对齐 consumerGroup |
| afterCommit 又 HTTP 又发消息 | 双轨重复关/退 | 只保留消息路径 |
| 本地状态 CAS 一直 0 | `statusFrom` 与库中不一致 | 消息带准 from，或查库再迁 |
| 两套 Group 消费同一逻辑 | 会各消费一次 | 同逻辑必须同 Group |

---

## 9. 文件清单

```text
TakeoutMqProperties              # compensate-* 配置
OrderPaidKitchenConsumer         # 失败抛异常 + 成功后幂等
mq_fail 表 + DlqArchiver         # 可选但推荐
PayCompensateMessage
PayCompensateProducer
PayCompensateConsumer
PayNotifyTxService.afterCommit   # 发补偿，去掉同步 HTTP
```

---

## 10. 和其它文档的关系

| 文档 | 内容 |
|------|------|
| `2026-08-13-rocketmq-pay-feature-lesson.md` | P0～P2 功能点总览 |
| `2026-08-13-rocketmq-p0-outbox-handson.md` | Outbox + ORDER_PAID |
| **本文** | **消费可恢复 + 补偿消息化（生产向）** |
| `2026-08-14-redis-teach-fail-counter.md` | 仅本地联调演示用；**不要进生产路径** |

---

## 11. 一句话

**P1 = 消费失败要抛出来才能重试，最终进死信/失败表可查；关单和退款从回调线程同步 HTTP，改成补偿 Topic + 幂等消费者，让入账后的副作用可恢复、可观测。**
