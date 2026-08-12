# 支付尝试：同一业务单仅一条「进行中」+ 成功关单 + 第二笔退款

日期：2026-08-11  
状态：待审阅（方案 A）  
范围：扩展现有假微信（关单/退款 API）+ 支付中心引入「支付单/支付尝试」；**不**新建假支付宝模块。

---

## 1. 目标与非目标

### 目标

在现有「回调验签 / nonce / 锁 / CAS 入账」之上，补齐「多开支付页也不双扣」的主路径：

1. **同一业务订单同一时刻只允许一条状态为 `PAYING` 的支付尝试（支付单）**
2. **业务单 CAS 入账成功后**：关闭本单其它未付的渠道单（假微信 `close`），本地尝试标 `CLOSED`
3. **若又收到另一笔渠道 SUCCESS**（关单失败 / 旧收银台晚付）：将该支付尝试走 **退款**，对渠道回成功，**不**再次改业务单已付状态

### 非目标

- 真实微信/支付宝 SDK、证书、分账
- 新建 `take-out-mock-alipay`
- 完整 Outbox 表（可继续用现有 `PayOutboxPort`；关单/退款可先 afterCommit 直调 + 失败记日志/重试表最小版）
- 改管理端 UI 交互大改（可选：支付返回带 `outTradeNo` / 确认页 URL）

### 成功标准

| 场景 | 期望 |
|------|------|
| 连点 / 并发 `requestPay` | 只能有一条 `PAYING`；其它被拒或复用同一条 |
| 正常付一笔 | 业务单待接单+已付；该尝试 `SUCCESS`；渠道未付单被 close |
| 旧页晚付成功（第二笔 SUCCESS） | 业务单仍只付一次；第二笔尝试 `REFUNDED`；假微信有退款记录 |
| 重复同一笔 notify | 仍幂等（nonce + 已付/已退） |

---

## 2. 核心概念

| 概念 | 说明 |
|------|------|
| **业务单 Order** | `orders` 表，用户看到的外卖单；`number` 如 `ORD...` |
| **支付尝试 PayAttempt** | 每次「去支付」产生的一条记录；有自己的 **`out_trade_no`**（渠道商户单号） |
| **渠道单 Trade** | 假微信内存里的交易，key = `out_trade_no` |

**关键变更（相对现状）：**

```text
现状：out_trade_no === orders.number（一单一号，无法表达多次尝试）
目标：out_trade_no === 支付尝试号（如 ORDxxx-A1001），经 PayAttempt 反查业务单
```

回调里现在的字段名仍可叫 `orderNumber`（兼容假微信 payload），**语义改为「渠道 out_trade_no」**，商户用它查 `pay_attempt`，再拿 `order_id`。

---

## 3. 状态机

### 3.1 PayAttemptStatus

```text
        requestPay 成功占坑
              │
              ▼
           PAYING ──────── requestPay 换渠道/重开前 close ──► CLOSED
              │
              │ 回调 SUCCESS 且 CAS 入账成功
              ▼
           SUCCESS
              │
              │ （罕见）同 attempt 重复通知
              └── 幂等返回

        另一条 attempt 在业务单已付后又 SUCCESS
              │
              ▼
         REFUNDING ──► REFUNDED
```

约束：**同一 `order_id` 最多一条 `PAYING`**（DB 唯一技巧 + 应用校验）。

### 3.2 假微信 TradeState（扩展）

```text
NOTPAY → SUCCESS（confirm）
NOTPAY → CLOSED（close）
SUCCESS → REFUND（refund，教学简化：直接标已退）
CLOSED 不可再 confirm（或 confirm 返回失败）
```

---

## 4. 端到端流程

### 4.1 发起支付（改造后）

```text
PUT /admin/order/mockPay/{orderId}
  → MockPaymentGateway.requestPay
       1. 查业务单：须待付款+未支付
       2. request 短锁（现有）
       3. 若已有 PAYING 尝试：
            - 策略「复用」：直接返回该 out_trade_no / 确认页 URL（推荐，防连点开多页）
            - 或策略「拒绝」：429
       4. 若无 PAYING：INSERT PayAttempt(PAYING) + 唯一约束占坑
       5. MockWechatHttpClient.createNativePay(outTradeNo=attempt.outTradeNo, ...)
       6. 更新 prepay_id；返回业务单（可附带 checkoutUrl）
```

### 4.2 回调入账（改造后）

```text
POST /admin/order/mockPay/notify  (orderNumber 字段 = out_trade_no)
  → handlePayNotify
       验签 / 时间窗 / nonce（不变）
       按 out_trade_no 查 PayAttempt（查不到 → 冲突）
       按 attempt.orderId 加 order:pay:lock
       → markPaidInShortTx：
            A) 业务单未付 + 本 attempt 为 PAYING
                 → CAS 业务单已付
                 → attempt = SUCCESS
                 → 同事务写 Outbox（可选）
                 → afterCommit：closeOtherChannelTrades(orderId, excludeOutTradeNo)
            B) 业务单已付 + 本 attempt 已是 SUCCESS
                 → 幂等成功
            C) 业务单已付 + 本 attempt 仍是 PAYING/其它未成功
                 → 重复支付：attempt → REFUNDING，afterCommit 调渠道 refund → REFUNDED
                 → HTTP 仍对渠道返回成功（避免疯狂重试）
```

### 4.3 「第二笔成功」如何在学习环境复现

在「严格一条 PAYING」下，双扣通常来自：

1. 本地把旧尝试标 `CLOSED`，但 **渠道 close 失败/未调**  
2. 又开了新 `PAYING`  
3. 用户在 **旧确认页** 仍点支付 → 旧 `out_trade_no` SUCCESS 回调  

教学可加假微信接口：`POST /mock/pay/force-success-without-close-guard` 不必；按上面 1–3 用「跳过 close」开关或故意 close 失败即可演示退款路径。

---

## 5. 数据模型

### 5.1 DDL（MySQL）

```sql
-- 支付尝试表：一次「去支付」一行
CREATE TABLE IF NOT EXISTS pay_attempt (
  id            BIGINT       NOT NULL PRIMARY KEY AUTO_INCREMENT,
  order_id      BIGINT       NOT NULL COMMENT '业务单 orders.id',
  order_number  VARCHAR(64)  NOT NULL COMMENT '业务单号 ORD...',
  out_trade_no  VARCHAR(64)  NOT NULL COMMENT '渠道商户单号，回调定位用',
  channel       VARCHAR(32)  NOT NULL DEFAULT 'WECHAT' COMMENT '教学期仅 WECHAT',
  status        VARCHAR(32)  NOT NULL COMMENT 'PAYING/SUCCESS/CLOSED/REFUNDING/REFUNDED',
  amount        DECIMAL(10,2) NOT NULL,
  prepay_id     VARCHAR(128) NULL,
  -- 技巧：仅 PAYING 时为 1，其它为 NULL；MySQL UNIQUE 允许多个 NULL
  -- → 同一 order_id 最多一条 paying_flag=1
  paying_flag   TINYINT      NULL COMMENT '1=进行中，否则 NULL',
  created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_out_trade_no (out_trade_no),
  UNIQUE KEY uk_order_paying (order_id, paying_flag),
  KEY idx_order_id (order_id)
) COMMENT='支付尝试/支付单';
```

### 5.2 枚举

库字段 `pay_attempt.status` 为 **VARCHAR**，存字符串 code。实体字段类型用枚举，靠 `@EnumValue` 映射（与 `PayStatus` 同模式，只是 code 是 String 不是 Integer）。

```java
package com.sky.takeout.pojo.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

@Getter
public enum PayAttemptStatus {
    PAYING("PAYING", "支付中"),
    SUCCESS("SUCCESS", "支付成功"),
    CLOSED("CLOSED", "已关闭"),
    REFUNDING("REFUNDING", "退款中"),
    REFUNDED("REFUNDED", "已退款");

    @EnumValue
    @JsonValue
    private final String code;
    private final String message;

    PayAttemptStatus(String code, String message) {
        this.code = code;
        this.message = message;
    }

    @JsonCreator
    public static PayAttemptStatus fromCode(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        for (PayAttemptStatus status : values()) {
            if (status.code.equals(code)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Invalid pay attempt status code: " + code);
    }
}
```

实体：`private PayAttemptStatus status;` → `getStatus()` 返回枚举，比较用 `== PayAttemptStatus.PAYING`。

---

## 6. 假微信 API 扩展

端口仍 `9090`。在现有 native / query / confirm 上增加：

### 6.1 关单

`POST /v3/pay/transactions/out-trade-no/{out_trade_no}/close`

- `NOTPAY` → `CLOSED`，返回 OK  
- 已 `SUCCESS` → 409（不能关已付）  
- 已 `CLOSED` → 幂等 OK  
- 不存在 → 404  

### 6.2 退款（教学简化）

`POST /v3/pay/transactions/out-trade-no/{out_trade_no}/refund`

```json
{ "reason": "duplicate_pay" }
```

- 仅 `SUCCESS` 可退 → `REFUND`  
- 已 `REFUND` → 幂等 OK  
- `NOTPAY`/`CLOSED` → 409  

### 6.3 confirm 规则微调

- 状态为 `CLOSED` 时 confirm → 400/409（已关闭不可付）  
- 这样「成功关单」后旧页再点确认会失败（理想路径）；关单失败才会走到商户退款兜底  

---

## 7. 改造清单（按模块）

| 步骤 | 模块 | 做什么 |
|------|------|--------|
| 1 | pojo | `PayAttempt` 实体、`PayAttemptStatus`；通知 DTO 注释改为 out_trade_no 语义 |
| 2 | sql | 建 `pay_attempt` 表 |
| 3 | system | `PayAttemptMapper` + `PayAttemptPort` 实现（或放 pay 模块用 Mapper，按你们分层：建议 **port 在 pay，实现在 system**） |
| 4 | mock-wechat | `TradeState` 加 `CLOSED`/`REFUND`；`TradeService.close/refund`；Controller |
| 5 | pay client | `MockWechatHttpClient`：`createNativePay` 改吃 `outTradeNo`；加 `close`/`refund` |
| 6 | pay gateway | `requestPay` 占坑/复用；`handlePayNotify` 按 attempt 路由；Tx 内 SUCCESS/退款分支；afterCommit 关单 |
| 7 | 前端（可选） | 确认页 URL 使用返回的 `outTradeNo`（不再默认用 `order.number`） |

---

## 8. 完整对照代码（带详细注释）

> 下列为**设计稿**，可手抄改造；类名/包名与现仓对齐。  
> 未改动的验签、nonce、request 锁逻辑保持你们现有实现即可。

---

### 8.1 实体与端口

```java
package com.sky.takeout.pojo.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.sky.takeout.pojo.enums.PayAttemptStatus;

import lombok.Data;

/**
 * 支付尝试（支付单）：业务订单与渠道 out_trade_no 之间的桥梁。
 * <p>
 * 一个 Order 可以有多条历史尝试，但同一时刻最多一条 PAYING。
 */
@Data
@TableName("pay_attempt")
public class PayAttempt {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 业务单 id */
    private Long orderId;

    /** 业务单号 ORD...（展示/排查用） */
    private String orderNumber;

    /** 渠道商户单号：回调、关单、退款都用它 */
    private String outTradeNo;

    /** 教学期固定 WECHAT */
    private String channel;

    private PayAttemptStatus status;

    private BigDecimal amount;

    private String prepayId;

    /**
     * 仅 PAYING 时为 1，其它状态必须为 null。
     * 配合 uk_order_paying(order_id, paying_flag) 保证「一条进行中」。
     */
    private Integer payingFlag;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
```

```java
package com.sky.takeout.pay.port;

import java.util.List;

import com.sky.takeout.pojo.entity.PayAttempt;
import com.sky.takeout.pojo.enums.PayAttemptStatus;

/**
 * 支付尝试仓储端口：pay 模块不直接依赖 Mapper。
 */
public interface PayAttemptPort {

    PayAttempt findByOutTradeNo(String outTradeNo);

    PayAttempt findPayingByOrderId(Long orderId);

    List<PayAttempt> listByOrderId(Long orderId);

    /** 插入 PAYING；若唯一约束冲突，返回 false 或抛业务异常 */
    int insertPaying(PayAttempt attempt);

    int updateStatus(Long id, PayAttemptStatus from, PayAttemptStatus to, Integer payingFlag);

    int updatePrepayId(Long id, String prepayId);
}
```

---

### 8.2 假微信：状态 + 关单 + 退款

```java
package com.sky.takeout.mockwechat.domain;

public enum TradeState {
    NOTPAY,
    SUCCESS,
    CLOSED,
    REFUND
}
```

```java
// ========== TradeService 增量（摘录，保留原 createNative/confirm）==========

/**
 * 关单：未支付才能关。成功后旧确认页再 confirm 应失败。
 */
public void close(String outTradeNo) {
    Trade trade = tradeStore.findByOutTradeNo(outTradeNo)
            .orElseThrow(() -> new MockWechatException(HttpStatus.NOT_FOUND, "ORDER_NOT_FOUND", "not found"));

    if (trade.getTradeState() == TradeState.CLOSED) {
        return; // 幂等
    }
    if (trade.getTradeState() == TradeState.SUCCESS || trade.getTradeState() == TradeState.REFUND) {
        throw new MockWechatException(HttpStatus.CONFLICT, "ORDER_PAID", "cannot close paid/refunded trade");
    }
    // NOTPAY → CLOSED
    trade.setTradeState(TradeState.CLOSED);
    tradeStore.save(trade);
}

/**
 * 退款：仅已成功支付可退。教学版不拆金额，整单退。
 */
public void refund(String outTradeNo, String reason) {
    Trade trade = tradeStore.findByOutTradeNo(outTradeNo)
            .orElseThrow(() -> new MockWechatException(HttpStatus.NOT_FOUND, "ORDER_NOT_FOUND", "not found"));

    if (trade.getTradeState() == TradeState.REFUND) {
        return; // 幂等
    }
    if (trade.getTradeState() != TradeState.SUCCESS) {
        throw new MockWechatException(HttpStatus.CONFLICT, "NOT_SUCCESS", "only SUCCESS can refund");
    }
    trade.setTradeState(TradeState.REFUND);
    // 可把 reason 记到扩展字段；教学可打日志
    tradeStore.save(trade);
}

/**
 * confirm 开头增加：已关闭不可付
 */
// if (trade.getTradeState() == TradeState.CLOSED) {
//     throw new MockWechatException(HttpStatus.CONFLICT, "ORDER_CLOSED", "trade closed");
// }
```

```java
// TransactionController 增量
@PostMapping("/out-trade-no/{outTradeNo}/close")
public void close(@PathVariable String outTradeNo) {
    tradeService.close(outTradeNo);
}

@PostMapping("/out-trade-no/{outTradeNo}/refund")
public void refund(@PathVariable String outTradeNo, @RequestBody(required = false) Map<String, String> body) {
    String reason = body == null ? "duplicate_pay" : body.getOrDefault("reason", "duplicate_pay");
    tradeService.refund(outTradeNo, reason);
}
```

---

### 8.3 HTTP Client 改造

```java
package com.sky.takeout.pay.client;

// ... imports ...

@Component
public class MockWechatHttpClient {

    // ... 构造与 restClient 同现有 ...

    /**
     * 统一下单：必须使用「支付尝试」的 outTradeNo，禁止再传 orders.number 当渠道单号。
     */
    public TransactionResponse createNativePay(String outTradeNo, BigDecimal amount, String description) {
        if (!StringUtils.hasText(outTradeNo)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "outTradeNo 不能为空");
        }
        if (amount == null || amount.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "金额非法");
        }
        if (!StringUtils.hasText(payProperties.getMerchantNotifyUrl())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "notifyUrl 未配置");
        }

        NativePayRequest body = NativePayRequest.builder()
                .outTradeNo(outTradeNo)
                .description(description)
                .notifyUrl(payProperties.getMerchantNotifyUrl())
                .amount(amount)
                .build();

        try {
            TransactionResponse response = restClient.post()
                    .uri("/v3/pay/transactions/native")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(TransactionResponse.class);

            if (response == null || !StringUtils.hasText(response.getPrepayId())) {
                throw new BusinessException(ErrorCode.ERROR, "响应缺失 prepay_id");
            }
            return response;
        } catch (RestClientResponseException e) {
            throw new BusinessException(ErrorCode.ERROR, "native 失败 HTTP " + e.getStatusCode().value());
        }
    }

    /** 兼容旧签名：内部改为用 attempt 字段调用新方法 */
    public TransactionResponse createNativePay(Order order, String outTradeNo) {
        return createNativePay(outTradeNo, order.getAmount(), "外卖订单-" + order.getNumber());
    }

    /** 关单：失败由调用方决定是否重试（afterCommit 里建议吞异常打日志 + 补偿） */
    public void close(String outTradeNo) {
        try {
            restClient.post()
                    .uri("/v3/pay/transactions/out-trade-no/{outTradeNo}/close", outTradeNo)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException e) {
            throw new BusinessException(ErrorCode.ERROR, "关单失败 HTTP " + e.getStatusCode().value());
        }
    }

    /** 退款 */
    public void refund(String outTradeNo, String reason) {
        try {
            restClient.post()
                    .uri("/v3/pay/transactions/out-trade-no/{outTradeNo}/refund", outTradeNo)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("reason", reason == null ? "duplicate_pay" : reason))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException e) {
            throw new BusinessException(ErrorCode.ERROR, "退款失败 HTTP " + e.getStatusCode().value());
        }
    }
}
```

---

### 8.4 requestPay：一条进行中 + 复用

```java
/**
 * 用户点支付：不再用 orders.number 当下单号。
 * <p>
 * 策略：若已有 PAYING → 复用（同一确认页）；否则新建 attempt 再调 native。
 * 这样「开三个页连点」在中心侧会收敛到同一 out_trade_no（仍建议前端别乱开）。
 */
public Order requestPay(Long orderId) {
    if (orderId == null) {
        throw new BusinessException(ErrorCode.BAD_REQUEST, "订单id不能为空");
    }

    Order order = requireOrderById(orderId);
    if (isPaid(order)) {
        return order;
    }
    validateOrder(order);

    String lockKey = REQUEST_LOCK_PREFIX + orderId;
    String token = redisIdempotentHelper.tryLock(lockKey, resolvePayLockTtl());
    if (token == null) {
        throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS, "支付发起中，请勿重复点击");
    }

    try {
        order = requireOrderById(orderId);
        if (isPaid(order)) {
            return order;
        }
        validateOrder(order);

        // ---------- 同一业务单仅一条进行中 ----------
        PayAttempt paying = payAttemptPort.findPayingByOrderId(orderId);
        if (paying != null) {
            // 复用：可再次调 native（假微信对同 out_trade_no NOTPAY 幂等返回）
            TransactionResponse resp = mockWechatHttpClient.createNativePay(order, paying.getOutTradeNo());
            payAttemptPort.updatePrepayId(paying.getId(), resp.getPrepayId());
            log.info("复用进行中支付单 orderId={} outTradeNo={}", orderId, paying.getOutTradeNo());
            return order;
        }

        // 新建支付尝试：out_trade_no 必须全局唯一
        String outTradeNo = order.getNumber() + "-A" + System.currentTimeMillis();
        PayAttempt created = new PayAttempt();
        created.setOrderId(orderId);
        created.setOrderNumber(order.getNumber());
        created.setOutTradeNo(outTradeNo);
        created.setChannel("WECHAT");
        created.setStatus(PayAttemptStatus.PAYING);
        created.setAmount(order.getAmount());
        created.setPayingFlag(1); // 占「进行中」坑

        try {
            payAttemptPort.insertPaying(created);
        } catch (DuplicateKeyException e) {
            // 并发下唯一约束：别人先插入了 PAYING → 当成复用
            PayAttempt raced = payAttemptPort.findPayingByOrderId(orderId);
            if (raced == null) {
                throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS, "支付发起冲突，请重试");
            }
            mockWechatHttpClient.createNativePay(order, raced.getOutTradeNo());
            return order;
        }

        TransactionResponse response = mockWechatHttpClient.createNativePay(order, outTradeNo);
        payAttemptPort.updatePrepayId(created.getId(), response.getPrepayId());
        log.info("新建支付单 orderId={} outTradeNo={} prepayId={}", orderId, outTradeNo, response.getPrepayId());
        return order;
    } finally {
        redisIdempotentHelper.unlock(lockKey, token);
    }
}
```

> **换渠道 / 用户主动取消**：先 `close(paying.outTradeNo)` + 本地 `PAYING→CLOSED` 且 `paying_flag=null`，再允许新建。本期可只做「复用不换号」；换号作为第二阶段。

---

### 8.5 回调：入账 or 退款

```java
/**
 * 回调外壳：验签/nonce/锁后进入短事务。
 * dto.orderNumber 语义 = 渠道 out_trade_no（与假微信 payload 字段名保持兼容）。
 */
public Order handlePayNotify(MockPayNotifyDTO dto) {
    if (dto == null) {
        throw new BusinessException(ErrorCode.BAD_REQUEST, "参数不能为空");
    }
    verifySignAndTimeWindow(dto); // 签名字符串仍用 dto.getOrderNumber() 作为 out_trade_no

    Long nonceTtl = payProperties.getNonceTtlSeconds() == null ? 600L : payProperties.getNonceTtlSeconds();
    String nonceKey = NONCE_KEY_PREFIX + dto.getNonce();
    boolean firstNonce = redisIdempotentHelper.trySetNx(nonceKey, dto.getOrderNumber(), nonceTtl);
    if (!firstNonce) {
        PayAttempt existedAttempt = payAttemptPort.findByOutTradeNo(dto.getOrderNumber());
        if (existedAttempt == null) {
            throw new BusinessException(ErrorCode.CONFLICT, "支付单不存在");
        }
        Order existed = orderPayPort.findOrderById(existedAttempt.getOrderId());
        if (existed == null) {
            throw new BusinessException(ErrorCode.CONFLICT, "订单不存在");
        }
        // 已付或本尝试已退款完成 → 对渠道成功；否则让渠道重试
        if (isPaid(existed) || existedAttempt.getStatus() == PayAttemptStatus.REFUNDED
                || existedAttempt.getStatus() == PayAttemptStatus.SUCCESS) {
            return existed;
        }
        throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS, "支付处理中，请稍后重试");
    }

    PayAttempt attempt = payAttemptPort.findByOutTradeNo(dto.getOrderNumber());
    if (attempt == null) {
        redisIdempotentHelper.delete(nonceKey);
        throw new BusinessException(ErrorCode.CONFLICT, "支付单不存在");
    }

    String lockKey = PAY_LOCK_PREFIX + attempt.getOrderId();
    String lockToken = redisIdempotentHelper.tryLock(lockKey, resolvePayLockTtl());
    if (lockToken == null) {
        throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS, "支付处理中，请稍后重试");
    }

    try {
        return payNotifyTxService.markPaidOrRefundInShortTx(dto, attempt.getId(), lockKey, lockToken);
    } catch (RuntimeException e) {
        throw e;
    }
}
```

```java
package com.sky.takeout.pay.gateway;

/**
 * 短事务：要么 CAS 入账并标记本 attempt SUCCESS，要么识别重复支付标记 REFUNDING。
 * 渠道 close/refund 一律 afterCommit，避免事务内 HTTP。
 */
@Component
public class PayNotifyTxService {

    private final OrderPayPort orderPayPort;
    private final PayAttemptPort payAttemptPort;
    private final PayOutboxPort payOutboxPort;
    private final RedisIdempotentHelper redisIdempotentHelper;
    private final MockWechatHttpClient mockWechatHttpClient;

    // 构造器注入略...

    @Transactional(rollbackFor = Exception.class)
    public Order markPaidOrRefundInShortTx(MockPayNotifyDTO dto, Long attemptId,
                                           String lockKey, String lockToken) {
        final boolean[] unlockRegistered = {false};
        // afterCommit 动作：CLOSE_OTHERS / REFUND
        final String[] afterCommitAction = {null};
        final Long[] orderIdHolder = {null};
        final String[] outTradeNoHolder = {null};

        try {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    String action = afterCommitAction[0];
                    Long orderId = orderIdHolder[0];
                    String outTradeNo = outTradeNoHolder[0];
                    if (action == null || orderId == null || outTradeNo == null) {
                        return;
                    }
                    if ("CLOSE_OTHERS".equals(action)) {
                        closeOtherUnpaidAttempts(orderId, outTradeNo);
                        try {
                            payOutboxPort.publishPendingForOrder(orderId);
                        } catch (Exception e) {
                            log.warn("Outbox 投递失败 orderId={}", orderId, e);
                        }
                    } else if ("REFUND".equals(action)) {
                        try {
                            mockWechatHttpClient.refund(outTradeNo, "duplicate_pay");
                            // 退款成功后再把本地标 REFUNDED（可用独立小事务/服务方法）
                            payAttemptPort.updateStatus(attemptId,
                                    PayAttemptStatus.REFUNDING, PayAttemptStatus.REFUNDED, null);
                        } catch (Exception e) {
                            log.warn("重复支付退款失败，留待补偿 outTradeNo={}", outTradeNo, e);
                        }
                    }
                }

                @Override
                public void afterCompletion(int status) {
                    redisIdempotentHelper.unlock(lockKey, lockToken);
                }
            });
            unlockRegistered[0] = true;

            PayAttempt attempt = payAttemptPort.findByOutTradeNo(dto.getOrderNumber());
            if (attempt == null || !attempt.getId().equals(attemptId)) {
                throw new BusinessException(ErrorCode.CONFLICT, "支付单不存在");
            }

            Order order = orderPayPort.findOrderById(attempt.getOrderId());
            if (order == null) {
                throw new BusinessException(ErrorCode.CONFLICT, "订单不存在");
            }
            if (order.getAmount() == null || order.getAmount().compareTo(dto.getAmount()) != 0) {
                throw new BusinessException(ErrorCode.CONFLICT, "支付金额与订单不一致");
            }
            if (attempt.getAmount() != null && attempt.getAmount().compareTo(dto.getAmount()) != 0) {
                throw new BusinessException(ErrorCode.CONFLICT, "支付金额与支付单不一致");
            }

            // ----- 分支 B：本尝试已成功 -----
            if (attempt.getStatus() == PayAttemptStatus.SUCCESS) {
                return order;
            }
            // ----- 分支：已在退 -----
            if (attempt.getStatus() == PayAttemptStatus.REFUNDED
                    || attempt.getStatus() == PayAttemptStatus.REFUNDING) {
                return order;
            }

            // ----- 分支 C：业务单已付，但这是另一笔尝试的 SUCCESS → 重复支付退款 -----
            if (isPaid(order)) {
                payAttemptPort.updateStatus(attempt.getId(),
                        attempt.getStatus(), PayAttemptStatus.REFUNDING, null);
                afterCommitAction[0] = "REFUND";
                orderIdHolder[0] = order.getId();
                outTradeNoHolder[0] = attempt.getOutTradeNo();
                log.warn("检测到重复支付，将退款 orderId={} outTradeNo={}", order.getId(), attempt.getOutTradeNo());
                return order;
            }

            // ----- 分支 A：正常入账 -----
            if (order.getStatus() != OrderStatus.PENDING_PAYMENT
                    || order.getPayStatus() != PayStatus.UNPAID) {
                throw new BusinessException(ErrorCode.CONFLICT, "当前订单不可支付");
            }
            if (attempt.getStatus() != PayAttemptStatus.PAYING
                    && attempt.getStatus() != PayAttemptStatus.CLOSED) {
                // CLOSED 但仍收到 SUCCESS：说明关单失败/晚到，若订单未付仍可尝试入账
                // 教学简化：仅允许 PAYING；CLOSED+SUCCESS 走「若未付则入账，若已付则退款」
                throw new BusinessException(ErrorCode.CONFLICT, "支付单状态不可入账");
            }

            int rows = orderPayPort.casMarkPaid(order.getId());
            if (rows == 0) {
                Order latest = orderPayPort.findOrderById(order.getId());
                if (latest != null && isPaid(latest)) {
                    // 并发下别人先付成功 → 本笔变重复支付
                    payAttemptPort.updateStatus(attempt.getId(),
                            attempt.getStatus(), PayAttemptStatus.REFUNDING, null);
                    afterCommitAction[0] = "REFUND";
                    orderIdHolder[0] = order.getId();
                    outTradeNoHolder[0] = attempt.getOutTradeNo();
                    return latest;
                }
                throw new BusinessException(ErrorCode.CONFLICT, "订单状态已变更，支付失败");
            }

            // 本尝试 SUCCESS，释放 paying_flag
            payAttemptPort.updateStatus(attempt.getId(),
                    PayAttemptStatus.PAYING, PayAttemptStatus.SUCCESS, null);
            // 若从 CLOSED 晚到成功且 CAS 成功，按实际 from 状态更新（实现时允许 from IN (...)）

            payOutboxPort.insertOrderPaid(order.getId(), order.getNumber());

            afterCommitAction[0] = "CLOSE_OTHERS";
            orderIdHolder[0] = order.getId();
            outTradeNoHolder[0] = attempt.getOutTradeNo();
            return orderPayPort.findOrderById(order.getId());
        } finally {
            if (!unlockRegistered[0]) {
                redisIdempotentHelper.unlock(lockKey, lockToken);
            }
        }
    }

    /**
     * 入账成功后：关闭本业务单下其它未成功尝试对应的渠道单，并本地标 CLOSED。
     * <p>
     * 「一条进行中」时，这里主要关掉：曾 PAYING 后被标 CLOSED 但仍可能 NOTPAY 的历史号，
     * 以及异常残留。胜出的 outTradeNo 跳过。
     */
    private void closeOtherUnpaidAttempts(Long orderId, String winnerOutTradeNo) {
        List<PayAttempt> all = payAttemptPort.listByOrderId(orderId);
        for (PayAttempt a : all) {
            if (winnerOutTradeNo.equals(a.getOutTradeNo())) {
                continue;
            }
            if (a.getStatus() == PayAttemptStatus.SUCCESS
                    || a.getStatus() == PayAttemptStatus.REFUNDED
                    || a.getStatus() == PayAttemptStatus.REFUNDING) {
                continue;
            }
            try {
                mockWechatHttpClient.close(a.getOutTradeNo());
            } catch (Exception e) {
                log.warn("关单失败，旧页仍可能付成功 → 靠退款兜底 outTradeNo={}", a.getOutTradeNo(), e);
            }
            try {
                payAttemptPort.updateStatus(a.getId(), a.getStatus(), PayAttemptStatus.CLOSED, null);
            } catch (Exception e) {
                log.warn("本地标 CLOSED 失败 attemptId={}", a.getId(), e);
            }
        }
    }

    private boolean isPaid(Order order) {
        return order.getStatus() == OrderStatus.TO_BE_CONFIRMED
                && order.getPayStatus() == PayStatus.PAID;
    }
}
```

---

### 8.6 PayAttemptPortImpl 完整实现（system，手抄稿）

> 路径：`take-out-system/.../pay/PayAttemptPortImpl.java`  
> 风格对齐 `OrderPayPortImpl`：只注入 Mapper，状态更新带「期望旧状态」条件。  
> **本小节仅为设计对照，请自行手写到工程，勿让助手直接改实现类。**

```java
package com.sky.takeout.system.pay;

import java.util.Collections;
import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.sky.takeout.pay.port.PayAttemptPort;
import com.sky.takeout.pojo.entity.PayAttempt;
import com.sky.takeout.pojo.enums.PayAttemptStatus;
import com.sky.takeout.system.mapper.PayAttemptMapper;

/**
 * 支付尝试端口实现（system 侧）。
 * <p>
 * pay 模块只依赖 {@link PayAttemptPort}，不直接碰 Mapper；
 * 所有对 {@code pay_attempt} 表的读写都收敛在这里。
 * <p>
 * 与订单入账类似：状态变更尽量带「期望旧状态」条件（CAS 思想），
 * 避免并发下把别人已经改过的行又改回去。
 */
@Component
public class PayAttemptPortImpl implements PayAttemptPort {

    private final PayAttemptMapper payAttemptMapper;

    public PayAttemptPortImpl(PayAttemptMapper payAttemptMapper) {
        this.payAttemptMapper = payAttemptMapper;
    }

    /**
     * 按渠道商户单号查一条支付尝试。
     * <p>
     * 回调里 {@code MockPayNotifyDTO.orderNumber} 语义就是 {@code out_trade_no}，
     * 必须用本方法定位到 attempt，再拿到业务 {@code orderId}。
     * <p>
     * SQL 等价：
     * {@code SELECT * FROM pay_attempt WHERE out_trade_no = ? LIMIT 1}
     */
    @Override
    public PayAttempt findByOutTradeNo(String outTradeNo) {
        if (!StringUtils.hasText(outTradeNo)) {
            return null;
        }
        return payAttemptMapper.selectOne(new LambdaQueryWrapper<PayAttempt>()
                .eq(PayAttempt::getOutTradeNo, outTradeNo)
                .last("LIMIT 1"));
    }

    /**
     * 查某业务单当前「进行中」的支付尝试（同一时刻最多一条）。
     * <p>
     * 依赖表约束 {@code uk_order_paying(order_id, paying_flag)}：
     * 只有 PAYING 时 {@code paying_flag = 1}，其它状态为 NULL。
     * {@code requestPay} 复用逻辑会先调本方法。
     * <p>
     * SQL 等价：
     * {@code SELECT * FROM pay_attempt WHERE order_id = ? AND status = 'PAYING' LIMIT 1}
     */
    @Override
    public PayAttempt findPayingByOrderId(Long orderId) {
        if (orderId == null) {
            return null;
        }
        return payAttemptMapper.selectOne(new LambdaQueryWrapper<PayAttempt>()
                .eq(PayAttempt::getOrderId, orderId)
                .eq(PayAttempt::getStatus, PayAttemptStatus.PAYING)
                .last("LIMIT 1"));
    }

    /**
     * 列出某业务单下全部支付尝试（含历史 CLOSED / SUCCESS / REFUNDED）。
     * <p>
     * 入账成功后 {@code closeOtherUnpaidAttempts} 会用来遍历并关渠道未付单。
     * 无结果返回空列表，避免调用方 NPE。
     */
    @Override
    public List<PayAttempt> listByOrderId(Long orderId) {
        if (orderId == null) {
            return Collections.emptyList();
        }
        List<PayAttempt> list = payAttemptMapper.selectList(new LambdaQueryWrapper<PayAttempt>()
                .eq(PayAttempt::getOrderId, orderId)
                .orderByAsc(PayAttempt::getId));
        return list == null ? Collections.emptyList() : list;
    }

    /**
     * 插入一条「进行中」支付尝试。
     * <p>
     * 调用方应已填好 orderId / orderNumber / outTradeNo / amount 等；
     * 这里再强制校正 status=PAYING、payingFlag=1，防止漏填导致唯一约束失效。
     * <p>
     * 若同单已有 PAYING（paying_flag=1），会触发
     * {@code DuplicateKeyException}（uk_order_paying），由网关捕获后走「复用」分支。
     *
     * @return 影响行数，正常为 1
     */
    @Override
    public int insertPaying(PayAttempt payAttempt) {
        if (payAttempt == null) {
            return 0;
        }
        // 兜底：保证「进行中」语义与唯一约束字段一致
        payAttempt.setStatus(PayAttemptStatus.PAYING);
        payAttempt.setPayingFlag(1);
        return payAttemptMapper.insert(payAttempt);
    }

    /**
     * 带期望旧状态的状态迁移（CAS）。
     * <p>
     * 只有当前库中 {@code status == statusFrom} 时才更新为 {@code statusTo}，
     * 并写入新的 {@code payingFlag}（离开 PAYING 时传 null，释放「进行中」坑位）。
     * <p>
     * 典型用法：
     * <ul>
     *   <li>PAYING → SUCCESS，payingFlag=null（入账成功）</li>
     *   <li>PAYING → CLOSED，payingFlag=null（关单）</li>
     *   <li>任意未终态 → REFUNDING，再 REFUNDING → REFUNDED</li>
     * </ul>
     * <p>
     * 注意：payingFlag 为 null 时也必须写进 UPDATE。
     * 用实体 {@code setPayingFlag(null)} + {@code update(entity, wrapper)} 时，
     * MyBatis-Plus 默认常会跳过 null 字段，因此这里用 {@link LambdaUpdateWrapper#set}。
     * <p>
     * SQL 等价：
     * {@code UPDATE pay_attempt SET status=?, paying_flag=? WHERE id=? AND status=?}
     *
     * @return 影响行数：1=成功；0=状态已变 / 行不存在
     */
    @Override
    public int updateStatus(Long id, PayAttemptStatus statusFrom, PayAttemptStatus statusTo,
            Integer payingFlag) {
        if (id == null || statusFrom == null || statusTo == null) {
            return 0;
        }

        LambdaUpdateWrapper<PayAttempt> wrapper = new LambdaUpdateWrapper<PayAttempt>()
                .eq(PayAttempt::getId, id)
                .eq(PayAttempt::getStatus, statusFrom)
                .set(PayAttempt::getStatus, statusTo)
                .set(PayAttempt::getPayingFlag, payingFlag);

        return payAttemptMapper.update(null, wrapper);
    }

    /**
     * 写回假微信返回的 prepay_id（不影响状态机）。
     * <p>
     * native 下单成功后调用；仅按主键更新，不校验 status
     * （复用 PAYING 单时也会再次拿到 prepayId）。
     */
    @Override
    public int updatePrepayId(Long id, String prepayId) {
        if (id == null || !StringUtils.hasText(prepayId)) {
            return 0;
        }
        return payAttemptMapper.update(null, new LambdaUpdateWrapper<PayAttempt>()
                .eq(PayAttempt::getId, id)
                .set(PayAttempt::getPrepayId, prepayId));
    }
}
```

---

### 8.7 前端确认页 + OrderMockVO（必改）

#### 问题

现状前端大致用 **业务单号** 打开确认页：

```text
/mock/pay/checkout?out_trade_no={order.number}   // ❌ 错误
```

改造后渠道单号是 **支付尝试** 的 `outTradeNo`（如 `ORD...-A1739...`），再用 `order.number` 会对不上 `pay_attempt`，回调也找不到 attempt。

正确 URL：

```text
/mock/pay/checkout?out_trade_no={payAttempt.outTradeNo}   // ✅
```

#### OrderMockVO 增加字段

`take-out-pojo/.../vo/order/OrderMockVO.java`：

```java
@Data
public final class OrderMockVO {

    @Schema(description = "订单 id")
    private Long id;

    @Schema(description = "业务订单号 ORD...")
    private String number;

    @Schema(description = "实付金额")
    private BigDecimal amount;

    /** requestPay / 复用 PAYING 后写入：渠道商户单号 */
    @Schema(description = "当前支付尝试的 out_trade_no；未发起支付时可为 null")
    private String outTradeNo;

    /** requestPay 后写入：假微信确认页完整 URL，前端可直接 window.open */
    @Schema(description = "假微信收银台 URL")
    private String checkoutUrl;
}
```

#### 后端如何写入（requestPay 之后）

思路：`mockPay` 仍返回 `OrderMockVO`，但 **不能再** 只用 `BeanUtils.copyProperties(order, vo)`——`outTradeNo` / `checkoutUrl` 不在 `Order` 上。

建议拆一个组装方法（手抄示意）：

```java
// OrderServiceImpl.mockPay
@Override
public OrderMockVO mockPay(Long id) {
    Order order = mockPaymentGateway.requestPay(id);
    // 发起/复用后，库中应有一条 PAYING（或刚建好的）attempt
    PayAttempt paying = payAttemptPort.findPayingByOrderId(id);
    return toMockVO(order, paying);
}

private OrderMockVO toMockVO(Order order) {
    return toMockVO(order, null);
}

private OrderMockVO toMockVO(Order order, PayAttempt paying) {
    OrderMockVO vo = new OrderMockVO();
    BeanUtils.copyProperties(order, vo);

    if (paying != null && StringUtils.hasText(paying.getOutTradeNo())) {
        vo.setOutTradeNo(paying.getOutTradeNo());
        // base 来自 pay.mock-wechat-base-url，去掉末尾 /
        String base = trimTrailingSlash(payProperties.getMockWechatBaseUrl());
        vo.setCheckoutUrl(base + "/mock/pay/checkout?out_trade_no="
                + URLEncoder.encode(paying.getOutTradeNo(), StandardCharsets.UTF_8));
    }
    return vo;
}
```

可选增强（网关更干净）：让 `requestPay` 返回 `(Order, outTradeNo)` 或在 `OrderMockVO` 专用装配放在 admin/system 一层查 `findPayingByOrderId`，**不要**把 VO 拼装塞进 `MockPaymentGateway`。

`mock` 下单、`mockPayNotify` 回调返回时：`outTradeNo` / `checkoutUrl` 可为 null（前端不用打开收银台）。

#### 前端 mockPay.vue

现状（错误回退到业务单号）：

```ts
const outTradeNo = (data.data && data.data.number) || row.number
const checkoutUrl = this.openWechatCheckout(String(outTradeNo))
```

改为优先用接口返回值：

```ts
const payload = data.data || {}
// 优先后端拼好的 URL；否则用 outTradeNo 本地拼
if (payload.checkoutUrl) {
  window.open(payload.checkoutUrl, '_blank', 'width=440,height=720')
} else if (payload.outTradeNo) {
  this.openWechatCheckout(String(payload.outTradeNo))
} else {
  this.$message.error('未返回 outTradeNo，无法打开确认页')
  return
}
```

`openWechatCheckout` 可保留作兜底；**禁止**再 fallback 到 `row.number`。

#### 验收

1. `PUT /admin/order/mockPay/{id}` 响应 `data.outTradeNo` 形如 `ORD...-A...`，不是纯 `ORD...`  
2. `data.checkoutUrl` 含该 `out_trade_no`  
3. 打开确认页能查到假微信 NOTPAY 单；确认后回调能命中同一 `pay_attempt`  

---

## 9. 与现有能力的关系

| 现有 | 改造后是否保留 |
|------|----------------|
| HMAC 验签 + 时间窗 | 保留（签名字段仍是 out_trade_no） |
| nonce 去重 | 保留 |
| `order:pay:lock` | 保留（按业务 orderId） |
| CAS `casMarkPaid` | 保留（钱只入一次的根保证） |
| request 锁 | 保留 |
| Outbox Noop | 保留；关单/退款失败另做补偿即可 |
| `out_trade_no = order.number` | **废除** |

---

## 10. 测试计划

1. **单次支付**：mock → mockPay → checkout confirm → 业务单已付；attempt SUCCESS。  
2. **连点 mockPay**：两次返回同一 `outTradeNo`；库中仅一条 PAYING/后 SUCCESS。  
3. **关单生效**：SUCCESS 后对历史 NOTPAY 调 close；再 confirm 旧号应失败。  
4. **重复支付退款**（教学）：  
   - 造两条 attempt（第二条通过「先手工把第一条 paying_flag 清掉且不 close 渠道」或临时测试 API）  
   - 先付 A 入账，再付 B → B 变 REFUNDED，业务单仍一笔已付。  
5. **并发两回调同 attempt**：CAS + nonce，只成功一次。  

---

## 11. 实施顺序建议（手写学习）

1. DDL + 实体 + Port/Mapper  
2. 假微信 close/refund + confirm 拒 CLOSED  
3. Client 方法  
4. `requestPay` 占坑/复用（此时回调仍可暂时只用 order.number——**不推荐跨越多日**；最好第 5 步紧跟）  
5. 回调按 `out_trade_no` 查 attempt + 退款分支 + afterCommit 关单  
6. 前端确认页改 out_trade_no  
7. 补测试 / 演示脚本  

---

## 12. 一句话总结

**业务单只认 CAS 入账一次；支付单保证同时只有一个进行中；成功后关渠道未付；若仍出现第二笔 SUCCESS 则退款。**  
方案 A 用「多次 WECHAT 尝试 + 不同 out_trade_no」演练多渠道并发，不必上假支付宝。

---

## 13. 审阅检查（自审）

- [x] 无「之后再写」空壳：关单/退款/占坑均有代码稿  
- [x] 与现网冲突点写明：`out_trade_no` 不再等于 `orders.number`  
- [x] 范围锁定方案 A（单假微信）  
- [x] 未要求本期实现真 Outbox 表  
- [ ] **待你确认**：`requestPay` 对已有 PAYING 是「复用」还是「429 拒绝」——正文默认 **复用**  

确认本 spec 后，可再拆 `docs/superpowers/plans/2026-08-11-pay-attempt-single-active.md` 实施计划（或你按第 11 节手写）。
