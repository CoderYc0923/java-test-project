# 教程：外卖支付对接假微信 HTTP API

日期：2026-08-10  
前提：

- 已跑通进程内 Mock：`MockWeChatPayClient` `@Async` + `handlePayNotify`（见 `2026-08-09-mock-wechat-pay-notify.md`）
- 已启动独立沙箱：`take-out-mock-wechat`（见模块 [`API.md`](../../take-out-mock-wechat/API.md)）

目标：把「点支付」从 **进程内 sleep 后自己回调自己**，改成 **HTTP 调假微信统一下单**；付款成功由假微信在 **确认支付** 后 **HTTP POST** 商户 `notify_url`。  
`handlePayNotify` 验签 / nonce / CAS **尽量不动**。

> 本文是手敲改造教程：含完整代码与注释。按节改即可；也可整段对照替换。

---

## 0. 改造前后对比

### 0.1 现在（同进程）

```text
PUT /admin/order/mockPay/{id}
  → MockPaymentGateway.requestPay
  → MockWeChatPayClient.sendPaidNotifyAsync  (@Async + sleep)
  → 进程内 mockPaymentGateway.handlePayNotify(dto)   ← 没有真 HTTP
```

### 0.2 目标（双进程，对齐真实习惯）

```text
PUT /admin/order/mockPay/{id}
  → 校验待付款
  → HTTP POST 假微信 /v3/pay/transactions/native   ← 统一下单，拿到 prepay_id
  → 立即返回（库仍是待付款）

操作员 / Postman：
  → POST 假微信 /mock/pay/confirm

假微信 :9090
  → HTTP POST 商户 /admin/order/mockPay/notify（HMAC 五字段）
  → MockPaymentGateway.handlePayNotify   ← 仍走原验签+CAS
```

| 步骤 | 谁发起 | 对应假微信 API |
|------|--------|----------------|
| 统一下单 | 外卖 pay 模块 | `POST /v3/pay/transactions/native` |
| 确认支付 | 人（Postman）或后续你再封装 | `POST /mock/pay/confirm` |
| 支付通知 | 假微信 → 外卖 | 出站 `notify_url` |

**密钥：** `pay.mock-secret` ≡ `mock-wechat.merchant-notify-secret`。

---

## 1. 改哪些文件（清单）

| 动作 | 路径 |
|------|------|
| 改 | `take-out-pay/pom.xml`（加 `spring-web`，给 `RestClient`） |
| 改 | `PayProperties.java` |
| 改 | `application.yml`（admin） |
| 新建 | `pay/client/dto/NativePayRequest.java`（出站请求） |
| 新建 | `pay/client/dto/TransactionResponse.java`（出站响应） |
| 新建 | `pay/config/PayHttpClientConfig.java` |
| 新建 | `pay/client/MockWechatHttpClient.java`（HTTP 客户端） |
| 改 | `MockPaymentGateway.java`（`requestPay` 调 HTTP） |
| 删或废弃 | `MockWeChatPayClient.java`（旧 `@Async` 方案） |

**不要改（本期）：** `handlePayNotify`、`HmacPaySignUtil`、`OrderController` 的 `/mockPay/notify`、Security 白名单。

---

## 2. Maven：pay 模块引入 spring-web

`take-out-pay` 是库模块，不要用 `spring-boot-starter-webmvc`（会拖嵌入式容器）。只加 Web 客户端相关：

```xml
<!-- take-out-pay/pom.xml 的 <dependencies> 内追加 -->
<!-- RestClient 在 spring-web；运行时由 admin 的 webmvc 提供实现类 -->
<dependency>
    <groupId>org.springframework</groupId>
    <artifactId>spring-web</artifactId>
</dependency>
```

编译验证：

```bash
mvn -pl take-out-pay,take-out-admin -am compile -q
```

---

## 3. 配置：PayProperties + yml

### 3.1 `PayProperties.java`（完整替换建议）

```java
package com.sky.takeout.pay.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Data;

/**
 * 支付中心配置。
 * <p>
 * 对接假微信后：
 * <ul>
 *   <li>{@code mockSecret}：与假微信 merchant-notify-secret 相同，用于验签回调</li>
 *   <li>{@code mockWechatBaseUrl}：假微信 Base URL</li>
 *   <li>{@code merchantNotifyUrl}：告诉假微信「付成功后回调哪里」</li>
 * </ul>
 * {@code notifyDelayMs} 仅旧版进程内 Async 使用；HTTP 方案可忽略。
 */
@Data
@ConfigurationProperties(prefix = "pay")
public class PayProperties {

    /** 与假微信 mock-wechat.merchant-notify-secret 必须一致 */
    private String mockSecret;

    private Long orderIdempotentTtlSeconds;

    private Long payLockTtlSeconds;

    /** 回调 nonce 去重 TTL（秒） */
    private Long nonceTtlSeconds = 600L;

    /** 允许的时间戳偏差（秒） */
    private Long timestampSkewSeconds = 300L;

    /**
     * 旧版：进程内延迟回调毫秒数。
     * HTTP 方案下可删除或保留无关。
     */
    private Long notifyDelayMs = 1500L;

    /**
     * 假微信沙箱地址，不要末尾斜杠。
     * 例：http://127.0.0.1:9090
     */
    private String mockWechatBaseUrl = "http://127.0.0.1:9090";

    /**
     * 商户支付结果通知 URL（完整路径），下单时传给假微信的 notify_url。
     * 例：http://127.0.0.1:8080/admin/order/mockPay/notify
     * <p>
     * Docker 里假微信若访问宿主机，可能要用 host.docker.internal。
     */
    private String merchantNotifyUrl = "http://127.0.0.1:8080/admin/order/mockPay/notify";
}
```

### 3.2 `application.yml`（admin）

在现有 `pay:` 下追加两行（密钥保持与假微信一致）：

```yaml
pay:
  mock-secret: takeout_admin_pay_secret_key_cyrus
  order-idempotent-ttl-seconds: 300
  pay-lock-ttl-seconds: 10
  nonce-ttl-seconds: 600
  timestamp-skew-seconds: 300
  # 对接假微信
  mock-wechat-base-url: http://127.0.0.1:9090
  merchant-notify-url: http://127.0.0.1:8080/admin/order/mockPay/notify
```

假微信侧同步改密钥（二选一）：

```yaml
# take-out-mock-wechat/.../application.yml
mock-wechat:
  merchant-notify-secret: takeout_admin_pay_secret_key_cyrus
  notify-max-retries: 2
  notify-retry-delay-ms: 500
```

或启动参数：

```bash
mvn -pl take-out-mock-wechat spring-boot:run -Dspring-boot.run.arguments="--mock-wechat.merchant-notify-secret=takeout_admin_pay_secret_key_cyrus"
```

---

## 4. 出站 DTO（对齐假微信 API 蛇形字段）

包名建议：`com.sky.takeout.pay.client.dto`（与网关 gateway 区分：client = 调外部渠道）。

### 4.1 `NativePayRequest.java`

```java
package com.sky.takeout.pay.client.dto;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Builder;
import lombok.Data;

/**
 * 调用假微信「统一下单」的请求体。
 * 字段名使用蛇形，与 take-out-mock-wechat/API.md §2 一致。
 */
@Data
@Builder
public class NativePayRequest {

    /** 商户订单号 = orders.number */
    @JsonProperty("out_trade_no")
    private String outTradeNo;

    /** 商品描述 */
    private String description;

    /** 付成功后假微信回调的商户 URL */
    @JsonProperty("notify_url")
    private String notifyUrl;

    /** 金额：元（BigDecimal），须与订单 amount 一致 */
    private BigDecimal amount;
}
```

### 4.2 `TransactionResponse.java`

```java
package com.sky.takeout.pay.client.dto;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

/**
 * 假微信统一下单 / 查单成功响应。
 * @see take-out-mock-wechat API.md §2 / §3
 */
@Data
public class TransactionResponse {

    @JsonProperty("out_trade_no")
    private String outTradeNo;

    /** 预支付 id，形如 wx_prepay_...；本期可只打日志，不必落库 */
    @JsonProperty("prepay_id")
    private String prepayId;

    private String description;

    private BigDecimal amount;

    private String currency;

    /** NOTPAY / SUCCESS */
    @JsonProperty("trade_state")
    private String tradeState;
}
```

---

## 5. RestClient 配置

```java
package com.sky.takeout.pay.config;

import java.time.Duration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * 支付中心出站 HTTP（调假微信）。
 * 超时避免假微信挂死拖垮 Tomcat 线程。
 */
@Configuration
public class PayHttpClientConfig {

    @Bean
    public RestClient.Builder payRestClientBuilder() {
        // JDK HttpClient 作为底层；也可用其他 ClientHttpRequestFactory
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(3));
        factory.setReadTimeout(Duration.ofSeconds(5));
        return RestClient.builder().requestFactory(factory);
    }
}
```

若 Boot 版本对 `JdkClientHttpRequestFactory` API 有差异，可改用 `SimpleClientHttpRequestFactory` 设 `connectTimeout` / `readTimeout`（毫秒）。

确保 `PayAutoConfiguration` 或组件扫描能扫到本配置（pay 已在 admin 引入且有 `@Configuration` 扫描时通常无问题；若 Bean 缺失，在 `PayAutoConfiguration` 上加 `@Import(PayHttpClientConfig.class)`）。

---

## 6. HTTP 客户端（完整代码 + 注解）

新建 `MockWechatHttpClient.java`，**替代**旧的 `MockWeChatPayClient` 职责中的「向渠道下单」部分。  
「确认支付」按 API 文档由人工调假微信，本类可不封装 confirm（需要的话文末附录给出）。

```java
package com.sky.takeout.pay.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import com.sky.takeout.common.exception.BusinessException;
import com.sky.takeout.common.result.ErrorCode;
import com.sky.takeout.pay.client.dto.NativePayRequest;
import com.sky.takeout.pay.client.dto.TransactionResponse;
import com.sky.takeout.pay.config.PayProperties;
import com.sky.takeout.pojo.entity.Order;

/**
 * 商户侧「微信支付 SDK」的教学版：用 RestClient 调假微信 HTTP API。
 * <p>
 * 真实项目里这里会是官方 SDK 或自签 RSA 的 HTTP 调用；
 * 本类对应 API.md：POST /v3/pay/transactions/native 。
 * <p>
 * 注意：本类<strong>不</strong>直接改订单支付状态；付成功只认商户 notify。
 */
@Component
public class MockWechatHttpClient {

    private static final Logger log = LoggerFactory.getLogger(MockWechatHttpClient.class);

    private final RestClient restClient;
    private final PayProperties payProperties;

    public MockWechatHttpClient(RestClient.Builder payRestClientBuilder, PayProperties payProperties) {
        this.payProperties = payProperties;
        // baseUrl 来自配置，便于换环境（本机 9090 / docker 服务名）
        String base = trimTrailingSlash(payProperties.getMockWechatBaseUrl());
        this.restClient = payRestClientBuilder.baseUrl(base).build();
    }

    /**
     * 向假微信发起统一下单。
     *
     * @param order 本地待付款订单（须已有 number、amount）
     * @return 渠道返回的交易信息（含 prepay_id、trade_state=NOTPAY）
     */
    public TransactionResponse createNativePay(Order order) {
        if (order == null || !StringUtils.hasText(order.getNumber())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "订单号不能为空");
        }
        if (order.getAmount() == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "订单金额不能为空");
        }
        if (!StringUtils.hasText(payProperties.getMerchantNotifyUrl())) {
            throw new BusinessException(ErrorCode.ERROR, "pay.merchant-notify-url 未配置");
        }

        // 拼请求：字段与假微信 NativePayRequest 对齐
        NativePayRequest body = NativePayRequest.builder()
                .outTradeNo(order.getNumber())
                .description("外卖订单-" + order.getNumber())
                .notifyUrl(payProperties.getMerchantNotifyUrl())
                .amount(order.getAmount())
                .build();

        log.info("请求假微信统一下单 outTradeNo={} amount={} notifyUrl={}",
                body.getOutTradeNo(), body.getAmount(), body.getNotifyUrl());

        try {
            TransactionResponse resp = restClient.post()
                    .uri("/v3/pay/transactions/native")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    // 4xx/5xx 会抛 RestClientResponseException
                    .body(TransactionResponse.class);

            if (resp == null || !StringUtils.hasText(resp.getPrepayId())) {
                throw new BusinessException(ErrorCode.ERROR, "假微信下单响应缺少 prepay_id");
            }

            log.info("假微信下单成功 outTradeNo={} prepayId={} tradeState={}",
                    resp.getOutTradeNo(), resp.getPrepayId(), resp.getTradeState());
            return resp;

        } catch (RestClientResponseException e) {
            // 把渠道 HTTP 状态与 body 打出来，方便对照 API.md 错误码
            log.error("假微信下单失败 status={} body={}", e.getStatusCode().value(), e.getResponseBodyAsString());
            throw new BusinessException(ErrorCode.ERROR,
                    "假微信下单失败: HTTP " + e.getStatusCode().value());
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            // 连接拒绝等：假微信没启动最常见
            log.error("假微信下单异常: {}", e.getMessage());
            throw new BusinessException(ErrorCode.ERROR, "无法连接假微信，请确认 9090 已启动");
        }
    }

    /**
     * 可选：查单，用于排障或前端轮询渠道态（本期 requestPay 可不调用）。
     */
    public TransactionResponse queryByOutTradeNo(String outTradeNo) {
        try {
            return restClient.get()
                    .uri("/v3/pay/transactions/out-trade-no/{outTradeNo}", outTradeNo)
                    .retrieve()
                    .body(TransactionResponse.class);
        } catch (RestClientResponseException e) {
            log.warn("假微信查单失败 outTradeNo={} status={}", outTradeNo, e.getStatusCode().value());
            throw new BusinessException(ErrorCode.ERROR, "假微信查单失败");
        }
    }

    private static String trimTrailingSlash(String url) {
        if (url == null || url.isBlank()) {
            return "http://127.0.0.1:9090";
        }
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
```

---

## 7. 改造 `MockPaymentGateway.requestPay`

只改「点支付」入口；**`handlePayNotify` 整段保留**。

### 7.1 构造注入替换

把 `MockWeChatPayClient` 换成 `MockWechatHttpClient`，可去掉 `@Lazy`（不再有循环依赖）。

```java
private final MockWechatHttpClient mockWechatHttpClient;

public MockPaymentGateway(OrderPayPort orderPayPort, PayProperties payProperties,
        RedisIdempotentHelper redisIdempotentHelper, MockWechatHttpClient mockWechatHttpClient) {
    this.orderPayPort = orderPayPort;
    this.payProperties = payProperties;
    this.redisIdempotentHelper = redisIdempotentHelper;
    this.mockWechatHttpClient = mockWechatHttpClient;
}
```

### 7.2 `requestPay` 完整方法（带注释）

```java
/**
 * 用户点「去支付」：向假微信统一下单，不在这里改支付状态。
 * <p>
 * 支付成功只发生在：假微信 confirm 之后 HTTP 回调 → {@link #handlePayNotify}。
 */
public Order requestPay(Long orderId) {
    if (orderId == null) {
        throw new BusinessException(ErrorCode.BAD_REQUEST, "订单id不能为空");
    }

    Order order = requireOrder(orderId);

    // 已支付：幂等直接返回（前端轮询友好）
    if (isPaid(order)) {
        log.info("订单{}已支付，直接返回", orderId);
        return order;
    }

    // 仅允许「待付款 + 未支付」发起渠道下单
    if (order.getStatus() != OrderStatus.PENDING_PAYMENT
            || order.getPayStatus() != PayStatus.UNPAID) {
        throw new BusinessException(ErrorCode.CONFLICT, "当前订单不可支付");
    }

    // ★ 核心改造：HTTP 统一下单，替代旧的 sendPaidNotifyAsync
    // 假微信返回 prepay_id；订单库状态仍是待付款，等回调
    TransactionResponse channelResp = mockWechatHttpClient.createNativePay(order);
    log.info("已向假微信下单 orderId={} number={} prepayId={}",
            orderId, order.getNumber(), channelResp.getPrepayId());

    // 可选：把 prepayId 存 Redis / 扩展字段，便于对账；一期打日志即可
    return order;
}
```

记得增加 import：

```java
import com.sky.takeout.pay.client.MockWechatHttpClient;
import com.sky.takeout.pay.client.dto.TransactionResponse;
```

---

## 8. 删除 / 废弃旧客户端

1. 删除（或移到 `deprecated` 包并标 `@Deprecated`）`MockWeChatPayClient.java`。  
2. 全局搜索 `sendPaidNotifyAsync`、`MockWeChatPayClient`，确保无残留引用。  
3. `PayProperties.notifyDelayMs` 可留着无用，或删掉及相关 yml。  
4. 若不再需要 `@EnableAsync` 仅服务于支付回调，可检查 `PayAsyncConfiguration` 是否还有别的异步任务；没有则可后续再清。

---

## 9. 联调剧本（务必双进程）

### 9.1 启动

```bash
# 终端 1：假微信
mvn -pl take-out-mock-wechat spring-boot:run

# 终端 2：外卖（密钥已对齐）
mvn -pl take-out-admin spring-boot:run
```

### 9.2 步骤

1. 管理端 / Postman：`POST /admin/order/mock` 造一笔 **待付款** 单，记下 `id` 与 `number`。  
2. `PUT /admin/order/mockPay/{id}` → 假微信日志应有统一下单；库中订单**仍未支付**。  
3. 查假微信：

```bash
curl http://127.0.0.1:9090/v3/pay/transactions/out-trade-no/{你的订单号}
# trade_state 应为 NOTPAY
```

4. **确认支付**（关键一步，以前是 sleep 自动完成，现在要手动）：

```bash
curl -X POST http://127.0.0.1:9090/mock/pay/confirm \
  -H "Content-Type: application/json" \
  -d "{\"out_trade_no\":\"你的订单号\"}"
```

5. 假微信应 POST `merchant-notify-url`；外卖 `handlePayNotify` 验签通过后 CAS → **待接单 + 已支付**。  
6. 再查管理端订单详情 / 列表验证。

### 9.3 常见失败

| 现象 | 排查 |
|------|------|
| 下单报无法连接 | 9090 没起，或 `mock-wechat-base-url` 写错 |
| 确认后外卖没变已支付 | 密钥不一致；`merchant-notify-url` 假微信访问不到 8080；notify 未进白名单 |
| 验签失败 | `amount.toPlainString()` 与订单金额不一致；secret 不同 |
| 409 ORDER_PAID | 同一 `out_trade_no` 已在假微信 SUCCESS，换新订单号或重启假微信清内存 |

---

## 10. 自测清单

- [ ] `take-out-pay` 编译通过，无 `MockWeChatPayClient` 引用  
- [ ] `PUT mockPay/{id}` 只调统一下单，不立刻改支付状态  
- [ ] 假微信内存单为 `NOTPAY` 且有 `prepay_id`  
- [ ] `POST /mock/pay/confirm` 后外卖订单变为已支付  
- [ ] 重复 confirm：假微信不二次通知；外卖仍幂等成功  
- [ ] 假微信关掉时，`mockPay` 返回明确错误  

---

## 11. 和旧教程的关系

| 文档 | 内容 |
|------|------|
| `2026-08-09-mock-wechat-pay-notify.md` | 进程内 Async + HMAC notify（已完成的基础） |
| `take-out-mock-wechat/API.md` | 假微信对外契约 |
| **本文** | 商户侧 Client 改为 HTTP，回调仍用 08-09 的 `handlePayNotify` |

知识点递进：**先会验签入账 → 再拆渠道进程 → 最后才考虑真微信 RSA**。

---

## 附录 A：可选 — 管理端一键「确认支付」（调假微信）

若不想每次开 Postman，可在 pay 客户端加方法，再在 `OrderController` 加教学接口（例如 `PUT /admin/order/mockPay/{id}/confirmChannel`）。**最小实现：**

```java
// MockWechatHttpClient 内追加
public void confirmPay(String outTradeNo) {
    restClient.post()
            .uri("/mock/pay/confirm")
            .contentType(MediaType.APPLICATION_JSON)
            .body(java.util.Map.of("out_trade_no", outTradeNo))
            .retrieve()
            .toBodilessEntity();
}
```

网关中可在 `requestPay` **之后**不自动 confirm（更接近真实「用户还要付钱」）；单独接口再调 `confirmPay(order.getNumber())`。是否自动 confirm 由你产品习惯决定——**真实支付绝不会在统一下单后由商户直接「confirm」官方接口**；本沙箱的 confirm 是教学替身。

---

## 附录 B：改造后时序（对照 API.md §6）

```text
管理端 8080                    假微信 9090
   |-- PUT mockPay/{id} ---------->|
   |   createNative (HTTP)         | 存 NOTPAY + prepay_id
   |<-- 仍待付款 -------------------|
   |                               |
   |  (人) POST /mock/pay/confirm  |
   |------------------------------>| SUCCESS
   |<-- POST /mockPay/notify ------| HMAC 五字段
   |   handlePayNotify CAS         |
   |   待接单+已支付                |
```

按本文改完后，你就具备「商户 HTTP 调渠道 + 渠道 HTTP 回调商户」的完整闭环，后续换真微信时主要替换 `MockWechatHttpClient` 内部实现与验签算法即可。
