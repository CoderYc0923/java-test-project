### Task 4: 鍑虹珯鍥炶皟 + 鎵嬪姩 confirm

**Files:**
- Create: `.../notify/MerchantNotifyPayload.java`锛堜簲瀛楁锛?
- Create: `.../notify/MerchantNotifyClient.java`
- Create: `.../api/dto/ConfirmRequest.java`
- Create: `.../api/dto/ConfirmResponse.java`
- Modify: `.../service/TradeService.java`锛堝鍔?`confirm`锛?
- Create: `.../api/ConfirmController.java`
- Test: `.../service/TradeServiceConfirmTest.java`

**Interfaces:**
- Consumes: `HmacNotifySignUtil`, `MockWechatProperties`, `TradeStore`
- Produces:
  - `MerchantNotifyClient.send(Trade trade)` 鈥?POST `trade.notifyUrl`锛屽け璐ユ寜閰嶇疆閲嶈瘯
  - `TradeService.confirm(ConfirmRequest)` 鈫?`ConfirmResponse`
  - `POST /mock/pay/confirm`

- [ ] **Step 1: 鍐?confirm 鍗曟祴锛圡ockWebServer锛?*

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

鎸夊疄闄呮瀯閫犲嚱鏁扮鍚嶅井璋冿紙璁″垝瑕佹眰瀹炵幇鏃朵繚鎸佸彲娴嬶細鏋勯€犳敞鍏?`TradeStore`銆乣MerchantNotifyClient`銆乣MockWechatProperties`锛夈€?

- [ ] **Step 2: 璺戞祴纭澶辫触**

Run: `mvn -pl take-out-mock-wechat -Dtest=TradeServiceConfirmTest test`  
Expected: FAIL

- [ ] **Step 3: 瀹炵幇 NotifyClient + confirm**

`MerchantNotifyPayload`锛歚orderNumber`銆乣amount`銆乣timestamp`銆乣nonce`銆乣sign`銆?

`MerchantNotifyClient.send`锛?

1. 鐢?`UUID` 鐢熸垚 nonce锛宍timestamp = now/1000`  
2. `sign = HmacNotifySignUtil.sign(...)`  
3. `RestClient.post().uri(notifyUrl).body(payload).retrieve()`  
4. 闈?2xx 鎴栧紓甯革細鏈€澶?`notifyMaxRetries` 娆★紝闂撮殧 `notifyRetryDelayMs`  
5. 鏈€缁堜粛澶辫触锛氭墦 error 鏃ュ織锛屼笉鎶涚粰 confirm锛堟笭閬撲晶宸?SUCCESS锛夆€斺€?*浣?* `notifySent` 浠呭湪鑷冲皯涓€娆?2xx 鏃剁疆 true锛涜嫢鍏ㄩ儴澶辫触锛宍notifySent=false` 涓旂姸鎬佸凡 SUCCESS锛堢畝鍖栧璐︽ā鍨嬶級銆? 

鏇磋创杩?spec銆屼笉閲嶅閫氱煡銆嶏細鍙湁 2xx 鎵?`notifySent=true`锛涚浜屾 confirm 鑻ュ凡 SUCCESS 涓?`notifySent`锛氱洿鎺ヨ繑鍥烇紱鑻?SUCCESS 浣嗕粠鏈€氱煡鎴愬姛锛屽厑璁稿啀璇曚竴娆?POST锛堝彲閫夊寮猴級銆?*鏈€灏忓疄鐜帮細** SUCCESS 鏃剁疆 `notifySent=true` 浠呭湪 2xx锛涚浜屾 confirm 鑻?SUCCESS 鍒欎笉鍐?POST锛堝嵆浣夸笂娆″け璐ヤ篃涓嶅啀鍒封€斺€擸AGNI锛夈€係pec 鍘熸枃锛氬凡 SUCCESS 涓嶅啀閲嶅 POST銆傛寜 spec锛氱浜屾涓?POST銆?

`confirmByOutTradeNo`锛?

```text
lock/鍚屾鍚屼竴 outTradeNo
鎵句笉鍒?鈫?404
SUCCESS 鈫?return锛堜笉 POST锛?
notifyUrl blank 鈫?400
state = SUCCESS, paidAt = now
璋冪敤 notifyClient.send
鑻?2xx 鈫?notifySent = true
save
```

`ConfirmController`锛歚POST /mock/pay/confirm`锛宐ody `{ "out_trade_no": "..." }`銆?

- [ ] **Step 4: 璺戞祴閫氳繃**

Run: `mvn -pl take-out-mock-wechat -Dtest=TradeServiceConfirmTest,TransactionControllerTest,HmacNotifySignUtilTest test`  
Expected: 鍏ㄩ儴 PASS

- [ ] **Step 5: Commit**

```bash
git add take-out-mock-wechat
git commit -m "feat(mock-wechat): add manual confirm and merchant HTTP notify"
```

---

