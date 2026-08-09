# 模拟微信支付回调（HMAC + 核金额 + nonce + 异步）

日期：2026-08-09  
前提：已完成 `take-out-pay`、Redis 锁、Lua 解锁、`mock` 下单（待付款）。  
目标：**支付成功只允许走 notify**；`PUT mockPay/{id}` 只负责「模拟微信过一会打回调」。

> 手敲教程。不上 MQ。

---

## 0. 目标形态（方案 B）

```text
用户点「模拟支付」
  → PUT /admin/order/mockPay/{id}
  → 校验订单仍是待付款
  → 立即返回「已发起支付，等待回调」（此时库可能仍未付）
  → @Async 延迟约 1.5s（模拟用户输密码 + 微信处理）
  → 模拟微信 POST /admin/order/mockPay/notify（带 HMAC 签名）
  → handleNotify：验签 → 时间窗 → nonce 去重 → 核金额 → 锁 + CAS
  → 前端轮询列表，看到「待接单 + 已支付」
```

| 接口 | 角色 |
|------|------|
| `PUT /mockPay/{id}` | 用户侧「点付钱」→ 触发模拟渠道 |
| `POST /mockPay/notify` | 微信侧回调入口（白名单，无需登录 JWT） |

**删除旧语义**：不再在 `pay(id)` 里直接 CAS；改库只在 `handleNotify`。

---

## 1. yml 增补

`take-out-admin/.../application.yml` 的 `pay:` 下增加：

```yaml
pay:
  mock-secret: takeout_admin_pay_secret_key_cyrus
  order-idempotent-ttl-seconds: 300
  pay-lock-ttl-seconds: 10
  # 回调 nonce 去重 TTL（秒）
  nonce-ttl-seconds: 600
  # 允许的时间戳偏差（秒），防重放
  timestamp-skew-seconds: 300
  # 模拟微信延迟回调（毫秒）
  notify-delay-ms: 1500
```

---

## 2. `PayProperties.java`（完整）

```java
package com.sky.takeout.pay.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Data;

@ConfigurationProperties(prefix = "pay")
@Data
public class PayProperties {

    /** 模拟微信 API 密钥（签名用） */
    private String mockSecret;

    private Long orderIdempotentTtlSeconds;

    private Long payLockTtlSeconds;

    /** nonce 去重 key 过期（秒） */
    private Long nonceTtlSeconds = 600L;

    /** 回调 timestamp 允许偏差（秒） */
    private Long timestampSkewSeconds = 300L;

    /** 模拟渠道延迟发回调（毫秒） */
    private Long notifyDelayMs = 1500L;
}
```

---

## 3. DTO：`MockPayNotifyDTO.java`

路径：`take-out-pojo/.../dto/order/MockPayNotifyDTO.java`

```java
package com.sky.takeout.pojo.dto.order;

import java.math.BigDecimal;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 模拟微信支付结果通知（学习版，字段刻意简化）。
 * 真微信字段更多，但验签 / 金额 / 幂等思路相同。
 */
@Data
public class MockPayNotifyDTO {

    @NotBlank
    @Schema(description = "商户订单号，对应 orders.number")
    private String orderNumber;

    @NotNull
    @Schema(description = "渠道声称的实付金额，必须与订单 amount 一致")
    private BigDecimal amount;

    @NotNull
    @Schema(description = "秒级时间戳，防重放")
    private Long timestamp;

    @NotBlank
    @Schema(description = "一次性随机串，Redis 去重")
    private String nonce;

    @NotBlank
    @Schema(description = "HMAC-SHA256 十六进制签名")
    private String sign;
}
```

---

## 4. 签名工具：`HmacPaySignUtil.java`

路径：`take-out-pay/.../sign/HmacPaySignUtil.java`

签名串（字段名字典序，末尾拼 key）：

```text
amount={plain}&nonce={nonce}&orderNumber={orderNumber}&timestamp={timestamp}&key={secret}
```

`amount` 用 `BigDecimal.toPlainString()`，避免科学计数法。

```java
package com.sky.takeout.pay.sign;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * 模拟微信：HMAC-SHA256 签名 / 验签。
 */
public final class HmacPaySignUtil {

    private HmacPaySignUtil() {}

    /**
     * 拼明文（不含 sign 本身）。
     */
    public static String buildPlain(String orderNumber, BigDecimal amount, long timestamp, String nonce, String secret) {
        String amountPlain = amount.toPlainString();
        return "amount=" + amountPlain
                + "&nonce=" + nonce
                + "&orderNumber=" + orderNumber
                + "&timestamp=" + timestamp
                + "&key=" + secret;
    }

    public static String sign(String orderNumber, BigDecimal amount, long timestamp, String nonce, String secret) {
        String plain = buildPlain(orderNumber, amount, timestamp, nonce, secret);
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] raw = mac.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(raw);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("HMAC 签名失败", e);
        }
    }

    /** 常量时间比较，降低时序攻击面（学习了解即可） */
    public static boolean verify(String orderNumber, BigDecimal amount, long timestamp,
                                 String nonce, String secret, String signFromChannel) {
        if (signFromChannel == null) {
            return false;
        }
        String expect = sign(orderNumber, amount, timestamp, nonce, secret);
        return MessageDigest.isEqual(
                expect.getBytes(StandardCharsets.UTF_8),
                signFromChannel.getBytes(StandardCharsets.UTF_8));
    }
}
```

---

## 5. 端口扩展：按订单号查单

### `OrderPayPort.java`

```java
package com.sky.takeout.pay.port;

import com.sky.takeout.pojo.entity.Order;

public interface OrderPayPort {

    Order findOrderById(Long orderId);

    /** 回调里只有商户订单号 */
    Order findOrderByNumber(String orderNumber);

    /**
     * CAS：待付款+未支付 → 待接单+已支付
     * @return 1 成功，0 状态已变
     */
    int casMarkPaid(Long orderId);
}
```

### `OrderPayPortImpl.java` 增加

```java
@Override
public Order findOrderByNumber(String orderNumber) {
    if (orderNumber == null || orderNumber.isBlank()) {
        return null;
    }
    return orderMapper.selectOne(new LambdaQueryWrapper<Order>()
            .eq(Order::getNumber, orderNumber)
            .last("LIMIT 1"));
}
```

（`casMarkPaid` 条件保持：`PENDING_PAYMENT` + `UNPAID`。）

---

## 6. 开启异步：`PayAsyncConfiguration.java`

路径：`take-out-pay/.../config/PayAsyncConfiguration.java`

```java
package com.sky.takeout.pay.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

@Configuration
@EnableAsync
public class PayAsyncConfiguration {
}
```

> `@Async` 必须开在**别的 Bean**上调用，同类自调用不生效。所以渠道模拟单独一个类。

---

## 7. 模拟微信渠道：`MockWechatPayClient.java`

路径：`take-out-pay/.../gateway/MockWechatPayClient.java`

```java
package com.sky.takeout.pay.gateway;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import com.sky.takeout.pay.config.PayProperties;
import com.sky.takeout.pay.port.OrderPayPort;
import com.sky.takeout.pay.sign.HmacPaySignUtil;
import com.sky.takeout.pojo.dto.order.MockPayNotifyDTO;
import com.sky.takeout.pojo.entity.Order;

/**
 * 扮演「微信支付服务器」：延迟后带着签名打我们的 notify。
 * 真项目里这一步是微信机房的 HTTP；这里是进程内 @Async。
 */
@Component
public class MockWechatPayClient {

    private static final Logger log = LoggerFactory.getLogger(MockWechatPayClient.class);

    private final OrderPayPort orderPayPort;
    private final PayProperties payProperties;
    private final MockPaymentGateway mockPaymentGateway;

    public MockWechatPayClient(OrderPayPort orderPayPort,
                               PayProperties payProperties,
                               MockPaymentGateway mockPaymentGateway) {
        this.orderPayPort = orderPayPort;
        this.payProperties = payProperties;
        this.mockPaymentGateway = mockPaymentGateway;
    }

    /**
     * 异步模拟渠道回调。不要在这个方法里直接改订单——只构造回调并交给网关。
     */
    @Async
    public void sendPaidNotifyAsync(Long orderId) {
        long delay = payProperties.getNotifyDelayMs() == null ? 1500L : payProperties.getNotifyDelayMs();
        try {
            Thread.sleep(delay);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }

        Order order = orderPayPort.findOrderById(orderId);
        if (order == null) {
            log.warn("模拟微信回调：订单不存在 id={}", orderId);
            return;
        }

        String secret = payProperties.getMockSecret();
        if (secret == null || secret.isBlank()) {
            log.error("pay.mock-secret 未配置，无法签名");
            return;
        }

        long timestamp = System.currentTimeMillis() / 1000;
        String nonce = UUID.randomUUID().toString().replace("-", "");
        String sign = HmacPaySignUtil.sign(
                order.getNumber(), order.getAmount(), timestamp, nonce, secret);

        MockPayNotifyDTO dto = new MockPayNotifyDTO();
        dto.setOrderNumber(order.getNumber());
        dto.setAmount(order.getAmount());
        dto.setTimestamp(timestamp);
        dto.setNonce(nonce);
        dto.setSign(sign);

        log.info("模拟微信发起回调 orderNumber={}, delayMs={}", order.getNumber(), delay);
        try {
            // 进程内调用 = 真项目里的 HTTP POST /notify
            mockPaymentGateway.handleNotify(dto);
        } catch (Exception ex) {
            // 真微信会重试；学习版打日志即可
            log.warn("模拟回调处理失败 orderNumber={}: {}", order.getNumber(), ex.getMessage());
        }
    }
}
```

---

## 8. 网关完整：`MockPaymentGateway.java`

```java
package com.sky.takeout.pay.gateway;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.sky.takeout.common.exception.BusinessException;
import com.sky.takeout.common.result.ErrorCode;
import com.sky.takeout.pay.config.PayProperties;
import com.sky.takeout.pay.port.OrderPayPort;
import com.sky.takeout.pay.redis.RedisIdempotentHelper;
import com.sky.takeout.pay.sign.HmacPaySignUtil;
import com.sky.takeout.pojo.dto.order.MockPayNotifyDTO;
import com.sky.takeout.pojo.entity.Order;
import com.sky.takeout.pojo.enums.OrderStatus;
import com.sky.takeout.pojo.enums.PayStatus;

/**
 * 支付网关：
 * - requestPay：用户点付钱 → 触发模拟微信异步回调（不改库）
 * - handleNotify：唯一改库入口（验签 + nonce + 核金额 + 锁 + CAS）
 */
@Component
public class MockPaymentGateway {

    private static final Logger log = LoggerFactory.getLogger(MockPaymentGateway.class);
    private static final String PAY_LOCK_PREFIX = "order:pay:lock:";
    private static final String NONCE_KEY_PREFIX = "order:pay:nonce:";

    private final OrderPayPort orderPayPort;
    private final PayProperties payProperties;
    private final RedisIdempotentHelper redisHelper;
    private final MockWechatPayClient mockWechatPayClient;

    public MockPaymentGateway(OrderPayPort orderPayPort,
                              PayProperties payProperties,
                              RedisIdempotentHelper redisHelper,
                              MockWechatPayClient mockWechatPayClient) {
        this.orderPayPort = orderPayPort;
        this.payProperties = payProperties;
        this.redisHelper = redisHelper;
        this.mockWechatPayClient = mockWechatPayClient;
    }

    /**
     * 用户点「模拟支付」：只发起，不直接 CAS。
     * @return 当前订单（多半仍是待付款），供前端展示
     */
    public Order requestPay(Long orderId) {
        if (orderId == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "订单 id 不能为空");
        }
        Order order = requireOrderById(orderId);
        if (isPaid(order)) {
            // 已付：不必再发回调
            return order;
        }
        if (order.getStatus() != OrderStatus.PENDING_PAYMENT
                || order.getPayStatus() != PayStatus.UNPAID) {
            throw new BusinessException(ErrorCode.CONFLICT, "当前订单不可支付");
        }
        // 异步：过一会「微信」打 notify
        mockWechatPayClient.sendPaidNotifyAsync(orderId);
        log.info("已请求模拟微信支付 orderId={}, number={}", orderId, order.getNumber());
        return order;
    }

    /**
     * 模拟微信回调处理（也可用 Postman 直接打这个逻辑对应的 HTTP）。
     * 验签失败抛错；重复 nonce / 已支付 → 当作成功（渠道重试友好）。
     */
    @Transactional(rollbackFor = Exception.class)
    public Order handleNotify(MockPayNotifyDTO dto) {
        if (dto == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "回调体为空");
        }

        String secret = payProperties.getMockSecret();
        if (!StringUtils.hasText(secret)) {
            throw new BusinessException(ErrorCode.ERROR, "未配置 pay.mock-secret");
        }

        // 1) 验签
        boolean ok = HmacPaySignUtil.verify(
                dto.getOrderNumber(), dto.getAmount(), dto.getTimestamp(),
                dto.getNonce(), secret, dto.getSign());
        if (!ok) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "验签失败");
        }

        // 2) 时间窗（防重放）
        long skew = payProperties.getTimestampSkewSeconds() == null
                ? 300L : payProperties.getTimestampSkewSeconds();
        long now = System.currentTimeMillis() / 1000;
        if (Math.abs(now - dto.getTimestamp()) > skew) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "回调已过期");
        }

        // 3) nonce 去重：SET NX，失败说明已处理过 → 直接按成功返回当前单
        long nonceTtl = payProperties.getNonceTtlSeconds() == null
                ? 600L : payProperties.getNonceTtlSeconds();
        String nonceKey = NONCE_KEY_PREFIX + dto.getNonce();
        boolean firstNonce = redisHelper.trySetNx(nonceKey, "1", nonceTtl);
        if (!firstNonce) {
            log.info("回调 nonce 重复，幂等返回 orderNumber={}", dto.getOrderNumber());
            Order existed = orderPayPort.findOrderByNumber(dto.getOrderNumber());
            if (existed == null) {
                throw new BusinessException(ErrorCode.CONFLICT, "订单不存在");
            }
            return existed;
        }

        try {
            // 4) 查单
            Order order = orderPayPort.findOrderByNumber(dto.getOrderNumber());
            if (order == null) {
                throw new BusinessException(ErrorCode.CONFLICT, "订单不存在");
            }

            // 5) 核金额（compareTo，别用 equals）
            if (order.getAmount() == null
                    || order.getAmount().compareTo(dto.getAmount()) != 0) {
                throw new BusinessException(ErrorCode.CONFLICT, "支付金额与订单不一致");
            }

            // 6) 已支付：业务幂等，回成功
            if (isPaid(order)) {
                return order;
            }

            // 7) 锁 + CAS（原 pay 核心）
            return markPaidWithLock(order.getId());
        } catch (RuntimeException ex) {
            // 业务失败时删掉 nonce，允许渠道用新请求重试（学习简化）
            // 真项目更常见：验签通过后的失败也留 nonce + 记失败流水，视策略而定
            redisHelper.delete(nonceKey);
            throw ex;
        }
    }

    /** 原 pay() 的锁 + CAS，仅供 notify 内部调用 */
    private Order markPaidWithLock(Long orderId) {
        Order order = requireOrderById(orderId);
        if (isPaid(order)) {
            return order;
        }

        String lockKey = PAY_LOCK_PREFIX + orderId;
        Long ttl = payProperties.getPayLockTtlSeconds();
        if (ttl == null || ttl <= 0) {
            ttl = 10L;
        }
        String lockToken = redisHelper.tryLock(lockKey, ttl);
        if (lockToken == null) {
            throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS, "支付处理中，请勿重复回调");
        }

        try {
            order = requireOrderById(orderId);
            if (isPaid(order)) {
                return order;
            }
            if (order.getStatus() != OrderStatus.PENDING_PAYMENT
                    || order.getPayStatus() != PayStatus.UNPAID) {
                throw new BusinessException(ErrorCode.CONFLICT, "当前订单不可支付");
            }

            int rows = orderPayPort.casMarkPaid(orderId);
            if (rows == 0) {
                Order latest = requireOrderById(orderId);
                if (isPaid(latest)) {
                    return latest;
                }
                throw new BusinessException(ErrorCode.CONFLICT, "订单状态已变更，支付失败");
            }
            log.info("回调支付成功 orderId={}", orderId);
            return requireOrderById(orderId);
        } finally {
            redisHelper.unlock(lockKey, lockToken);
        }
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

> **循环依赖注意**：`MockPaymentGateway` ↔ `MockWechatPayClient` 构造器互引时，可给其中一侧加 `@Lazy`：
>
> ```java
> public MockPaymentGateway(..., @Lazy MockWechatPayClient mockWechatPayClient)
> ```

---

## 9. OrderService / Controller

### `OrderServiceImpl.mockPay`

```java
@Override
public OrderMockVO mockPay(Long id) {
    // 只发起异步回调，立刻返回当前单（多半仍待付款）
    return toMockVO(mockPaymentGateway.requestPay(id));
}
```

### 新增 Service 方法（可选，给 notify 用）

```java
// OrderService
OrderMockVO mockPayNotify(MockPayNotifyDTO dto);

// Impl
@Override
public OrderMockVO mockPayNotify(MockPayNotifyDTO dto) {
    return toMockVO(mockPaymentGateway.handleNotify(dto));
}
```

### `OrderController`

```java
@PutMapping("/mockPay/{id}")
public Result<OrderMockVO> mockPay(@PathVariable Long id) {
    // data 里仍是当前订单；提示前端：请等待回调后刷新
    return Result.success(orderService.mockPay(id));
}

@PostMapping("/mockPay/notify")
public Result<OrderMockVO> mockPayNotify(@Valid @RequestBody MockPayNotifyDTO dto) {
    return Result.success(orderService.mockPayNotify(dto));
}
```

---

## 10. 回调白名单（像真微信：不带用户 JWT）

`SecurityConstant.java`：

```java
public static final String[] WHITE_LIST = {
    "/admin/employee/login",
    "/admin/order/mockPay/notify",
    "/v3/api-docs/**",
    "/swagger-ui/**",
    "/swagger-ui.html"
};
```

---

## 11. 前端 `mockPay.vue`（轮询）

`handlePay` 成功后不要当成「已付」，改为提示并轮询：

```ts
const { data } = await mockPayOrder(row.id)
if (data.code === 1) {
  this.$message.success('已发起支付，等待模拟微信回调…')
  await this.pollUntilPaid(row.id)
} else {
  this.$message.error(data.msg || '发起支付失败')
}

// 方法：
private async pollUntilPaid(orderId: number) {
  for (let i = 0; i < 10; i++) {
    await new Promise((r) => setTimeout(r, 800))
    await this.loadList()
    const row = this.tableData.find((x: any) => x.id === orderId)
    // 列表只查待付款时，付成功后该行会消失也算成功
    if (!row) {
      this.$message.success('支付成功（回调已处理）')
      return
    }
    const st = row.status && row.status.code != null ? row.status.code : row.status
    const ps = row.payStatus && row.payStatus.code != null ? row.payStatus.code : row.payStatus
    if (String(st) === '2' && String(ps) === '1') {
      this.$message.success('支付成功，订单已进入待接单')
      return
    }
  }
  this.$message.warning('仍未收到支付结果，请稍后点刷新')
}
```

（若列表固定 `status=1` 待付款，付成功后行消失，用「找不到该 id」判断即可。）

---

## 12. Postman 当微信（可选验收）

1. 查库拿到 `number`、`amount`。  
2. 用同一规则算 `sign`（或临时写个 main / 单测调用 `HmacPaySignUtil.sign`）。  
3. **不带** Authorization：

```http
POST http://localhost:8080/admin/order/mockPay/notify
Content-Type: application/json

{
  "orderNumber": "ORD....",
  "amount": 62.00,
  "timestamp": 1730000000,
  "nonce": "abc123unique",
  "sign": "算出来的hex"
}
```

错签 → 失败；同 nonce 再发 → 幂等成功；改 amount → 金额不一致。

---

## 13. Redis 可观察

```bash
docker exec -it take-out-redis redis-cli
KEYS order:pay:*
GET order:pay:nonce:你的nonce
TTL order:pay:nonce:你的nonce
```

支付完成后锁会删；nonce 会留到 TTL。

---

## 14. 手敲清单

- [ ] yml 三个新配置  
- [ ] `PayProperties` 字段  
- [ ] `MockPayNotifyDTO`  
- [ ] `HmacPaySignUtil`  
- [ ] `OrderPayPort.findOrderByNumber` + Impl  
- [ ] `PayAsyncConfiguration`  
- [ ] `MockWechatPayClient`（@Async）  
- [ ] 重写 `MockPaymentGateway`（requestPay + handleNotify）  
- [ ] `@Lazy` 打破循环依赖（若启动报循环）  
- [ ] Controller + Service  
- [ ] 白名单 notify  
- [ ] 前端轮询  
- [ ] `mvn -pl take-out-admin -am spring-boot:run` 验收  

---

## 15. 面试口述

1. 用户支付与渠道回调分离；**改单以回调为准**。  
2. 回调必须：**验签 →（时间窗）→ 幂等（nonce）→ 核金额 → 条件更新**。  
3. 重复通知要当成功，别让微信一直重试。  
4. MQ 不是必须；先 Redis nonce + DB CAS，量上来再考虑异步化。

---

## 16. 和旧代码差异一览

| 旧 | 新 |
|----|----|
| `mockPay` → `pay()` 直接 CAS | `mockPay` → `requestPay` → 异步 notify → `handleNotify` 才 CAS |
| 无签名 | HMAC-SHA256 + `mock-secret` |
| 无 nonce | `order:pay:nonce:{nonce}` |
| 信任路径 id | 回调用订单号 + 核对金额 |
