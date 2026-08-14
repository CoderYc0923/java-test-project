# RocketMQ 顺序消息：订单状态流转（生产向）

日期：2026-08-14  
前置：已熟悉普通并发消费（如 `ORDER_PAID` + `@RocketMQMessageListener`）  
总览挂点：`2026-08-13-rocketmq-pay-feature-lesson.md` **F7**  
配置前缀建议：`mq.*`（可扩 `TakeoutMqProperties`）

本文说明：**顺序消息解决什么、不解决什么**，以及用「同一订单状态变更」落地时的生产写法。  
消费者只留状态分支挂点，**不写具体业务**（不改库、不推 WS）；你后续按领域服务填空即可。

---

## 1. 顺序消息是什么

### 1.1 定义（生产语义）

对**同一个业务分片键**（本例：`orderId`）发出的多条消息，Broker 投递与消费保证：

```text
发送顺序：已支付 → 接单 → 派送 → 完成
消费顺序：同一 orderId 上，必须按上述次序处理，不能「派送」先于「接单」被消费完
```

实现要点：

1. **发送侧**：按 `orderId` 哈希，固定打进**同一条 MessageQueue（分区）**  
2. **消费侧**：对该队列使用**顺序消费**（单线程推进该队列，或严格按位点顺序）

不同 `orderId` 可以并行，互不影响。

### 1.2 不是什么

| 顺序消息 **不是** | 说明 |
|-------------------|------|
| 「A 消费者做完再发 B」的工作流引擎 | 那是编排 / Saga；顺序消息只保证**已发出消息**的相对次序 |
| 全局全 Topic 绝对有序 | 通常只保证 **同一分片键** 有序 |
| 支付入账正确性的手段 | 入账靠 DB CAS / 状态机；你们 P0 的 `ORDER_PAID` **不必**改成顺序消息 |

### 1.3 和本项目现有链路的关系

```text
支付入账成功 → Outbox → ORDER_PAID（普通并发消息）→ 厨房通知
订单后续：接单 / 派送 / 完成 → 若用事件广播且要求同单不乱序
            → 可用「订单状态变更」顺序 Topic（本文）
```

厨房来单可以继续用现有并发消费；**状态机演进事件**才是顺序消息的典型场景。

---

## 2. 为什么订单状态要用顺序（以及何时可以不用）

### 2.1 乱序会怎样

假设同单快速连续发出：

```text
T1 发送：PAID
T2 发送：CONFIRMED（商家接单）
```

若并发消费、同单落到不同队列，可能出现：

```text
消费者先处理 CONFIRMED，再处理 PAID
→ 按事件「盲目改状态」时，库可能被写成非法跃迁，或后到的 PAID 覆盖了已接单
```

用顺序消息后：同 `orderId` 上 PAID 一定先于 CONFIRMED 被该有序消费者处理完（在成功 ack 的前提下）。

### 2.2 生产上仍建议的双重保险

即使有顺序消息，消费端仍应：

- **按当前 DB 状态做合法迁移**（非法则跳过或进失败表，而不是盲写）  
- **幂等**（同一 `eventId` 只生效一次）  

顺序降低乱序概率；**状态机才是最终正确性**。不要「有了顺序就不校验状态」。

### 2.3 何时不要用

- 纯通知、互相无依赖（多个独立下游订同一 `ORDER_PAID`）→ 普通并发更好  
- 强依赖吞吐、分片键极度倾斜（大商户单量打满一个分区）→ 评估热点  
- 仅「支付成功」一条事件 → 无多条同单事件可排，顺序无意义  

---

## 3. 领域约定（本教程）

### 3.1 Topic / Tag / Group

| 项 | 建议值 | 说明 |
|----|--------|------|
| Topic | `takeout-order-status` | 订单状态变更事件 |
| Tag | 与状态一致，如 `PAID` / `CONFIRMED` / `DELIVERING` / `COMPLETED` | 便于过滤与排查 |
| 分片键 | `orderId` | 发送选择队列、排查用 |
| Consumer Group | `takeout-order-status-consumer` | 顺序消费组；改名等于新组重消费 |

### 3.2 消息体

```java
package com.sky.takeout.pojo.dto.mq;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 订单状态变更事件（顺序消息载荷）。
 * 同一 orderId 上多条事件应按 occurred 次序被顺序消费者处理。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderStatusChangedMessage {

    /** 事件唯一 ID：消费幂等键 */
    private String eventId;

    /** 分片键 / 业务主键 */
    private Long orderId;

    private String orderNumber;

    /**
     * 变更后的目标状态 code，例如：
     * PAID / CONFIRMED / DELIVERING / COMPLETED / CANCELLED
     * （与你们 OrderStatus 枚举对齐即可）
     */
    private String toStatus;

    /** 可选：变更前状态，便于审计与排障 */
    private String fromStatus;

    /** 业务发生时间（ISO 或 LocalDateTime.toString） */
    private String occurredAt;
}
```

### 3.3 配置

```yaml
mq:
  order-status-topic: takeout-order-status
  order-status-consumer-group: takeout-order-status-consumer
```

`TakeoutMqProperties` 增加对应字段即可。

---

## 4. 发送：按 orderId 选队列

### 4.1 为什么必须选队列

普通 `syncSend(destination, msg)` 可能把同单消息散到不同 Queue → **无法**跨队列保序。  
顺序发送要用 **MessageQueueSelector**（或封装好的有序发送 API），让同一 `orderId` 始终进入同一 Queue。

### 4.2 Producer（完整对照）

```java
package com.sky.takeout.system.mq;

import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.common.message.MessageQueue;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

import com.sky.takeout.pay.config.TakeoutMqProperties;
import com.sky.takeout.pojo.dto.mq.OrderStatusChangedMessage;

import tools.jackson.databind.ObjectMapper;

/**
 * 订单状态变更 — 顺序消息生产者。
 * <p>
 * 关键：同一 orderId 哈希到同一 MessageQueue，为顺序消费提供前提。
 */
@Component
public class OrderStatusChangedProducer {

    private static final Logger log = LoggerFactory.getLogger(OrderStatusChangedProducer.class);

    private final RocketMQTemplate rocketMQTemplate;
    private final TakeoutMqProperties mqProperties;
    private final ObjectMapper objectMapper;

    public OrderStatusChangedProducer(RocketMQTemplate rocketMQTemplate,
            TakeoutMqProperties mqProperties, ObjectMapper objectMapper) {
        this.rocketMQTemplate = rocketMQTemplate;
        this.mqProperties = mqProperties;
        this.objectMapper = objectMapper;
    }

    /**
     * 发送一条状态变更事件（有序）。
     * 调用方应在「状态已成功写入 DB」之后发送（或走 Outbox 再发），避免先发消息库未提交。
     */
    public void send(OrderStatusChangedMessage event) {
        if (event.getOrderId() == null) {
            throw new IllegalArgumentException("orderId required for orderly send");
        }
        if (event.getEventId() == null || event.getToStatus() == null) {
            throw new IllegalArgumentException("eventId and toStatus required");
        }

        try {
            String json = objectMapper.writeValueAsString(event);
            // Tag = 目标状态，便于控制台过滤；destination = topic:tag
            String destination = mqProperties.getOrderStatusTopic() + ":" + event.getToStatus();

            /*
             * syncSendOrderly(destination, payload, hashKey)
             * hashKey：同一 orderId → 同一队列。部分版本签名为
             * syncSendOrderly(destination, Message, hashKey)。
             * 若你用的 starter 无此方法，见下方「等价手写 Selector」。
             */
            SendResult result = rocketMQTemplate.syncSendOrderly(
                    destination,
                    MessageBuilder.withPayload(json)
                            .setHeader("KEYS", String.valueOf(event.getOrderId()))
                            .setHeader("eventId", event.getEventId())
                            .build(),
                    String.valueOf(event.getOrderId()));

            log.info("orderly status event sent orderId={} toStatus={} msgId={}",
                    event.getOrderId(), event.getToStatus(), result.getMsgId());
        } catch (Exception e) {
            // 生产：与业务事务策略配合——通常状态已落库则记录失败待补发，而不是回滚状态
            throw new IllegalStateException(
                    "send orderly order-status failed orderId=" + event.getOrderId(), e);
        }
    }
}
```

若 `syncSendOrderly` 在你版本中不存在，等价思路：

```java
// 伪代码：list 出 topic 的 MessageQueue，按 orderId.hashCode() % queues.size() 选中后同步发送
MessageQueue selected = queues.get(Math.floorMod(orderId.hashCode(), queues.size()));
producer.send(message, selected);
```

**发送时机（生产）：**

```text
推荐：DB 状态迁移成功（同事务或之后）→ 再发顺序消息
更好：状态变更也写 Outbox，扫描器用有序发送补发（与 P0 同一可靠性模型）
禁止：事务未提交就发，导致消费端读到旧状态
```

### 4.3 业务侧如何挂（优化教程：CAS + 抽公共 + afterCommit）

> **示意，非强制立刻改仓库。** 目标：把现在的「先 assert 再 `updateById`」收成可复用的状态机迁移，并在**事务提交后**再发有序状态消息。

#### 4.3.1 现状问题（对照你现在的 `confirm`）

```text
getOrder → assertStatus(期望) → setStatus → updateById
```

并发下两个人同时接同一单时，两人都可能通过 assert，两人都 update 成功（或后写覆盖），**没有「只有一人迁移成功」的保证**。  
发 MQ 若写在 `@Transactional` 方法返回前同步发送，消费端还可能读到**未提交**的旧状态。

#### 4.3.2 目标形态

```text
CAS：WHERE id=? AND status=fromStatus  →  SET status=toStatus（+ 附带字段）
  rows==1：本请求赢得迁移
  rows==0：冲突 / 已被别人改走 → 抛业务异常
事务提交后（afterCommit）：OrderStatusProducer.send（有序，hashKey=orderId）
```

#### 4.3.3 抽一层公共：`transitionAndPublish`

接单 / 派送 / 完成 / 取消，差异只有：

| 差异点 | 例子 |
|--------|------|
| `fromStatus` | 接单：待接单；派送：已接单 |
| `toStatus` | 接单→已接单；派送→派送中 |
| 额外字段 patch | 拒单写 `rejectionReason`；取消写 `cancelReason` |
| 错误文案 | 「只有待接单才能接单」 |

抽成一个私有模板方法（名字随意）：

```text
transitionAndPublish(
    orderId,
    fromStatus,
    toStatus,
    patch回调,          // 可选：给 UPDATE 补字段
    conflictMessage     // CAS 失败文案
)
```

内部步骤固定：

1. （可选）先 `selectById` 拿 `orderNumber` 等展示字段；**不以这次读到的 status 当写条件**。  
2. `casUpdateStatus(orderId, from, to, patch)` → 影响行数。  
3. `rows != 1` → `BusinessException(conflictMessage)`。  
4. **注册** `TransactionSynchronization.afterCommit`：组装 `OrderStatusMessage`（或教程里的 `OrderStatusChangedMessage`），调用已有 `OrderStatusProducer.send`。  
5. 方法在事务内返回；真正发 MQ 发生在提交成功之后。

各业务方法变薄：

```text
confirm   → transitionAndPublish(id, TO_BE_CONFIRMED, CONFIRMED, null, "只有待接单…")
delivery  → transitionAndPublish(id, CONFIRMED, DELIVERY_IN_PROGRESS, null, "…")
complete  → transitionAndPublish(id, DELIVERY_IN_PROGRESS, COMPLETED, patch交货时间, "…")
rejection → transitionAndPublish(id, TO_BE_CONFIRMED, CANCELLED, patch拒单原因+时间, "…")
cancel    → 先校验 from ∈ 可取消集合，再 CAS（from=当前库状态 或 对每个允许 from 尝试；见下）
```

**取消**若允许多种原状态：不要用「读出来的 status 直接当 CAS from」以外的写法——要么：

- CAS：`WHERE id=? AND status IN (待接单,已接单,派送中)` 一次更新成取消（单 SQL）；或  
- 读到 current 后 `transitionAndPublish(id, current, CANCELLED, …)`（读与写之间仍有缝，但比无 CAS 好；严格可用 `IN` 版）。

#### 4.3.4 Mapper CAS（示意 SQL）

```sql
UPDATE orders
SET status = #{toStatus},
    -- 可选：rejection_reason / cancel_reason / delivery_time / cancel_time
    update_time = NOW()   -- 若有该列
WHERE id = #{id}
  AND status = #{fromStatus}
```

MyBatis-Plus 等价：`LambdaUpdateWrapper` 带 `.eq(Order::getStatus, from)` + `.set(Order::getStatus, to)`，返回 `update` 影响行数。  
与支付侧 `casMarkPaid` **同一思想**，只是订单状态机多几个 from→to。

#### 4.3.5 为什么发消息放 afterCommit（推荐）

| 做法 | 结果 |
|------|------|
| 事务内 `update` 后立刻 `syncSendOrderly` | 可能：消息已发、事务稍后回滚；或消费者读库仍旧 |
| **afterCommit 再 send** | 库已可见新状态再通知下游 |
| 同事务写状态 Outbox，扫描有序发送 | 最稳（与支付 P0 同模型）；本期可先 afterCommit |

发送失败策略（与文档 §4.2 一致）：状态已提交则**不要轻易回滚订单**；记日志 / 进补发（Outbox 扫描），由运维或定时任务用同一 `orderId` 有序重发。

#### 4.3.6 伪代码总览（手抄对照）

```java
// ========== 公共：CAS + afterCommit 发有序消息 ==========
private void transitionAndPublish(Long orderId,
                                  OrderStatus from,
                                  OrderStatus to,
                                  Consumer<Order> patch,      // 可 null
                                  String conflictMsg) {
    Order snapshot = getOrder(orderId); // 主要拿 number；不作为写条件

    Order patchEntity = new Order();
    patchEntity.setStatus(to);
    if (patch != null) {
        patch.accept(patchEntity); // 例如 setRejectionReason / setCancelTime
    }

    int rows = orderMapper.update(patchEntity,
            new LambdaUpdateWrapper<Order>()
                    .eq(Order::getId, orderId)
                    .eq(Order::getStatus, from));
    if (rows != 1) {
        throw new BusinessException(ErrorCode.CONFLICT, conflictMsg);
    }

    String eventId = UUID.randomUUID().toString();
    Long oid = orderId;
    String number = snapshot.getNumber();

    TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
        @Override
        public void afterCommit() {
            orderStatusProducer.send(OrderStatusMessage.builder()
                    .eventId(eventId)
                    .orderId(oid)
                    .orderNumber(number)
                    .fromStatus(from)   // 若消息体是枚举；字符串版则用 name()
                    .toStatus(to)
                    .occurredAt(LocalDateTime.now().toString())
                    .build());
            // Producer 内部：syncSendOrderly(..., hashKey = String.valueOf(orderId))
        }
    });
}

// ========== 业务变薄 ==========
public void confirm(OrderConfirmDTO dto) {
    transitionAndPublish(dto.getId(),
            OrderStatus.TO_BE_CONFIRMED, OrderStatus.CONFIRMED,
            null, "只有待接单订单才能接单");
}

public void delivery(Long id) {
    transitionAndPublish(id,
            OrderStatus.CONFIRMED, OrderStatus.DELIVERY_IN_PROGRESS,
            null, "只有待派送订单才能派送");
}
```

多条同单连续变更（接单→派送→完成），只要都走该模板且 **hashKey 均为 orderId**，消费端顺序监听就会按发送序处理。

#### 4.3.7 手写检查清单

- [ ] 写库条件带 `status = from`，不靠「先读再盲写」  
- [ ] `rows == 1` 才算成功；`0` 当冲突  
- [ ] confirm / delivery / complete / rejection（及 cancel）复用同一迁移方法  
- [ ] MQ 在 **afterCommit**（或 Outbox），不在未提交事务里硬发  
- [ ] `eventId` 每次迁移新生成；消费端按 eventId 幂等  
- [ ] 发送失败不误回滚已提交状态（除非你明确要强一致同步发且能接受回滚）

#### 4.3.8 和支付 CAS 的对照

| | 支付入账 | 订单状态（本节省） |
|--|----------|-------------------|
| CAS 条件 | 待付款+未支付 | `status = fromStatus` |
| 成功后副作用 | Outbox → 厨房 MQ | afterCommit → 状态有序 MQ |
| 公共抽象 | `casMarkPaid` | `transitionAndPublish` |

学完支付 CAS 后，把订单四五个入口收成一个模板，是同一肌肉记忆。

---

## 5. 消费：顺序监听 + 挂点（无具体业务）

### 5.1 必须开顺序消费模式

并发消费（默认）下，同队列也可能多线程乱序处理。  
顺序消费：`consumeMode = ConsumeMode.ORDERLY`（属性名以你用的 `rocketmq-spring-boot-starter` 为准）。

### 5.2 Consumer（完整对照，业务为空实现）

```java
package com.sky.takeout.system.mq;

import java.time.Duration;

import org.apache.rocketmq.spring.annotation.ConsumeMode;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.sky.takeout.pay.redis.RedisIdempotentHelper;
import com.sky.takeout.pojo.dto.mq.OrderStatusChangedMessage;

import tools.jackson.databind.ObjectMapper;

/**
 * 订单状态变更 — 顺序消费者。
 * <p>
 * ConsumeMode.ORDERLY：按队列串行消费，配合发送侧 orderId 选队列，保证同单事件有序。
 * 本类不实现具体领域逻辑，只做解析 / 幂等骨架 / 按 toStatus 分发挂点。
 */
@Component
@RocketMQMessageListener(
        topic = "${mq.order-status-topic:takeout-order-status}",
        consumerGroup = "${mq.order-status-consumer-group:takeout-order-status-consumer}",
        consumeMode = ConsumeMode.ORDERLY,
        maxReconsumeTimes = 8
)
public class OrderStatusChangedConsumer implements RocketMQListener<String> {

    private static final Logger log = LoggerFactory.getLogger(OrderStatusChangedConsumer.class);
    private static final String IDEMPOTENT_PREFIX = "mq:consume:order-status:";

    private final ObjectMapper objectMapper;
    private final RedisIdempotentHelper redis;

    public OrderStatusChangedConsumer(ObjectMapper objectMapper, RedisIdempotentHelper redis) {
        this.objectMapper = objectMapper;
        this.redis = redis;
    }

    @Override
    public void onMessage(String body) {
        OrderStatusChangedMessage msg;
        try {
            msg = objectMapper.readValue(body, OrderStatusChangedMessage.class);
        } catch (Exception e) {
            // 毒消息：不重试，避免堵住该顺序队列
            log.error("订单状态消息无法解析，丢弃 body={}", body, e);
            return;
        }

        if (msg.getOrderId() == null || !StringUtils.hasText(msg.getEventId())
                || !StringUtils.hasText(msg.getToStatus())) {
            log.error("订单状态消息缺必填字段，丢弃 body={}", body);
            return;
        }

        String idemKey = IDEMPOTENT_PREFIX + msg.getEventId();
        if (StringUtils.hasText(redis.get(idemKey))) {
            log.info("订单状态事件已处理，跳过 eventId={} orderId={}",
                    msg.getEventId(), msg.getOrderId());
            return;
        }

        try {
            dispatch(msg);
        } catch (RuntimeException e) {
            // 暂时失败：抛出 → 顺序消费会阻塞该队列后续消息直到成功或进死信
            // 这是顺序消费的代价：一条卡住，同队列（通常含该分片其它单）会受影响
            log.error("处理订单状态事件失败，将顺序重试 eventId={} orderId={} toStatus={}",
                    msg.getEventId(), msg.getOrderId(), msg.getToStatus(), e);
            throw e;
        }

        redis.trySetNx(idemKey, "SUCCESS", Duration.ofDays(7).getSeconds());
    }

    /**
     * 按目标状态分发。此处不写业务，只留挂点。
     * 后续可注入 OrderStatusApplicationService，在各 case 内做合法状态迁移校验等。
     */
    private void dispatch(OrderStatusChangedMessage msg) {
        String to = msg.getToStatus();
        switch (to) {
            case "PAID" -> onPaid(msg);
            case "CONFIRMED" -> onConfirmed(msg);
            case "DELIVERING" -> onDelivering(msg);
            case "COMPLETED" -> onCompleted(msg);
            case "CANCELLED" -> onCancelled(msg);
            default -> {
                // 未知状态：打日志后 ack，避免毒 tag 堵队列；也可改抛异常进死信由人工看
                log.error("未知 toStatus={}，跳过 eventId={}", to, msg.getEventId());
            }
        }
    }

    /** 挂点：支付成功后的领域处理（例如通知下游、刷新视图模型）。当前空实现。 */
    private void onPaid(OrderStatusChangedMessage msg) {
        log.info("order-status hook PAID orderId={} eventId={}", msg.getOrderId(), msg.getEventId());
        // TODO: 注入领域服务后实现；需做状态机校验 + 幂等
    }

    /** 挂点：商家接单。 */
    private void onConfirmed(OrderStatusChangedMessage msg) {
        log.info("order-status hook CONFIRMED orderId={} eventId={}", msg.getOrderId(), msg.getEventId());
    }

    /** 挂点：开始配送。 */
    private void onDelivering(OrderStatusChangedMessage msg) {
        log.info("order-status hook DELIVERING orderId={} eventId={}", msg.getOrderId(), msg.getEventId());
    }

    /** 挂点：完成。 */
    private void onCompleted(OrderStatusChangedMessage msg) {
        log.info("order-status hook COMPLETED orderId={} eventId={}", msg.getOrderId(), msg.getEventId());
    }

    /** 挂点：取消。 */
    private void onCancelled(OrderStatusChangedMessage msg) {
        log.info("order-status hook CANCELLED orderId={} eventId={}", msg.getOrderId(), msg.getEventId());
    }
}
```

### 5.3 顺序消费的生产代价（必读）

| 点 | 说明 |
|----|------|
| 单队列阻塞 | 一条消息反复失败时，**同队列后续消息等待**（含碰巧哈希到同队列的其它订单） |
| 毒消息 | 解析失败应 **return ack**，不要无限抛；业务永久失败要想清楚是否该跳过 |
| 死信 | 与 F4 相同，可挂 `%DLQ%{order-status-consumer-group}` → `mq_fail` |
| 幂等 | 成功后再写 Redis；与厨房消费者同一原则 |

---

## 6. 端到端流程（同单）

```text
DB: 待接单 → 接单成功
  → send(orderId=100, toStatus=CONFIRMED)   // 有序，队列 Qk

DB: 接单 → 配送成功
  → send(orderId=100, toStatus=DELIVERING)  // 同一 Qk

顺序消费者（ORDERLY）在 Qk 上：
  1) 处理 CONFIRMED 挂点 → 成功 → 幂等
  2) 再处理 DELIVERING 挂点 → 成功 → 幂等

orderId=200 可能在另一队列 Qj，与 100 并行，互不阻塞（除非哈希碰撞进同一队列）
```

---

## 7. 验收建议

1. 对同一 `orderId` 连续有序发送 `PAID` → `CONFIRMED` → `DELIVERING`  
2. 消费日志时间戳顺序与发送一致  
3. 在 `onConfirmed` 临时 `throw`：应看到后续 `DELIVERING` **暂不处理**；恢复后先完成 CONFIRMED 再 DELIVERING  
4. 换不同 `orderId` 并发发送：允许交叉，互不影响  

（可用临时管理接口或单测里调 `OrderStatusChangedProducer`，不必先接真实接单 API。）

---

## 8. 文件清单

```text
pojo/.../OrderStatusChangedMessage.java
TakeoutMqProperties                 # + orderStatusTopic / consumerGroup
application.yml                     # mq.order-status-*
OrderStatusChangedProducer.java     # syncSendOrderly + orderId hashKey
OrderStatusChangedConsumer.java     # ConsumeMode.ORDERLY + 空挂点
（可选）%DLQ% 归档 → mq_fail
```

---

## 9. 和支付 P0/P1 怎么选

| 场景 | 建议 |
|------|------|
| 支付成功通知厨房 | 普通消息 + 幂等（现网） |
| 关单/退款补偿 | 普通消息 + 幂等（P1） |
| 同单多条状态演进事件且消费端敏感于顺序 | **顺序消息（本文）** |
| 只靠消息保证「库状态正确」 | **不够**；必须 DB 状态机 |

---

## 10. 一句话

**顺序消息 = 同一 `orderId` 的状态事件按发送次序进入同一队列并顺序消费；用在订单状态流转广播上。发送用 `orderId` 选队列，消费开 `ORDERLY`，业务挂在 `onPaid/onConfirmed/...` 空方法里后续再填，且仍要用状态机 + 幂等兜底。**
