# 厨房来单提醒（管理端 WebSocket）设计

日期：2026-08-13  
状态：已确认并已落地实现（2026-08-13）  
已确认：方案 1（简单 WebSocket 会话表）+ **推送成功后再标记幂等**  
产品形态：**A** — 顶栏弹窗 + 提示音 + 文案引导接单（复用黑马 Navbar，不做独立厨房看板）

---

## 1. 目标与非目标

### 目标

1. 用户支付入账成功后，经现有 Outbox → RocketMQ `ORDER_PAID`，由 `OrderPaidKitchenConsumer` 通知管理端。
2. 管理端已打开的页面通过 **WebSocket** 收到推送：弹窗 + 提示音，文案体现厨房视角，例如「厨房：您有一笔新的订单，请接单」。
3. 点击通知跳转订单待接单列表（沿用现有 Navbar 行为）。
4. 消费幂等贴生产：**副作用（推送）成功后再标记 eventId 已消费**；推送失败可让 MQ 重试。

### 非目标（一期）

- 独立厨房看板 / 出餐台
- WebSocket 多实例集群同步（Redis Pub/Sub）；学习环境默认单机 admin
- 催单 type=2（可预留字段，本期可不发）
- 改支付入账 / Outbox / Producer 主流程（只接 Consumer 尾巴）

---

## 2. 端到端链路

```text
CAS 入账 + insertOrderPaid(Outbox)
        ↓ afterCommit
publishPendingForOrder → OrderPaidProducer → Topic ORDER_PAID
        ↓
OrderPaidKitchenConsumer.onMessage
        ├─ 解析 OrderPaidMessage
        ├─ Redis GET 幂等：已是 SUCCESS → return（重复投递）
        ├─ KitchenNotifyService.notifyNewOrder(msg)  → 广播 WebSocket
        │         ↑ 失败抛错 → 不写 SUCCESS → MQ 重试
        └─ Redis SET 幂等 SUCCESS（TTL 建议 7 天）
        ↓
管理端 Navbar WebSocket onmessage
        → type=1 提示音 + $notify 弹窗 → 点击进 /order
```

---

## 3. 幂等策略（推送后再标记）

### 3.1 Redis Key

```text
mq:consume:order-paid:{eventId}  →  值 "SUCCESS"（或 "1"）
TTL: 7 天
```

### 3.2 顺序（必须）

```text
1. 解析失败 → log + return（毒消息，不重试占坑；生产可进死信）
2. 若 key 已存在且为成功态 → return（幂等短路）
3. 执行厨房推送（WebSocket broadcast）
4. 推送成功 → SET NX/SET key=SUCCESS + TTL
5. 步骤 3 抛异常 → 不写 key → RocketMQ 重试
```

### 3.3 与「先占坑」教学版的对比

| | 先 trySetNx 再推（现状注释） | 先推再标记（本期） |
|--|--|--|
| 推失败 | 已被占坑，重试直接跳过，厨房永远收不到 | 可重试，直到推成功或进死信 |
| 重复推 | 少 | 重试窗口内可能对前端推多次 |
| 前端 | — | 按 `orderId` / `eventId` 短时去重（建议） |

**重复弹窗可接受**：来单提醒是提示性的；前端可用 `sessionStorage` 记 `notified:{orderId}` 几分钟内忽略重复。

### 3.4 明确不做（一期）

两阶段 `PROCESSING` / `SUCCESS`（更严、稍复杂）。若以后推送里还有写库副作用，再升级两阶段。

---

## 4. WebSocket 设计（方案 1）

### 4.1 与前端对齐

现有前端（已存在，尽量少改）：

- URL：`VUE_APP_SOCKET_URL + clientId`  
  开发环境：`ws://localhost:8080/ws/{clientId}`
- 消息 JSON 期望字段（Navbar）：
  - `type`：`1` = 待接单/来单，`2` = 催单
  - `orderId`
  - `content`：展示文案

后端推送体建议：

```json
{
  "type": 1,
  "orderId": 123,
  "orderNumber": "ORD...",
  "content": "厨房：您有一笔新的订单（ORD...），请接单",
  "eventId": "uuid-..."
}
```

### 4.2 服务端组件（建议落在 system 或 admin）

| 类 | 职责 |
|----|------|
| `WebSocketConfig` | `@EnableWebSocket`，注册 handler，路径 `/ws/{clientId}` |
| `KitchenWebSocketHandler` | `afterConnectionEstablished` 登记 Session；`afterConnectionClosed` 移除；心跳可选 |
| `KitchenSessionHub` | `ConcurrentHashMap<String, Session>`（或按 clientId）；`broadcast(String json)` |
| `KitchenNotifyService` | 组装来单 JSON，调用 `sessionHub.broadcast`；无在线会话时 **打 warn 日志仍算成功**（或按产品选择：无会话也成功，避免无收银员时 MQ 死循环重试） |

**无在线客户端时的策略（已选默认）：**

- **广播 0 个 Session 仍视为推送成功并标记幂等**（店员未打开后台时，不应无限重试）。
- 店员之后打开页面靠订单列表「待接单」自行查看；本期不做离线消息盒子。

### 4.3 安全

- Spring Security 对 `/ws/**` **permitAll**（握手阶段带 Cookie/Token 在学习项目较麻烦；与黑马一致先放行）。
- 生产应：握手校验 JWT / 仅内网；本期文档注明即可。

在 `SecurityConstant.WHITE_LIST`（或等价配置）增加：`/ws/**`。

### 4.4 依赖

`take-out-admin` 或 `take-out-framework` 增加：

```xml
spring-boot-starter-websocket
```

（具体加在 admin 即可，因 WS 挂在 8080 进程。）

---

## 5. Consumer 改造要点

路径：`OrderPaidKitchenConsumer.java`

伪代码（手抄对照）：

```java
@Override
public void onMessage(String body) {
    OrderPaidMessage msg;
    try {
        msg = objectMapper.readValue(body, OrderPaidMessage.class);
    } catch (Exception e) {
        log.error("解析支付消息失败，body={}", body, e);
        return; // 毒消息：不重试
    }

    String idemKey = IDEMPOTENT_PREFIX + msg.getEventId();
    // 已成功消费过 → 直接跳过
    if (StringUtils.hasText(redis.get(idemKey))) {
        log.info("幂等短路，已消费 eventId={}", msg.getEventId());
        return;
    }

    // 厨房处理：推管理端
    kitchenNotifyService.notifyNewOrder(msg);

    // 推送成功（或无在线会话按约定仍成功）后再标记
    boolean ok = redis.trySetNx(idemKey, "SUCCESS", Duration.ofDays(7).getSeconds());
    if (!ok) {
        // 并发下另一个消费者已标记：可忽略
        log.info("幂等标记时发现已存在 eventId={}", msg.getEventId());
    }
}
```

注意：

- **删掉**「先 trySetNx 再处理」的旧顺序。
- `notifyNewOrder` 内部异常要抛出（或包装后抛），不要吞掉，否则无法触发 MQ 重试。
- 若 `RedisIdempotentHelper` 暂无 `get`，需确认已有 `get(key)`；没有则加，或用 `StringRedisTemplate` 只在此处读。

---

## 6. 前端改动（最小）

文件：`project-rjwm-admin-vue-ts/src/layout/components/Navbar/index.vue`

1. 确认 `mounted`/`created` 会调 `webSocket()`（已有则不动）。
2. 文案：后端 `content` 已含「厨房：…请接单」时，可直接展示 `jsonMsg.content`；或保留模板「您有1个订单待处理」。
3. 可选去重：

```ts
const dedupeKey = 'kitchen_notified_' + jsonMsg.orderId
if (sessionStorage.getItem(dedupeKey)) return
sessionStorage.setItem(dedupeKey, '1')
```

4. `.env.development` 已是 `ws://localhost:8080/ws/`，与后端路径一致即可。

---

## 7. 改造清单

| 步骤 | 模块 | 内容 |
|------|------|------|
| 1 | admin pom | `spring-boot-starter-websocket` |
| 2 | framework/admin | Security 放行 `/ws/**` |
| 3 | system 或 admin | `KitchenSessionHub` + `KitchenWebSocketHandler` + `WebSocketConfig` |
| 4 | system | `KitchenNotifyService` |
| 5 | system | 改 `OrderPaidKitchenConsumer`：先推后标记 |
| 6 | pay redis（可选） | Helper 补 `get`（若还没有） |
| 7 | 前端 | Navbar 文案/去重微调 |

类放哪：

- **推荐**：WebSocket 配置与 Handler 放 `take-out-admin`（Web 入口）；`KitchenNotifyService` + Hub 放 `take-out-system`（供 Consumer 注入）。若 Handler 与 Hub 分模块，Hub 必须在 system，admin 的 Handler 调 Hub（admin 已依赖 system）。
- 更简单教学拆法：全部 WebSocket 相关先放 **admin**，Consumer 在 system 通过 **接口端口** `KitchenNotifyPort`（接口在 pay/common，实现 admin——会环）。  
  **故正确拆法：Hub + NotifyService + Handler/Config 都放 system，admin 扫到即可；或 Config/Handler 在 admin，Hub 在 system，admin Handler 注入 Hub。**

推荐落地：

```text
take-out-system
  mq/OrderPaidKitchenConsumer
  notify/KitchenNotifyService
  websocket/KitchenSessionHub
  websocket/KitchenWebSocketHandler
  websocket/WebSocketConfig   // 若 system 无 spring-websocket，则 Config+Handler 放 admin
```

`take-out-system` 目前无 websocket 依赖时：

- system 只放 `KitchenSessionHub` + `KitchenNotifyService`
- admin 加 websocket 依赖 + `WebSocketConfig` + `Handler`（注入 Hub）

---

## 8. 测试计划

1. 起 MySQL / Redis / RocketMQ / admin / mock-wechat；打开管理端并登录（触发 WS 连接，控制台见「浏览器WebSocket已打开」）。
2. 模拟下单 → 支付 → 确认页确认。
3. 管理端应弹窗 + 声音；文案含新订单/请接单。
4. 同一 `eventId` 人工再投一次 MQ：不应再弹（幂等短路）；或 Consumer 日志「幂等短路」。
5. 推送前停掉管理端页（无 Session）：Consumer 应仍标记成功（按 §4.2 约定），不疯狂重试。
6. 推送中故意让 `broadcast` 抛错（可临时测试）：不应写幂等，MQ 会重试；修复后能再次推到。

---

## 9. 一句话

**MQ 告诉厨房有新单；WebSocket 告诉浏览器弹窗；成功推完（或确认无需在线）再记幂等。**

---

## 10. 审阅检查

- [x] 方案锁定：简单 WS + 先推后标记  
- [x] 无会话策略写明（算成功，避免空重试）  
- [x] 与现有 Navbar / `VUE_APP_SOCKET_URL` 对齐  
- [x] 未改支付 CAS 主路径  
- [ ] 待你确认后进入实施（手写或让助手写代码）

请审阅本 spec。确认后回复「可以写代码」或「按文档我自己手写」。
