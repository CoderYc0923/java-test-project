# P0 手抄教程：支付 Outbox + RocketMQ（ORDER_PAID）

日期：2026-08-13  
前置：`2026-08-13-rocketmq-pay-feature-lesson.md`（功能点总览）  
范围：**仅 P0**——F1 发 `ORDER_PAID`、F2 消费幂等、F3 提交后再发 / Outbox 补发  
文档性质：**教你手写的完整对照稿**（含详细注释代码）。**不会自动改你仓库**；现网仍是 `NoopPayOutboxPort`。

现网已有挂点（你**不用改**调用顺序，只需把 Noop 换成真实现）：

```text
PayNotifyTxService 短事务内：
  casMarkPaid → attempt SUCCESS → payOutboxPort.insertOrderPaid(...)
afterCommit（CLOSE_OTHERS 分支里）：
  payOutboxPort.publishPendingForOrder(orderId)
```

---

## 0. 用大白话再记一遍 P0

没有 MQ 时：事务提交后直接调「通知厨房」。  
有 Outbox + MQ 时：

```text
同一 DB 事务：改订单 + 插入 pay_outbox(NEW)
事务提交成功后：把 NEW 发到 RocketMQ，标 SENT
厨房消费者：收到消息 → 幂等检查 → 打日志/模拟备餐
```

| 步骤 | 若失败会怎样 |
|------|----------------|
| CAS / insert Outbox 任一步失败 | 整单回滚，**不会**发消息 |
| 入账已提交，发 MQ 失败 | Outbox 仍是 NEW，扫描器可补发；**不回滚**已支付 |
| 消费者处理两次 | Redis/DB 幂等，副作用只做一次 |

---

## 1. 动手顺序（照着勾）

1. Docker 起 RocketMQ（NameServer + Broker）  
2. Maven 加 `rocketmq-spring-boot-starter`  
3. Liquibase：`003-create-pay-outbox`  
4. 实体 / Mapper / 真 `PayOutboxPort` 实现（替换 Noop）  
5. Producer 发送 + Consumer 消费（含幂等）  
6. （推荐）定时扫描 `NEW` 补发  
7. 跑通：假微信 confirm → 看 Outbox → 看消费者日志  

预计：本地顺利的话 **半天～一天**。

---

## 2. Docker：本地 RocketMQ

在 `docker-compose.yml` 中**追加**（示意；镜像拉取慢时用你已配好的 mirror / `--pull never` 思路）：

```yaml
  # ----- RocketMQ（教学单机）-----
  rocketmq-namesrv:
    image: apache/rocketmq:5.3.1
    container_name: take-out-rocketmq-namesrv
    ports:
      - "9876:9876"
    command: sh mqnamesrv
    environment:
      JAVA_OPT_EXT: "-Xms256m -Xmx256m"

  rocketmq-broker:
    image: apache/rocketmq:5.3.1
    container_name: take-out-rocketmq-broker
    ports:
      - "10909:10909"
      - "10911:10911"
      - "10912:10912"
    environment:
      NAMESRV_ADDR: rocketmq-namesrv:9876
      JAVA_OPT_EXT: "-Xms256m -Xmx256m"
    # 单机开发常用：让 Broker 对外暴露宿主机可达地址
    # 若消费者连不上，检查 brokerIP1 / 防火墙
    command: >
      sh -c "echo 'brokerIP1=127.0.0.1
      enablePropertyFilter=true
      autoCreateTopicEnable=true' > /home/rocketmq/rocketmq-5.3.1/conf/broker-takeout.conf
      && sh mqbroker -n rocketmq-namesrv:9876 -c /home/rocketmq/rocketmq-5.3.1/conf/broker-takeout.conf"
    depends_on:
      - rocketmq-namesrv
```

> 路径 / 版本随镜像可能微调；若官方 compose 示例更省事，可改用社区常用 `rocketmqinc/rocketmq` 单机脚本。  
> **验收：** `docker ps` 里 namesrv、broker 为 Up；应用能连 `127.0.0.1:9876`。

常用命令可记到 `docker-command.md`：

```bash
docker compose up -d rocketmq-namesrv rocketmq-broker
docker compose logs -f rocketmq-broker
```

可选：再加 `rocketmq-dashboard` 看 Topic（非必须）。

---

## 3. Maven 依赖

建议挂在 **会启动的 `take-out-admin`**（或 `take-out-pay` + admin 传递依赖）。版本以 Maven Central 为准，写作时用：

```xml
<!-- take-out-admin/pom.xml 或 take-out-pay/pom.xml -->
<dependency>
    <groupId>org.apache.rocketmq</groupId>
    <artifactId>rocketmq-spring-boot-starter</artifactId>
    <version>2.3.6</version>
</dependency>
```

> Boot **4.1** 与 starter 若启动报自动配置问题，查该版本 release note；必要时 `@Import(RocketMQAutoConfiguration.class)`。以你本机能 `Producer`/`@RocketMQMessageListener` 跑通为准。

`application.yml`：

```yaml
rocketmq:
  name-server: 127.0.0.1:9876
  producer:
    group: takeout-pay-producer
    send-message-timeout: 3000
  # 部分版本消费者还要配 pull-consumer.group；若监听不生效再补

# 教学常量（也可用 @Value / 配置类）
takeout:
  mq:
    order-paid-topic: takeout-order-paid
    order-paid-tag: ORDER_PAID
    order-paid-consumer-group: takeout-kitchen-consumer
```

---

## 4. Liquibase：建 `pay_outbox`

### 4.1 SQL

`db/changelog/changes/003-create-pay-outbox.sql`：

```sql
-- 支付出站消息表（Outbox）：与入账同事务写入，提交后再发 MQ
CREATE TABLE IF NOT EXISTS pay_outbox (
  id            BIGINT       NOT NULL PRIMARY KEY AUTO_INCREMENT,
  event_id      VARCHAR(64)  NOT NULL COMMENT '业务事件唯一ID，供消费幂等',
  order_id      BIGINT       NOT NULL COMMENT '业务订单ID',
  order_number  VARCHAR(64)  NOT NULL COMMENT '业务订单号',
  event_type    VARCHAR(32)  NOT NULL COMMENT '如 ORDER_PAID',
  payload       TEXT         NOT NULL COMMENT 'JSON 消息体',
  status        VARCHAR(16)  NOT NULL COMMENT 'NEW/SENT/FAILED',
  retry_count   INT          NOT NULL DEFAULT 0,
  next_retry_at DATETIME     NULL COMMENT '扫描补发用，可空',
  created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_event_id (event_id),
  KEY idx_status_created (status, created_at),
  KEY idx_order_id (order_id)
) COMMENT='支付 Outbox';
```

### 4.2 YAML changeSet

`db/changelog/changes/003-create-pay-outbox.yaml`：

```yaml
databaseChangeLog:
  - changeSet:
      id: 003-create-pay-outbox
      author: take-out
      comment: 支付 Outbox 表
      changes:
        - sqlFile:
            path: db/changelog/changes/003-create-pay-outbox.sql
            relativeToChangelogFile: false
            splitStatements: true
            stripComments: false
      rollback:
        - sql: DROP TABLE IF EXISTS pay_outbox;
```

### 4.3 master 追加

```yaml
databaseChangeLog:
  - include:
      file: db/changelog/changes/001-baseline-schema-and-seed.yaml
  - include:
      file: db/changelog/changes/002-create-pay-attempt.yaml
  - include:
      file: db/changelog/changes/003-create-pay-outbox.yaml
```

已有库：`mvn liquibase:update`（不要对已有表乱 sync 掉 003）。

---

## 5. 领域模型与 Mapper

### 5.1 实体

```java
package com.sky.takeout.pojo.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

/**
 * 支付 Outbox 一行 = 一条待可靠投递的领域事件。
 */
@Data
@TableName("pay_outbox")
public class PayOutbox {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 全局唯一，建议 UUID；消费者用它做幂等 */
    private String eventId;

    private Long orderId;
    private String orderNumber;

    /** 如 ORDER_PAID */
    private String eventType;

    /** JSON 字符串 */
    private String payload;

    /** NEW / SENT / FAILED */
    private String status;

    private Integer retryCount;
    private LocalDateTime nextRetryAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
```

### 5.2 状态常量（可放 enums）

```java
package com.sky.takeout.pojo.enums;

public final class PayOutboxStatus {
    private PayOutboxStatus() {}
    public static final String NEW = "NEW";
    public static final String SENT = "SENT";
    public static final String FAILED = "FAILED";
}
```

### 5.3 Mapper

```java
package com.sky.takeout.system.mapper;

import org.apache.ibatis.annotations.Mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sky.takeout.pojo.entity.PayOutbox;

@Mapper
public interface PayOutboxMapper extends BaseMapper<PayOutbox> {
}
```

> `@MapperScan("com.sky.takeout.system.mapper")` 已有则不用改。

---

## 6. 消息体 DTO

```java
package com.sky.takeout.pay.mq;

import java.time.Instant;

import lombok.Builder;
import lombok.Data;

/**
 * 发到 Topic takeout-order-paid 的 JSON 结构。
 */
@Data
@Builder
public class OrderPaidMessage {

    /** 与 pay_outbox.event_id 一致 */
    private String eventId;

    private Long orderId;
    private String orderNumber;

    /** ISO-8601 或 epoch 均可；教学用字符串时间 */
    private String occurredAt;
}
```

---

## 7. 真·Outbox 实现（替换 Noop）

### 7.1 处理 Noop

二选一：

- 删除或去掉 `NoopPayOutboxPort` 的 `@Component`；或  
- 真实现加 `@Primary`。

同一接口只能有一个注入候选，否则启动失败。

### 7.2 Port 实现（完整注释）

```java
package com.sky.takeout.system.pay;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sky.takeout.pay.mq.OrderPaidMessage;
import com.sky.takeout.pay.mq.OrderPaidProducer;
import com.sky.takeout.pay.port.PayOutboxPort;
import com.sky.takeout.pojo.entity.PayOutbox;
import com.sky.takeout.pojo.enums.PayOutboxStatus;
import com.sky.takeout.system.mapper.PayOutboxMapper;

/**
 * Outbox 真实现：
 * - insertOrderPaid：只写库（必须在 @Transactional 内被调用）
 * - publishPendingForOrder：发 MQ + 更新 SENT（在 afterCommit 调用，无事务或新事务均可）
 */
@Primary
@Component
public class PayOutboxPortImpl implements PayOutboxPort {

    private static final Logger log = LoggerFactory.getLogger(PayOutboxPortImpl.class);
    private static final String EVENT_ORDER_PAID = "ORDER_PAID";

    private final PayOutboxMapper payOutboxMapper;
    private final OrderPaidProducer orderPaidProducer;
    private final ObjectMapper objectMapper;

    public PayOutboxPortImpl(PayOutboxMapper payOutboxMapper,
                             OrderPaidProducer orderPaidProducer,
                             ObjectMapper objectMapper) {
        this.payOutboxMapper = payOutboxMapper;
        this.orderPaidProducer = orderPaidProducer;
        this.objectMapper = objectMapper;
    }

    /**
     * F1 / F3：与入账同事务插入 NEW。
     * 这里<strong>禁止</strong>调用 RocketMQ，否则事务回滚时消息可能已经发出。
     */
    @Override
    public void insertOrderPaid(Long orderId, String orderNumber) {
        if (orderId == null || !StringUtils.hasText(orderNumber)) {
            throw new IllegalArgumentException("orderId/orderNumber required");
        }

        String eventId = UUID.randomUUID().toString().replace("-", "");
        OrderPaidMessage msg = OrderPaidMessage.builder()
                .eventId(eventId)
                .orderId(orderId)
                .orderNumber(orderNumber)
                .occurredAt(LocalDateTime.now().toString())
                .build();

        try {
            PayOutbox row = new PayOutbox();
            row.setEventId(eventId);
            row.setOrderId(orderId);
            row.setOrderNumber(orderNumber);
            row.setEventType(EVENT_ORDER_PAID);
            row.setPayload(objectMapper.writeValueAsString(msg));
            row.setStatus(PayOutboxStatus.NEW);
            row.setRetryCount(0);
            payOutboxMapper.insert(row);
            log.info("Outbox NEW inserted orderId={} eventId={}", orderId, eventId);
        } catch (Exception e) {
            // 让外层事务回滚：入账与 Outbox 必须同生共死
            throw new IllegalStateException("insert pay_outbox failed", e);
        }
    }

    /**
     * F1 / F3：事务提交后调用。
     * 查出该订单下仍为 NEW 的 ORDER_PAID，逐条发送；成功标 SENT，失败留 NEW 待扫描。
     */
    @Override
    public void publishPendingForOrder(Long orderId) {
        if (orderId == null) {
            return;
        }
        List<PayOutbox> pending = payOutboxMapper.selectList(
                new LambdaQueryWrapper<PayOutbox>()
                        .eq(PayOutbox::getOrderId, orderId)
                        .eq(PayOutbox::getEventType, EVENT_ORDER_PAID)
                        .eq(PayOutbox::getStatus, PayOutboxStatus.NEW)
                        .orderByAsc(PayOutbox::getId));

        for (PayOutbox row : pending) {
            trySendAndMark(row);
        }
    }

    /**
     * 供定时扫描调用：捞一批全局 NEW（F3 补发）。
     */
    public int publishBatchNew(int limit) {
        List<PayOutbox> pending = payOutboxMapper.selectList(
                new LambdaQueryWrapper<PayOutbox>()
                        .eq(PayOutbox::getStatus, PayOutboxStatus.NEW)
                        .orderByAsc(PayOutbox::getId)
                        .last("LIMIT " + Math.max(1, limit)));
        int ok = 0;
        for (PayOutbox row : pending) {
            if (trySendAndMark(row)) {
                ok++;
            }
        }
        return ok;
    }

    private boolean trySendAndMark(PayOutbox row) {
        try {
            orderPaidProducer.send(row.getPayload(), row.getOrderId(), row.getEventId());
            int updated = payOutboxMapper.update(null,
                    new LambdaUpdateWrapper<PayOutbox>()
                            .eq(PayOutbox::getId, row.getId())
                            .eq(PayOutbox::getStatus, PayOutboxStatus.NEW) // CAS：避免并发双发乱标
                            .set(PayOutbox::getStatus, PayOutboxStatus.SENT));
            if (updated == 1) {
                log.info("Outbox SENT id={} eventId={}", row.getId(), row.getEventId());
                return true;
            }
            log.warn("Outbox send ok but status race id={}", row.getId());
            return false;
        } catch (Exception e) {
            // 不抛给 afterCommit 去回滚入账（入账已提交）；留 NEW 给扫描器
            payOutboxMapper.update(null,
                    new LambdaUpdateWrapper<PayOutbox>()
                            .eq(PayOutbox::getId, row.getId())
                            .setSql("retry_count = retry_count + 1"));
            log.warn("Outbox send failed, keep NEW id={} eventId={}", row.getId(), row.getEventId(), e);
            return false;
        }
    }
}
```

---

## 8. Producer（发送到 RocketMQ）

```java
package com.sky.takeout.pay.mq;

import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

/**
 * 只负责「把字符串发到 Topic」，不写业务库。
 */
@Component
public class OrderPaidProducer {

    private static final Logger log = LoggerFactory.getLogger(OrderPaidProducer.class);

    private final RocketMQTemplate rocketMQTemplate;

    @Value("${takeout.mq.order-paid-topic:takeout-order-paid}")
    private String topic;

    @Value("${takeout.mq.order-paid-tag:ORDER_PAID}")
    private String tag;

    public OrderPaidProducer(RocketMQTemplate rocketMQTemplate) {
        this.rocketMQTemplate = rocketMQTemplate;
    }

    /**
     * @param jsonPayload Outbox 里存的 JSON
     * @param orderId     用作 Message Key，便于控制台排查
     * @param eventId     也可打到 header，方便链路追踪
     */
    public void send(String jsonPayload, Long orderId, String eventId) {
        String destination = topic + ":" + tag; // RocketMQ-Spring：topic:tag
        rocketMQTemplate.syncSend(
                destination,
                MessageBuilder.withPayload(jsonPayload)
                        .setHeader("KEYS", String.valueOf(orderId)) // 部分版本用 keys
                        .setHeader("eventId", eventId)
                        .build());
        log.info("RocketMQ sent topic={} tag={} orderId={} eventId={}", topic, tag, orderId, eventId);
    }
}
```

> 若你用的 starter API 是 `syncSend(destination, payload)` 直接传 String，也可以；以编译通过为准。  
> **同步发送**教学更直观；生产可再评估 async。

---

## 9. Consumer + 幂等（F2）

教学阶段消费者可放在 `take-out-system` 或 `admin` 同进程。

```java
package com.sky.takeout.system.mq;

import java.time.Duration;

import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sky.takeout.pay.mq.OrderPaidMessage;
import com.sky.takeout.pay.redis.RedisIdempotentHelper;

/**
 * 模拟「厨房」：收到 ORDER_PAID 后打日志。
 * MQ 至少一次投递 → 必须幂等。
 */
@Component
@RocketMQMessageListener(
        topic = "${takeout.mq.order-paid-topic:takeout-order-paid}",
        consumerGroup = "${takeout.mq.order-paid-consumer-group:takeout-kitchen-consumer}",
        selectorExpression = "${takeout.mq.order-paid-tag:ORDER_PAID}" // Tag 过滤；若无效可改成消费后判断
)
public class OrderPaidKitchenConsumer implements RocketMQListener<String> {

    private static final Logger log = LoggerFactory.getLogger(OrderPaidKitchenConsumer.class);
    private static final String IDEMPOTENT_PREFIX = "mq:consume:order-paid:";

    private final ObjectMapper objectMapper;
    private final RedisIdempotentHelper redis;

    public OrderPaidKitchenConsumer(ObjectMapper objectMapper, RedisIdempotentHelper redis) {
        this.objectMapper = objectMapper;
        this.redis = redis;
    }

    @Override
    public void onMessage(String body) {
        OrderPaidMessage msg;
        try {
            msg = objectMapper.readValue(body, OrderPaidMessage.class);
        } catch (Exception e) {
            // 毒消息：解析失败通常不应无限重试；教学直接打错误日志
            // 生产可进死信（P1）
            log.error("ORDER_PAID payload invalid: {}", body, e);
            return;
        }

        String idemKey = IDEMPOTENT_PREFIX + msg.getEventId();
        // SET NX：第一个消费者占坑成功；重复投递占坑失败 → 直接跳过
        boolean first = redis.trySetNx(idemKey, "1", Duration.ofDays(7).getSeconds());
        if (!first) {
            log.info("duplicate ORDER_PAID skipped eventId={} orderId={}", msg.getEventId(), msg.getOrderId());
            return;
        }

        // ===== 真正的下游副作用（教学用日志代替推厨房）=====
        log.info("[厨房] 收到已支付订单，开始备餐 orderId={} orderNumber={} eventId={}",
                msg.getOrderId(), msg.getOrderNumber(), msg.getEventId());

        // 若这里抛异常：RocketMQ 会重试；注意幂等键已占用——
        // 更严谨做法：副作用成功后再写幂等，或「处理中/成功」两阶段键（进阶）。
        // 教学简化：先占幂等再处理；若要练重试，把 trySetNx 挪到成功之后。
    }
}
```

### 幂等键放哪更稳？（回顾用）

| 策略 | 说明 |
|------|------|
| 成功后再 SET NX | 失败可重试，成功后挡重复；推荐生产 |
| 先 SET NX 再处理 | 教学简单；处理失败时重试会被跳过（假成功） |

生产更推荐：**处理成功后再写幂等**，或 DB 唯一索引 `event_id`。

更稳的教学改法：

```java
// 1) 做备餐（可失败抛错触发 MQ 重试）
kitchen.prepare(msg);
// 2) 成功后再占坑
redis.trySetNx(idemKey, "1", ttl);
// 若 2 失败但 1 已成功：备餐接口本身也要幂等（按 orderId）
```

---

## 10. NEW 扫描补发（F3）

`afterCommit` 若发送失败，靠定时任务捞 `NEW`：

```java
package com.sky.takeout.system.pay;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 每隔一段时间把卡在 NEW 的 Outbox 再发一遍。
 * 需在启动类或配置上 @EnableScheduling。
 */
@Component
public class PayOutboxScanJob {

    private static final Logger log = LoggerFactory.getLogger(PayOutboxScanJob.class);

    private final PayOutboxPortImpl payOutboxPort;

    public PayOutboxScanJob(PayOutboxPortImpl payOutboxPort) {
        // 扫描用到 publishBatchNew；若不想依赖 Impl，可把方法提进 Port
        this.payOutboxPort = payOutboxPort;
    }

    @Scheduled(fixedDelayString = "${takeout.mq.outbox-scan-delay-ms:15000}")
    public void scan() {
        int n = payOutboxPort.publishBatchNew(50);
        if (n > 0) {
            log.info("Outbox scan sent {} message(s)", n);
        }
    }
}
```

启动类增加：

```java
@EnableScheduling
```

也可把 `publishBatchNew` 加进 `PayOutboxPort` 接口，避免 Job 依赖 Impl。

---

## 11. 和现网 `PayNotifyTxService` 如何对上（你几乎不用改）

你现在的逻辑已经是正确骨架：

```text
短事务：
  ...
  payOutboxPort.insertOrderPaid(order.getId(), order.getNumber());  // ← 会写入 NEW
  afterCommitAction = CLOSE_OTHERS

afterCommit:
  closeOtherUnpaidAttempts(...)
  payOutboxPort.publishPendingForOrder(orderId);  // ← 会 send + SENT
```

手抄完真实现后：

1. 确认 `Noop` 不再注册  
2. 付一笔成功  
3. SQL：`SELECT * FROM pay_outbox ORDER BY id DESC LIMIT 5;` 应为 `SENT`  
4. 应用日志出现 `[厨房] 收到已支付订单...`

**不要**在 `insertOrderPaid` 里发 MQ。

---

## 12. 验收实验（P0 必做）

### 实验 A：开心路径

1. 下单 → requestPay → 假微信 confirm  
2. `pay_outbox` 有一行 `ORDER_PAID` 且 `SENT`  
3. 消费者日志有厨房输出  

### 实验 B：事务回滚不发消息

临时在 `insertOrderPaid` **之后**、方法 return 前 `throw new RuntimeException("boom")`：  

- 订单应仍未支付（整单回滚）  
- `pay_outbox` **无新行**  
- MQ **无**新消息  

测完删掉 throw。

### 实验 C：Broker 挂了仍能入账

1. `docker stop take-out-rocketmq-broker`  
2. 再付一笔成功  
3. 订单已支付；Outbox 为 `NEW`  
4. `docker start` Broker → 等扫描或再调 `publishPendingForOrder` → 变为 `SENT`，厨房收到  

### 实验 D：消费幂等

1. 用 Dashboard / 工具对同一条消息重投（或临时消费者里第一次处理后再次 `onMessage`）  
2. 厨房副作用只生效一次（按你采用的幂等策略验证）  

---

## 13. 常见坑

| 现象 | 原因 | 处理 |
|------|------|------|
| 启动两个 `PayOutboxPort` Bean | Noop + Impl 都在 | 去掉 Noop 的 `@Component` |
| 能发不能收 | Group/Topic/Tag 不一致；Broker 地址不对 | 对齐配置；查 `brokerIP1` |
| 事务里直接 send | 违背 F3 | 只写 Outbox |
| 回滚了但厨房收到了 | 在事务内发了 MQ | 同上 |
| 扫描一直发重复 | 发送成功但没标 SENT；或标 SENT 条件不对 | 更新加 `status=NEW` 条件 |
| Boot 4 与 starter 不兼容 | 版本问题 | 换 starter 版本或查官方 issue |

---

## 14. 文件清单（手抄对照）

```text
docker-compose.yml                          # + namesrv/broker
take-out-admin/pom.xml                      # + rocketmq-spring-boot-starter
application.yml                             # rocketmq.* / takeout.mq.*

db/changelog/changes/003-create-pay-outbox.sql
db/changelog/changes/003-create-pay-outbox.yaml
db.changelog-master.yaml                    # include 003

pojo/.../entity/PayOutbox.java
pojo/.../enums/PayOutboxStatus.java
system/mapper/PayOutboxMapper.java
system/pay/PayOutboxPortImpl.java           # @Primary，替换 Noop
system/pay/PayOutboxScanJob.java
pay/mq/OrderPaidMessage.java
pay/mq/OrderPaidProducer.java
system/mq/OrderPaidKitchenConsumer.java

（可选删除或去注解）pay/port/NoopPayOutboxPort.java
```

---

## 15. 和总览文档的关系

| 文档 | 内容 |
|------|------|
| `2026-08-13-rocketmq-pay-feature-lesson.md` | 全部功能点 P0～P2、Outbox vs 事务消息 |
| **本文** | **只把 P0 落到可手抄的完整代码** |

P1（死信、补偿消息化）手抄教程：  
[`2026-08-14-rocketmq-p1-retry-compensate-handson.md`](./2026-08-14-rocketmq-p1-retry-compensate-handson.md)

---

## 16. 一句话

**P0 = 入账同事务写 Outbox → 提交后发 RocketMQ → 消费者幂等处理；发送失败不回滚支付，靠 NEW 扫描补发。**

你按 §1 勾选做即可；卡住时对照 §12 实验与 §13 坑表。需要我「对着仓库真改代码」时，从 Milestone 2 说一声。
