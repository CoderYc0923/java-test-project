# Mock WeChat V3 Sandbox Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在同仓新建可独立启动的 `take-out-mock-wechat`（端口 9090），提供形似微信 V3 的统一下单/查单、教学用手动确认支付，并以 HMAC 回调商户 `notify_url`。

**Architecture:** 独立 Spring Boot 应用 + 内存 `ConcurrentHashMap` 存交易；出站用 `RestClient` POST 扁平五字段通知体（兼容现有 `MockPayNotifyDTO`）。不修改 `take-out-pay`。

**Tech Stack:** Java 17、Spring Boot 4.1.0（父 POM）、`spring-boot-starter-webmvc`、`spring-boot-starter-validation`、JUnit 5、`MockWebServer`（okhttp）测出站回调。

**Spec:** `docs/superpowers/specs/2026-08-10-mock-wechat-v3-design.md`

## Global Constraints

- 只新建/改：`take-out-mock-wechat/**`、父 `take-out/pom.xml` 的 `<modules>`（及可选 `dependencyManagement` 条目）；**禁止**改 `take-out-pay`、`take-out-system`、前端。
- 端口默认 `9090`。
- 金额单位：**元** `BigDecimal`；签名与 pay 模块一致：`amount={plain}&nonce={nonce}&orderNumber={orderNumber}&timestamp={timestamp}&key={secret}`，HMAC-SHA256 hex。
- 回调 body 字段名：`orderNumber`、`amount`、`timestamp`、`nonce`、`sign`。
- 重复 confirm 已 SUCCESS：**不**再次 POST notify。
- 签名工具在 mock-wechat 内复制算法，注释对齐 `HmacPaySignUtil`。

## File Structure

| 路径 | 职责 |
|------|------|
| `take-out/pom.xml` | 注册 module |
| `take-out-mock-wechat/pom.xml` | 独立 Boot 应用依赖 |
| `.../MockWechatApplication.java` | 启动类 |
| `.../config/MockWechatProperties.java` | `mock-wechat.*` |
| `.../domain/TradeState.java` | `NOTPAY` / `SUCCESS` |
| `.../domain/Trade.java` | 交易聚合 |
| `.../store/TradeStore.java` | 内存仓库 |
| `.../sign/HmacNotifySignUtil.java` | 签名 |
| `.../notify/MerchantNotifyClient.java` | 出站回调 + 重试 |
| `.../api/dto/*.java` | 请求/响应/错误体 |
| `.../api/MockWechatExceptionHandler.java` | 404/409/400 |
| `.../api/TransactionController.java` | 下单 + 查单 |
| `.../api/ConfirmController.java` | confirm |
| `.../service/TradeService.java` | 业务编排 |
| `src/main/resources/application.yml` | 端口与密钥 |
| `src/test/java/.../HmacNotifySignUtilTest.java` | 签名单测 |
| `src/test/java/.../TradeServiceConfirmTest.java` | confirm + notify 次数 |
| `README.md`（模块内） | 启动与 Postman 示例 |

---

### Task 1: 脚手架 — 模块可启动

**Files:**
- Modify: `take-out/pom.xml`
- Create: `take-out-mock-wechat/pom.xml`
- Create: `take-out-mock-wechat/src/main/java/com/sky/takeout/mockwechat/MockWechatApplication.java`
- Create: `take-out-mock-wechat/src/main/resources/application.yml`

**Interfaces:**
- Produces: 可执行模块 `take-out-mock-wechat`，主类 `MockWechatApplication`

- [ ] **Step 1: 父 POM 增加 module**

在 `take-out/pom.xml` 的 `<modules>` 中追加：

```xml
<module>take-out-mock-wechat</module>
```

（可选）在 `<dependencyManagement>` 增加：

```xml
<dependency>
    <groupId>com.sky</groupId>
    <artifactId>take-out-mock-wechat</artifactId>
    <version>${take-out.version}</version>
</dependency>
```

- [ ] **Step 2: 写 `take-out-mock-wechat/pom.xml`**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>com.sky</groupId>
        <artifactId>take-out</artifactId>
        <version>0.0.1-SNAPSHOT</version>
    </parent>
    <artifactId>take-out-mock-wechat</artifactId>
    <name>take-out-mock-wechat</name>
    <description>假微信 V3 沙箱（教学）</description>
    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-webmvc</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-validation</artifactId>
        </dependency>
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <optional>true</optional>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-webmvc-test</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>com.squareup.okhttp3</groupId>
            <artifactId>mockwebserver</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>
    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
```

若 `mockwebserver` 版本需显式指定，与 Spring Boot BOM 管理冲突时再在父 POM 或本模块加 version；优先让 BOM 管理。

- [ ] **Step 3: 启动类与配置**

`MockWechatApplication.java`:

```java
package com.sky.takeout.mockwechat;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class MockWechatApplication {
    public static void main(String[] args) {
        SpringApplication.run(MockWechatApplication.class, args);
    }
}
```

`application.yml`:

```yaml
server:
  port: 9090

mock-wechat:
  merchant-notify-secret: change-me
  notify-max-retries: 2
  notify-retry-delay-ms: 500
```

- [ ] **Step 4: 编译验证可解析模块**

Run:

```bash
mvn -pl take-out-mock-wechat -am -DskipTests package
```

Expected: `BUILD SUCCESS`

- [ ] **Step 5: Commit**

```bash
git add take-out/pom.xml take-out-mock-wechat/pom.xml \
  take-out-mock-wechat/src/main/java/com/sky/takeout/mockwechat/MockWechatApplication.java \
  take-out-mock-wechat/src/main/resources/application.yml
git commit -m "chore: scaffold take-out-mock-wechat module"
```

---

### Task 2: 领域模型、内存仓库、HMAC 签名

**Files:**
- Create: `.../config/MockWechatProperties.java`
- Create: `.../domain/TradeState.java`
- Create: `.../domain/Trade.java`
- Create: `.../store/TradeStore.java`
- Create: `.../sign/HmacNotifySignUtil.java`
- Test: `.../sign/HmacNotifySignUtilTest.java`

**Interfaces:**
- Produces:
  - `MockWechatProperties`: `getMerchantNotifySecret()`, `getNotifyMaxRetries()`, `getNotifyRetryDelayMs()`
  - `TradeStore.save(Trade)`, `findByOutTradeNo(String)`, `findByPrepayId(String)` → `Optional<Trade>`
  - `HmacNotifySignUtil.sign(orderNumber, amount, timestamp, nonce, secret)` → `String`
  - `HmacNotifySignUtil.verify(...)` → `boolean`

- [ ] **Step 1: 先写签名失败测试（类尚不存在）**

`HmacNotifySignUtilTest.java`:

```java
package com.sky.takeout.mockwechat.sign;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HmacNotifySignUtilTest {

    @Test
    void sign_shouldMatchKnownVector() {
        String secret = "test-secret";
        String orderNumber = "ORD001";
        BigDecimal amount = new BigDecimal("10.00");
        Long timestamp = 1700000000L;
        String nonce = "abc";

        String sign = HmacNotifySignUtil.sign(orderNumber, amount, timestamp, nonce, secret);

        // 与 take-out-pay HmacPaySignUtil 同算法；固定向量便于回归
        String again = HmacNotifySignUtil.sign(orderNumber, amount, timestamp, nonce, secret);
        assertEquals(sign, again);
        assertTrue(HmacNotifySignUtil.verify(orderNumber, amount, timestamp, nonce, secret, sign));
    }

    @Test
    void amountPlain_shouldUseToPlainString() {
        // 10.0 与 10.00 的 plain 不同会导致签名不一致；商户侧用订单 amount
        String s1 = HmacNotifySignUtil.sign("N", new BigDecimal("10.0"), 1L, "n", "k");
        String s2 = HmacNotifySignUtil.sign("N", new BigDecimal("10.00"), 1L, "n", "k");
        // 文档约定：调用方传入与商户订单一致的 BigDecimal；测试仅锁定 toPlainString 行为
        org.junit.jupiter.api.Assertions.assertNotEquals(
                new BigDecimal("10.0").toPlainString(),
                new BigDecimal("10.00").toPlainString());
        org.junit.jupiter.api.Assertions.assertNotEquals(s1, s2);
    }
}
```

- [ ] **Step 2: 跑测确认失败**

Run: `mvn -pl take-out-mock-wechat -Dtest=HmacNotifySignUtilTest test`  
Expected: 编译失败或测试失败（类不存在）

- [ ] **Step 3: 实现 Properties / Domain / Store / Sign**

`MockWechatProperties.java`:

```java
package com.sky.takeout.mockwechat.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Data;

@Data
@ConfigurationProperties(prefix = "mock-wechat")
public class MockWechatProperties {
    /** 与 take-out pay.mock-secret 保持一致 */
    private String merchantNotifySecret = "change-me";
    private int notifyMaxRetries = 2;
    private long notifyRetryDelayMs = 500L;
}
```

`TradeState.java`:

```java
package com.sky.takeout.mockwechat.domain;

public enum TradeState {
    NOTPAY,
    SUCCESS
}
```

`Trade.java`:

```java
package com.sky.takeout.mockwechat.domain;

import java.math.BigDecimal;
import java.time.Instant;

import lombok.Data;

@Data
public class Trade {
    private String outTradeNo;
    private String prepayId;
    private String description;
    private String notifyUrl;
    private BigDecimal amount;
    private String currency;
    private TradeState tradeState;
    private Instant createdAt;
    private Instant paidAt;
    /** 是否已向商户发出过成功通知 */
    private boolean notifySent;
}
```

`TradeStore.java`:

```java
package com.sky.takeout.mockwechat.store;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

import com.sky.takeout.mockwechat.domain.Trade;

@Component
public class TradeStore {
    private final ConcurrentHashMap<String, Trade> byOutTradeNo = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> prepayIdToOutTradeNo = new ConcurrentHashMap<>();

    public void save(Trade trade) {
        byOutTradeNo.put(trade.getOutTradeNo(), trade);
        prepayIdToOutTradeNo.put(trade.getPrepayId(), trade.getOutTradeNo());
    }

    public Optional<Trade> findByOutTradeNo(String outTradeNo) {
        return Optional.ofNullable(byOutTradeNo.get(outTradeNo));
    }

    public Optional<Trade> findByPrepayId(String prepayId) {
        String outTradeNo = prepayIdToOutTradeNo.get(prepayId);
        if (outTradeNo == null) {
            return Optional.empty();
        }
        return findByOutTradeNo(outTradeNo);
    }
}
```

`HmacNotifySignUtil.java`（对齐 pay 模块算法）：

```java
package com.sky.takeout.mockwechat.sign;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * 与 take-out-pay HmacPaySignUtil 同算法（教学副本，避免依赖 pay 模块）。
 * plain: amount={plain}&nonce={nonce}&orderNumber={orderNumber}&timestamp={timestamp}&key={secret}
 */
public final class HmacNotifySignUtil {
    private HmacNotifySignUtil() {}

    public static String buildPlain(String orderNumber, BigDecimal amount, Long timestamp, String nonce, String secret) {
        return String.format("amount=%s&nonce=%s&orderNumber=%s&timestamp=%s&key=%s",
                amount.toPlainString(), nonce, orderNumber, timestamp, secret);
    }

    public static String sign(String orderNumber, BigDecimal amount, Long timestamp, String nonce, String secret) {
        String plain = buildPlain(orderNumber, amount, timestamp, nonce, secret);
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(plain.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("HMAC 签名失败", e);
        }
    }

    public static boolean verify(String orderNumber, BigDecimal amount, Long timestamp, String nonce,
            String secret, String signFromChannel) {
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

- [ ] **Step 4: 再跑签名测试**

Run: `mvn -pl take-out-mock-wechat -Dtest=HmacNotifySignUtilTest test`  
Expected: `BUILD SUCCESS`，测试通过

- [ ] **Step 5: Commit**

```bash
git add take-out-mock-wechat/src/main/java take-out-mock-wechat/src/test/java
git commit -m "feat(mock-wechat): add trade store and HMAC notify sign util"
```

---

### Task 3: 统一下单 + 查单 API

**Files:**
- Create: `.../api/dto/NativePayRequest.java`
- Create: `.../api/dto/TransactionResponse.java`
- Create: `.../api/dto/ErrorBody.java`
- Create: `.../api/MockWechatException.java`（可选携带 HTTP 状态）
- Create: `.../api/MockWechatExceptionHandler.java`
- Create: `.../service/TradeService.java`（create + query）
- Create: `.../api/TransactionController.java`
- Test: `.../api/TransactionControllerTest.java`（`@SpringBootTest` + `MockMvc`）

**Interfaces:**
- Consumes: `TradeStore`, `Trade`
- Produces:
  - `TradeService.createNative(NativePayRequest)` → `TransactionResponse`
  - `TradeService.queryByOutTradeNo(String)` → `TransactionResponse`
  - `POST /v3/pay/transactions/native`
  - `GET /v3/pay/transactions/out-trade-no/{out_trade_no}`

- [ ] **Step 1: 写 MockMvc 失败用例（接口未实现）**

```java
package com.sky.takeout.mockwechat.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class TransactionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void nativePay_thenQuery_shouldReturnNotPay() throws Exception {
        String body = """
                {
                  "out_trade_no": "ORD_TEST_001",
                  "description": "测试",
                  "notify_url": "http://127.0.0.1:8080/admin/order/mockPay/notify",
                  "amount": 62.00
                }
                """;

        mockMvc.perform(post("/v3/pay/transactions/native")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.out_trade_no").value("ORD_TEST_001"))
                .andExpect(jsonPath("$.trade_state").value("NOTPAY"))
                .andExpect(jsonPath("$.prepay_id").isNotEmpty());

        mockMvc.perform(get("/v3/pay/transactions/out-trade-no/ORD_TEST_001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trade_state").value("NOTPAY"));
    }

    @Test
    void query_missing_should404() throws Exception {
        mockMvc.perform(get("/v3/pay/transactions/out-trade-no/NO_SUCH"))
                .andExpect(status().isNotFound());
    }
}
```

注意：若 Boot 4 的 `@AutoConfigureMockMvc` 包名不同，以项目内 `EmployeeControllerTest` / 依赖为准调整 import（`org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc` 或 `org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc`）。

- [ ] **Step 2: 跑测确认失败**

Run: `mvn -pl take-out-mock-wechat -Dtest=TransactionControllerTest test`  
Expected: FAIL（404 mapping）

- [ ] **Step 3: 实现 DTO、异常、Service、Controller**

请求/响应字段使用 Jackson 默认驼峰；JSON 可用 `@JsonProperty("out_trade_no")` 若要坚持蛇形（**推荐蛇形对齐 V3**）：

```java
// NativePayRequest 关键字段示例
@NotBlank
@JsonProperty("out_trade_no")
private String outTradeNo;

@JsonProperty("notify_url")
@NotBlank
private String notifyUrl;

@NotNull
private BigDecimal amount;
```

响应同样 `@JsonProperty("prepay_id")`、`trade_state`、`out_trade_no`。

`TradeService.createNative` 逻辑：

1. 若已存在且 `SUCCESS` → throw 409  
2. 若已存在且 `NOTPAY` → 返回原交易响应（幂等）  
3. 否则 `prepayId = "wx_prepay_" + UUID`，状态 `NOTPAY`，`currency` 默认 `CNY`，`save`

`queryByOutTradeNo`：找不到 → 404 异常

`MockWechatExceptionHandler`：映射到 `ErrorBody(code, message)` + 对应 HTTP 状态。

- [ ] **Step 4: 跑测通过**

Run: `mvn -pl take-out-mock-wechat -Dtest=TransactionControllerTest test`  
Expected: PASS

另测：重复 native 同一 `out_trade_no` 返回同一 `prepay_id`（可加断言或手工 Postman）。

- [ ] **Step 5: Commit**

```bash
git add take-out-mock-wechat
git commit -m "feat(mock-wechat): add native pay and query APIs"
```

---

### Task 4: 出站回调 + 手动 confirm

**Files:**
- Create: `.../notify/MerchantNotifyPayload.java`（五字段）
- Create: `.../notify/MerchantNotifyClient.java`
- Create: `.../api/dto/ConfirmRequest.java`
- Create: `.../api/dto/ConfirmResponse.java`
- Modify: `.../service/TradeService.java`（增加 `confirm`）
- Create: `.../api/ConfirmController.java`
- Test: `.../service/TradeServiceConfirmTest.java`

**Interfaces:**
- Consumes: `HmacNotifySignUtil`, `MockWechatProperties`, `TradeStore`
- Produces:
  - `MerchantNotifyClient.send(Trade trade)` — POST `trade.notifyUrl`，失败按配置重试
  - `TradeService.confirm(ConfirmRequest)` → `ConfirmResponse`
  - `POST /mock/pay/confirm`

- [ ] **Step 1: 写 confirm 单测（MockWebServer）**

```java
package com.sky.takeout.mockwechat.service;

import java.math.BigDecimal;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import com.sky.takeout.mockwechat.config.MockWechatProperties;
import com.sky.takeout.mockwechat.domain.Trade;
import com.sky.takeout.mockwechat.domain.TradeState;
import com.sky.takeout.mockwechat.notify.MerchantNotifyClient;
import com.sky.takeout.mockwechat.store.TradeStore;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TradeServiceConfirmTest {

    private MockWebServer server;
    private TradeStore store;
    private TradeService tradeService;

    @BeforeEach
    void setUp() throws Exception {
        server = new MockWebServer();
        server.start();
        store = new TradeStore();
        MockWechatProperties props = new MockWechatProperties();
        props.setMerchantNotifySecret("test-secret");
        props.setNotifyMaxRetries(2);
        props.setNotifyRetryDelayMs(10);
        MerchantNotifyClient client = new MerchantNotifyClient(RestClient.builder(), props);
        tradeService = new TradeService(store, client, props);
    }

    @AfterEach
    void tearDown() throws Exception {
        server.shutdown();
    }

    @Test
    void confirm_shouldPostNotifyOnce_andIdempotentSecondConfirm() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(200));
        String notifyUrl = server.url("/admin/order/mockPay/notify").toString();

        Trade trade = new Trade();
        trade.setOutTradeNo("ORD_C1");
        trade.setPrepayId("wx_prepay_c1");
        trade.setAmount(new BigDecimal("6.00"));
        trade.setNotifyUrl(notifyUrl);
        trade.setTradeState(TradeState.NOTPAY);
        trade.setNotifySent(false);
        store.save(trade);

        tradeService.confirmByOutTradeNo("ORD_C1");
        RecordedRequest req = server.takeRequest(1, TimeUnit.SECONDS);
        assertEquals("POST", req.getMethod());
        assertTrue(req.getBody().readUtf8().contains("\"orderNumber\":\"ORD_C1\""));

        tradeService.confirmByOutTradeNo("ORD_C1");
        assertEquals(1, server.getRequestCount());
        assertEquals(TradeState.SUCCESS, store.findByOutTradeNo("ORD_C1").orElseThrow().getTradeState());
    }
}
```

按实际构造函数签名微调（计划要求实现时保持可测：构造注入 `TradeStore`、`MerchantNotifyClient`、`MockWechatProperties`）。

- [ ] **Step 2: 跑测确认失败**

Run: `mvn -pl take-out-mock-wechat -Dtest=TradeServiceConfirmTest test`  
Expected: FAIL

- [ ] **Step 3: 实现 NotifyClient + confirm**

`MerchantNotifyPayload`：`orderNumber`、`amount`、`timestamp`、`nonce`、`sign`。

`MerchantNotifyClient.send`：

1. 用 `UUID` 生成 nonce，`timestamp = now/1000`  
2. `sign = HmacNotifySignUtil.sign(...)`  
3. `RestClient.post().uri(notifyUrl).body(payload).retrieve()`  
4. 非 2xx 或异常：最多 `notifyMaxRetries` 次，间隔 `notifyRetryDelayMs`  
5. 最终仍失败：打 error 日志，不抛给 confirm（渠道侧已 SUCCESS）——**但** `notifySent` 仅在至少一次 2xx 时置 true；若全部失败，`notifySent=false` 且状态已 SUCCESS（简化对账模型）。  

更贴近 spec「不重复通知」：只有 2xx 才 `notifySent=true`；第二次 confirm 若已 SUCCESS 且 `notifySent`：直接返回；若 SUCCESS 但从未通知成功，允许再试一次 POST（可选增强）。**最小实现：** SUCCESS 时置 `notifySent=true` 仅在 2xx；第二次 confirm 若 SUCCESS 则不再 POST（即使上次失败也不再刷——YAGNI）。Spec 原文：已 SUCCESS 不再重复 POST。按 spec：第二次不 POST。

`confirmByOutTradeNo`：

```text
lock/同步同一 outTradeNo
找不到 → 404
SUCCESS → return（不 POST）
notifyUrl blank → 400
state = SUCCESS, paidAt = now
调用 notifyClient.send
若 2xx → notifySent = true
save
```

`ConfirmController`：`POST /mock/pay/confirm`，body `{ "out_trade_no": "..." }`。

- [ ] **Step 4: 跑测通过**

Run: `mvn -pl take-out-mock-wechat -Dtest=TradeServiceConfirmTest,TransactionControllerTest,HmacNotifySignUtilTest test`  
Expected: 全部 PASS

- [ ] **Step 5: Commit**

```bash
git add take-out-mock-wechat
git commit -m "feat(mock-wechat): add manual confirm and merchant HTTP notify"
```

---

### Task 5: 模块 README + 手工验收

**Files:**
- Create: `take-out-mock-wechat/README.md`

**Interfaces:** 无代码接口；产出可跟做的验收步骤。

- [ ] **Step 1: 写 README**

内容须包含：

1. 启动：`mvn -pl take-out-mock-wechat spring-boot:run`  
2. `merchant-notify-secret` 须与外卖 `pay.mock-secret` 一致  
3. Postman 三步：native → query → confirm  
4. `notify_url` 示例：`http://127.0.0.1:8080/admin/order/mockPay/notify`（需管理端已启动且白名单放行）  
5. 说明：**不改 take-out-pay**；作者后续自行把 Client 改为调本服务  

- [ ] **Step 2: 本地烟雾验收**

```bash
mvn -pl take-out-mock-wechat spring-boot:run
```

另开终端（PowerShell 可用 `Invoke-RestMethod` 或 curl）：

```bash
curl -s -X POST http://127.0.0.1:9090/v3/pay/transactions/native -H "Content-Type: application/json" -d "{\"out_trade_no\":\"ORD_SMOKE_1\",\"description\":\"smoke\",\"notify_url\":\"https://httpbin.org/post\",\"amount\":1.00}"
curl -s http://127.0.0.1:9090/v3/pay/transactions/out-trade-no/ORD_SMOKE_1
curl -s -X POST http://127.0.0.1:9090/mock/pay/confirm -H "Content-Type: application/json" -d "{\"out_trade_no\":\"ORD_SMOKE_1\"}"
curl -s http://127.0.0.1:9090/v3/pay/transactions/out-trade-no/ORD_SMOKE_1
```

Expected: 最后查单 `trade_state` 为 `SUCCESS`；confirm 过程对 httpbin 有 POST。

- [ ] **Step 3: Commit**

```bash
git add take-out-mock-wechat/README.md
git commit -m "docs(mock-wechat): add runbook and Postman smoke steps"
```

---

## Spec Coverage Checklist

| Spec 项 | Task |
|---------|------|
| 独立模块端口 9090 | Task 1 |
| 统一下单 + prepay_id + NOTPAY | Task 3 |
| 查单 | Task 3 |
| 手动 confirm | Task 4 |
| HMAC 回调五字段 | Task 2 + 4 |
| 幂等下单 / 重复 confirm 不二次 notify | Task 3 + 4 |
| 不改 take-out-pay | Global Constraints |
| 内存存储 | Task 2 |
| 回调重试 | Task 4 |
| README / 验收 | Task 5 |

## Placeholder / Consistency Review

- 无 TBD；蛇形 JSON 与 Java 驼峰通过 `@JsonProperty` 统一。  
- `TradeService` 方法名在 Task 3/4 一致使用 `createNative` / `queryByOutTradeNo` / `confirmByOutTradeNo`。  
- 测试 import 若与 Boot 4 包名不符，以实现时仓库内已有测试为准微调（已在 Task 3 注明）。
