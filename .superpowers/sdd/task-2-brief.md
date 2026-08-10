### Task 2: 棰嗗煙妯″瀷銆佸唴瀛樹粨搴撱€丠MAC 绛惧悕

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
  - `TradeStore.save(Trade)`, `findByOutTradeNo(String)`, `findByPrepayId(String)` 鈫?`Optional<Trade>`
  - `HmacNotifySignUtil.sign(orderNumber, amount, timestamp, nonce, secret)` 鈫?`String`
  - `HmacNotifySignUtil.verify(...)` 鈫?`boolean`

- [ ] **Step 1: 鍏堝啓绛惧悕澶辫触娴嬭瘯锛堢被灏氫笉瀛樺湪锛?*

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

        // 涓?take-out-pay HmacPaySignUtil 鍚岀畻娉曪紱鍥哄畾鍚戦噺渚夸簬鍥炲綊
        String again = HmacNotifySignUtil.sign(orderNumber, amount, timestamp, nonce, secret);
        assertEquals(sign, again);
        assertTrue(HmacNotifySignUtil.verify(orderNumber, amount, timestamp, nonce, secret, sign));
    }

    @Test
    void amountPlain_shouldUseToPlainString() {
        // 10.0 涓?10.00 鐨?plain 涓嶅悓浼氬鑷寸鍚嶄笉涓€鑷达紱鍟嗘埛渚х敤璁㈠崟 amount
        String s1 = HmacNotifySignUtil.sign("N", new BigDecimal("10.0"), 1L, "n", "k");
        String s2 = HmacNotifySignUtil.sign("N", new BigDecimal("10.00"), 1L, "n", "k");
        // 鏂囨。绾﹀畾锛氳皟鐢ㄦ柟浼犲叆涓庡晢鎴疯鍗曚竴鑷寸殑 BigDecimal锛涙祴璇曚粎閿佸畾 toPlainString 琛屼负
        org.junit.jupiter.api.Assertions.assertNotEquals(
                new BigDecimal("10.0").toPlainString(),
                new BigDecimal("10.00").toPlainString());
        org.junit.jupiter.api.Assertions.assertNotEquals(s1, s2);
    }
}
```

- [ ] **Step 2: 璺戞祴纭澶辫触**

Run: `mvn -pl take-out-mock-wechat -Dtest=HmacNotifySignUtilTest test`  
Expected: 缂栬瘧澶辫触鎴栨祴璇曞け璐ワ紙绫讳笉瀛樺湪锛?

- [ ] **Step 3: 瀹炵幇 Properties / Domain / Store / Sign**

`MockWechatProperties.java`:

```java
package com.sky.takeout.mockwechat.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Data;

@Data
@ConfigurationProperties(prefix = "mock-wechat")
public class MockWechatProperties {
    /** 涓?take-out pay.mock-secret 淇濇寔涓€鑷?*/
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
    /** 鏄惁宸插悜鍟嗘埛鍙戝嚭杩囨垚鍔熼€氱煡 */
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

`HmacNotifySignUtil.java`锛堝榻?pay 妯″潡绠楁硶锛夛細

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
 * 涓?take-out-pay HmacPaySignUtil 鍚岀畻娉曪紙鏁欏鍓湰锛岄伩鍏嶄緷璧?pay 妯″潡锛夈€?
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
            throw new IllegalStateException("HMAC 绛惧悕澶辫触", e);
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

- [ ] **Step 4: 鍐嶈窇绛惧悕娴嬭瘯**

Run: `mvn -pl take-out-mock-wechat -Dtest=HmacNotifySignUtilTest test`  
Expected: `BUILD SUCCESS`锛屾祴璇曢€氳繃

- [ ] **Step 5: Commit**

```bash
git add take-out-mock-wechat/src/main/java take-out-mock-wechat/src/test/java
git commit -m "feat(mock-wechat): add trade store and HMAC notify sign util"
```

---

