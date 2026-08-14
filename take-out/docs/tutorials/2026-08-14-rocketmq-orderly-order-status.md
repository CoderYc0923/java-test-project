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

### 4.3 业务侧如何挂（示意，非强制改仓库）

在「接单 / 起送 / 完成」等 **状态 CAS 成功之后**：

```java
// 伪代码：OrderService.confirm(orderId) 内
int rows = orderMapper.casStatus(orderId, TO_BE_CONFIRMED, CONFIRMED);
if (rows == 1) {
    orderStatusChangedProducer.send(OrderStatusChangedMessage.builder()
            .eventId(UUID.randomUUID().toString())
            .orderId(orderId)
            .orderNumber(order.getNumber())
            .fromStatus("TO_BE_CONFIRMED")
            .toStatus("CONFIRMED")
            .occurredAt(LocalDateTime.now().toString())
            .build());
}
```

多条同单连续变更，只要都用同一 `orderId` 有序发送，消费端就会按发送序处理。

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
