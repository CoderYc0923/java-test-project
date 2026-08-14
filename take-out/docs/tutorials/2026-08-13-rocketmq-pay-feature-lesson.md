# RocketMQ 功能教学（挂在外卖支付中心）

日期：2026-08-13  
选型：Apache RocketMQ（国内业务消息常见）  
挂载点：现有支付入账短事务 + `PayOutboxPort`（当前 `NoopPayOutboxPort`）+ `afterCommit`  
文档性质：**功能教学 / 学习路线**——先弄清「练什么」，再动手接入；**默认不假定仓库已引入 RocketMQ 依赖**。

相关代码（现网）：

- `PayNotifyTxService.markPaidOrRufundInShortTx`：CAS 入账、`insertOrderPaid`、`afterCommit` 里 `publishPendingForOrder` / 关单 / 退款  
- `PayOutboxPort` / `NoopPayOutboxPort`：发件箱端口占位  
- 设计背景：`docs/tutorials/2026-08-11-pay-callback-short-tx-and-lock.md`、`docs/superpowers/specs/2026-08-11-pay-attempt-single-active-design.md`

---

## 0. 为什么用 RocketMQ 练这个项目

支付里真正适合 MQ 的，不是「验签 / CAS / Redis 锁」，而是**入账已经成功之后**的副作用：

```text
DB 已提交：订单待接单+已支付
  → 通知厨房 / 推送用户 / 记流水 / 异步关其它渠道单（可选）
这些不该堵在微信回调线程里同步干完
```

| 现网 | 接 RocketMQ 后 |
|------|----------------|
| `NoopPayOutboxPort.publishPendingForOrder` 空实现 | 真正发 `ORDER_PAID` 到 Topic |
| `afterCommit` 里直接 HTTP 关单/退款 | 可改为发「补偿命令」消息，失败可重试 |
| 回调线程做完所有事 | 回调尽快 200；下游异步消化 |

**铁律不变：先短事务改库（+ Outbox 行），再发消息；消费端必须幂等。**

---

## 1. 先记 RocketMQ 最小概念（对照支付）

| 概念 | 含义 | 映射到本项目 |
|------|------|----------------|
| **Producer** | 生产者，发消息 | 支付中心：`publishPendingForOrder` / Outbox 扫描器 |
| **Consumer** | 消费者，收消息 | 例如「厨房通知服务」、关单补偿消费者（可先写在 admin 同进程） |
| **Topic** | 消息主题 | 如 `takeout-order-paid`、`takeout-pay-compensate` |
| **Tag** | 主题下子类型 | 如 `ORDER_PAID`、`CLOSE_CHANNEL`、`REFUND` |
| **Message Key** | 业务键 | 建议 `orderId` 或 `outTradeNo`，便于排查 |
| **Consumer Group** | 一组竞争消费 | 同 Group 内一条消息只被一个实例处理 |
| **Broker** | 消息代理/消息中间人，存消息、投递 | Docker 起 NameServer + Broker |

面试句：**Topic 分类业务，Group 决定谁来消费、能否水平扩展。**

---

## 2. 本项目建议练的功能点（按优先级）

下面每一项都对应支付链路里的真实痛点，而不是空练 Hello World。

### P0 — 必练（支付 Outbox 主路径）

> **手抄完整教程（Docker / Liquibase / Outbox 实现 / Producer·Consumer / 验收实验）：**  
> [`2026-08-13-rocketmq-p0-outbox-handson.md`](./2026-08-13-rocketmq-p0-outbox-handson.md)

#### F1. 普通消息：订单已支付事件

**业务：** CAS 成功后，通知下游「可以备餐 / 打日志」。  

**现网挂点：**

```text
短事务内：payOutboxPort.insertOrderPaid(orderId, orderNumber)
afterCommit：payOutboxPort.publishPendingForOrder(orderId)
```

**练什么：**

1. 建表 `pay_outbox`（或最小字段）与 Liquibase 增量  
2. `insertOrderPaid`：同事务写入 `NEW`  
3. `publishPendingForOrder`：查待发送 → Producer 发 RocketMQ → 标 `SENT`  
4. Consumer 订阅 `takeout-order-paid`，打印 / 模拟推厨房  

**验收：**

- 付成功后 Broker 上能看到消息  
- 消费者日志出现 `orderId=...`  
- 故意在事务里抛错：不应发出消息（Outbox 行也回滚）

#### F2. 消费幂等

**业务：** MQ 至少一次投递，同一 `ORDER_PAID` 可能消费两次。  

**练什么：**

- 消费前用 Redis / DB 唯一键：`consume:order-paid:{orderId}`  
- 已处理直接 ack，不再推厨房  

**验收：** 手动重投同一条，下游副作用只发生一次。

#### F3. 发送时机：严禁事务内裸发 MQ

**业务：** 你们已经用 `TransactionSynchronization.afterCommit`，要保持。  

**练什么：**

| 错误 | 正确 |
|------|------|
| `@Transactional` 方法里直接 `producer.send` | 事务内只写 Outbox；`afterCommit` 或定时扫 Outbox 再 send |
| 发送失败就回滚入账 | 入账已提交则保留 Outbox=`NEW`，靠重试发送 |

**验收：** 停掉 Broker 时入账仍成功，Outbox 留 `NEW`；Broker 恢复后扫描补发。

---

### P1 — 强烈建议（和现有 afterCommit 副作用对齐）

> **生产向说明（失败可恢复 / 补偿消息化 close·refund，含为什么与落地）：**  
> [`2026-08-14-rocketmq-p1-retry-compensate-handson.md`](./2026-08-14-rocketmq-p1-retry-compensate-handson.md)

#### F4. 失败重试 + 死信 / 可观测

**业务：** `close` / `refund` / 推厨房失败不能丢。  

**练什么：**

- Consumer 抛异常 → RocketMQ 重试（注意间隔与次数）  
- 超过次数进死信 Topic 或本地「失败表」+ 告警日志  
- 与现网 `log.warn(...留待补偿)` 对齐：消息化后要有地方看见失败

**验收：** 消费者前 2 次故意失败，第 3 次成功；或进入死信可查。

#### F5. 用消息承载「关单 / 退款」补偿（可选改造）

**现网：** `afterCommit` 里直接 `mockWechatHttpClient.close/refund`。  

**进阶练法：**

```text
afterCommit
  → 发 Tag=CLOSE_OTHERS / REFUND 的补偿消息（body 含 orderId、outTradeNo）
  → CompensateConsumer 调假微信 HTTP
```

**好处：** 回调线程更瘦；HTTP 失败由 MQ 重试。  
**注意：** 关单/退款本身也要幂等（假微信已支持 CLOSED/REFUND 幂等）。

本期教学可仍保留 afterCommit 直调 HTTP，另开 Topic 只练 `ORDER_PAID`；P1 再迁补偿。

---

### P2 — 扩展（订单域，不挡支付主线）

#### F6. 延时消息：待付款超时关单

**业务：** 下单后 15 分钟未支付 → 关业务单 / 关渠道单。  

**练什么：** RocketMQ 延时 / 定时消息（注意开源版延时级别是阶梯，不是任意秒）。  

**挂点：** `OrderService` 下单成功后发延时消息；消费者检查仍是待付款再取消。  

**验收：** 测试用 10s 延时，到期未付则订单取消。

#### F7. 顺序消息（订单状态流转，慎用）

**业务：** 同一订单的「已支付 → 接单 → 派送 → 完成」等**多条状态事件**希望按发送序消费。  

**说明：** 支付入账本身靠 DB 状态机，**不必**为单条 `ORDER_PAID` 强上顺序消息。  

> **生产向说明（分片键 / 有序发送 / ORDERLY 消费 / 空业务挂点）：**  
> [`2026-08-14-rocketmq-orderly-order-status.md`](./2026-08-14-rocketmq-orderly-order-status.md)

#### F8. 事务消息（RocketMQ 特色，选修）

**业务：** 半消息 + 本地事务 + 回查，和 Outbox 是两条路。  

**教学建议：** 先掌握 **本地消息表 Outbox（你们已占位）**，再对比事务消息；本项目主推 Outbox，不必两套都上生产。  
**展开对比与业界用法：** 见下文 [§9 事务消息 vs Outbox](#9-事务消息-vs-outbox发件可靠性)。

---

## 3. 推荐 Topic / Tag / 消息体（教学约定）

### 3.1 Topic

| Topic | 用途 |
|-------|------|
| `takeout-order-paid` | 订单已支付领域事件 |
| `takeout-pay-compensate` | 关单 / 退款等补偿命令（P1） |
| `takeout-order-delay` | 待付款超时（P2） |

### 3.2 Tag

| Tag | 含义 |
|-----|------|
| `ORDER_PAID` | 入账成功 |
| `CLOSE_CHANNEL` | 关闭某 outTradeNo |
| `REFUND` | 对某 outTradeNo 退款 |
| `CANCEL_UNPAID` | 超时取消待付款 |

### 3.3 消息体示例（JSON）

```json
{
  "eventId": "outbox-row-id-or-uuid",
  "orderId": 1001,
  "orderNumber": "ORD20260813120001",
  "outTradeNo": "ORD20260813120001-A1723...",
  "occurredAt": "2026-08-13T12:00:00"
}
```

- `eventId`：消费幂等键（优先）  
- `orderId`：业务主键、Message Key  

---

## 4. 和现网调用链如何接（目标形态）

```text
假微信 notify
  → handlePayNotify（验签、nonce、支付锁）
  → markPaidOrRufundInShortTx  (@Transactional)
        ├─ CAS 订单已支付
        ├─ attempt → SUCCESS
        ├─ insertOrderPaid → pay_outbox(NEW)     ← F1
        └─ afterCommitAction = CLOSE_OTHERS | REFUND
  → afterCommit
        ├─ publishPendingForOrder → RocketMQ      ← F1/F3
        └─ （P0 可暂留）close/refund HTTP
           （P1）改为发 compensate 消息            ← F5
  → Consumer(ORDER_PAID) 幂等处理                 ← F2/F4
```

**不要**在验签前、CAS 前发「已支付」消息。

---

## 5. 学习里程碑（建议按周）

### Milestone 1：能发能收（1～2 天）

- [ ] Docker 起 NameServer + Broker（单机）  
- [ ] `take-out-admin` 引入 RocketMQ Spring 依赖（版本对齐 Boot 4.1 需查兼容）  
- [ ] 写死一个「测试按钮 / 单测」发送 + 日志消费者  
- [ ] 读懂 Topic / Group / Tag  

### Milestone 2：挂上支付 Outbox（核心）

- [ ] Liquibase：`003-create-pay-outbox`  
- [ ] 实现真 `PayOutboxPort`（替换或并列 Noop，用 `@Primary` / 去掉 Noop）  
- [ ] `afterCommit` 真正 `publish`  
- [ ] 消费端模拟「厨房收到订单」  
- [ ] 断 Broker、重复消费两组实验  

### Milestone 3：可靠性

- [ ] 发送失败保留 NEW + 定时扫描补发  
- [ ] 消费失败重试 + 死信 / 失败表  
- [ ] （可选）compensate Topic 承接 close/refund  

### Milestone 4：订单延时（加分）

- [ ] 下单发延时消息  
- [ ] 到期取消未支付订单（注意与支付回调竞态：先付后取消要用状态 CAS）  

---

## 6. 本地环境建议（提纲）

```text
docker compose 增加：
  rocketmq-namesrv
  rocketmq-broker   （开发用单 Broker 即可）
可选：
  rocketmq-dashboard  （看 Topic / 堆积）
```

应用配置（示意）：

```yaml
rocketmq:
  name-server: 127.0.0.1:9876
  producer:
    group: takeout-pay-producer
  # consumer group 在 @RocketMQMessageListener 上声明
```

具体镜像 tag、Broker 的 `broker.conf`（`brokerIP1`、自动创建 Topic）落地实现时再写进 `docker-compose.yml` / `docker-command.md`。

---

## 7. 模块放哪里（建议）

| 内容 | 模块 |
|------|------|
| Producer、Outbox 实现 | `take-out-pay`（或 system 实现 Port） |
| `ORDER_PAID` 消费者（厨房模拟） | 可先放 `take-out-system` / `admin` 同进程；以后再拆服务 |
| MQ 配置 | `application.yml` + 可选 `take-out-framework` |

教学阶段**同进程多 Consumer** 完全够用；重点是语义，不是微服务拆分。

---

## 8. 和 Kafka / Rabbit 概念对照（防晕）

| 想法 | RocketMQ | 支付里怎么用 |
|------|----------|----------------|
| 发一条业务事件 | Topic + Tag | `ORDER_PAID` |
| 一组服务抢着消费 | Consumer Group | 厨房消费者组 |
| 至少一次 | 默认需幂等 | F2 |
| 延迟 | 延时消息 | 待付款超时 F6 |
| 事务型出站 | 事务消息 **或** Outbox | **本项目主推 Outbox**（详见 §9） |

---

## 9. 事务消息 vs Outbox（发件可靠性）

两者都在解决：**本地改库和发消息如何尽量一致**（避免「库成了消息没发」或「消息发了库回滚」）。做法不同。

### 9.1 一句话

| | **Outbox（本地消息表）** | **RocketMQ 事务消息** |
|--|--------------------------|------------------------|
| 核心 | 业务数据 + 待发消息**同一 DB 事务**写入；提交后再发 MQ | MQ 先收「半消息」，再执行本地事务，Broker **回查**本地是否成功 |
| 谁当协调者 | 你的数据库 | RocketMQ Broker + 回查接口 |

### 9.2 Outbox（本项目主推，`PayOutboxPort`）

```text
@Transactional
  CAS 入账
  insert pay_outbox(NEW)     ← 和订单同事务
提交成功
  → afterCommit / 定时任务：读 Outbox → send MQ → 标 SENT
```

- 入账回滚 → Outbox 行也没有 → **不会发**  
- 入账成功、send 失败 → 行还在 `NEW` → **可重试补发**  
- **不依赖** MQ 是否支持「事务消息」，换 Kafka/Rabbit 也能用  
- 代价：多一张表 + 发送器 / 扫描补发逻辑  

### 9.3 RocketMQ 事务消息

```text
Producer 发「半消息」到 Broker（此时消费者看不到）
  → 执行本地事务（CAS 入账）
  → 成功则 commit 半消息 / 失败则 rollback 半消息
若 Producer 超时没回报
  → Broker 回调「事务回查」：查订单到底付没付，再决定投递或丢弃
```

- 一致性主要由 **MQ 协议 + 回查** 保证  
- 可不建 Outbox 表，但仍要能回答回查（订单状态 / 本地流水）  
- **绑定 RocketMQ**；换别的 MQ 就没有同一套 API  
- 回查、超时、并发要自己实现正确  

### 9.4 支付场景对比

| 维度 | Outbox | 事务消息 |
|------|--------|----------|
| 「库成了、消息暂时没发出」 | 有，靠扫描补发 | 半消息未 commit 前消费端不可见 |
| 「消息可见了、库其实失败」 | 同事务写入，正常不会 | 靠 rollback / 回查避免 |
| 跨 MQ 可移植 | 好 | 差 |
| 运维 / 代码 | 表 + 发送器 | 监听器 + 回查 |
| 本仓库 | 已占位，主推 | F8 选修，只做对比 |

口诀：**Outbox = 消息先落自己的库再发；事务消息 = 先向 Broker 打半票，再以本地事务结果决定是否让消费者看见。**

### 9.5 业界一般用哪种？

若问「发件可靠性」几条路，业界**更常用 Outbox（本地消息表）**，尤其支付 / 订单要强一致出站时：

| 做法 | 业界大致情况 |
|------|----------------|
| **Outbox + 提交后发送 / 扫描补发** | 最通用：不绑特定 MQ；Kafka / Rabbit / Rocket 都能用；大厂、中小厂都常见 |
| **RocketMQ 事务消息** | 主要在 **已上 RocketMQ 的国内团队**里用；不是跨栈默认方案 |
| **事务里直接发 MQ，或只 afterCommit 裸发、无落库** | 简单系统有；出问题难补，严肃支付一般不靠它当唯一手段 |

补充：

- 很多人说的「最终一致性」落地 = **Outbox（或 CDC）+ 消费幂等 + 重试**，不一定上事务消息。  
- Kafka 生态更常见 **Outbox / Debezium CDC**，而不是 MQ 自带事务消息。  
- 消费侧「至少一次 + 幂等」几乎是标配，和发送选 Outbox 还是事务消息无关。  

**结论：业界默认更认 Outbox；事务消息是 RocketMQ 场景下的可选实现。本项目主推 Outbox，与主流一致；F8 仅作对比实验，不必与 Outbox 两套叠加上生产。**

---

## 10. 明确不做（防范围膨胀）

- 不上完整事件溯源 / CQRS  
- 不为每个 Redis 锁改 MQ  
- 不把验签、CAS 搬到消费者里  
- 第一期不必上集群多副本、不必上事务消息（了解即可，见 §9）  

---

## 11. 功能点总表（打印用）

| ID | 功能点 | 业务挂点 | 优先级 |
|----|--------|----------|--------|
| F1 | 普通消息 ORDER_PAID | Outbox + afterCommit publish | P0 |
| F2 | 消费幂等 | Consumer | P0 |
| F3 | 提交后再发 / Outbox 补发 | 短事务 + 扫描器 | P0 |
| F4 | 重试与死信 | Consumer | P1 |
| F5 | 关单/退款补偿消息化 | 替代部分 afterCommit HTTP | P1 |
| F6 | 延时关未支付订单 | Order 下单 | P2 |
| F7 | 顺序消息（了解） | 订单状态流 | P2 |
| F8 | 事务消息（对比 Outbox，见 §9） | 选修 | P2 |

---

## 12. 一句话

**用 RocketMQ 练的不是「会发 Hello」，而是：支付入账后如何可靠地通知下游——Outbox 写出站、afterCommit/扫描发送、消费幂等与失败重试；关单退款与超时关单是下一阶挂件。发件可靠性业界更认 Outbox，事务消息作 RocketMQ 选修对比即可。**

---

## 13. 下一步（你点头后再改代码）

1. Docker 增加 RocketMQ + 最小可运行 Producer/Consumer  
2. Liquibase 增加 `pay_outbox`  
3. 实现 `PayOutboxPort` 真发 `ORDER_PAID`  
4. 再考虑 F5 补偿消息化  

需要开工时从 **Milestone 1** 或直接 **Milestone 2** 说一声即可。  
P0 逐步手抄请跟：[`2026-08-13-rocketmq-p0-outbox-handson.md`](./2026-08-13-rocketmq-p0-outbox-handson.md)。  
P1（重试/死信 + 补偿消息）请跟：[`2026-08-14-rocketmq-p1-retry-compensate-handson.md`](./2026-08-14-rocketmq-p1-retry-compensate-handson.md)。
