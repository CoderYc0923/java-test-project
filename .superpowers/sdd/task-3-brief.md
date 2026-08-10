### Task 3: 缁熶竴涓嬪崟 + 鏌ュ崟 API

**Files:**
- Create: `.../api/dto/NativePayRequest.java`
- Create: `.../api/dto/TransactionResponse.java`
- Create: `.../api/dto/ErrorBody.java`
- Create: `.../api/MockWechatException.java`锛堝彲閫夋惡甯?HTTP 鐘舵€侊級
- Create: `.../api/MockWechatExceptionHandler.java`
- Create: `.../service/TradeService.java`锛坈reate + query锛?
- Create: `.../api/TransactionController.java`
- Test: `.../api/TransactionControllerTest.java`锛坄@SpringBootTest` + `MockMvc`锛?

**Interfaces:**
- Consumes: `TradeStore`, `Trade`
- Produces:
  - `TradeService.createNative(NativePayRequest)` 鈫?`TransactionResponse`
  - `TradeService.queryByOutTradeNo(String)` 鈫?`TransactionResponse`
  - `POST /v3/pay/transactions/native`
  - `GET /v3/pay/transactions/out-trade-no/{out_trade_no}`

- [ ] **Step 1: 鍐?MockMvc 澶辫触鐢ㄤ緥锛堟帴鍙ｆ湭瀹炵幇锛?*

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
                  "description": "娴嬭瘯",
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

娉ㄦ剰锛氳嫢 Boot 4 鐨?`@AutoConfigureMockMvc` 鍖呭悕涓嶅悓锛屼互椤圭洰鍐?`EmployeeControllerTest` / 渚濊禆涓哄噯璋冩暣 import锛坄org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc` 鎴?`org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc`锛夈€?

- [ ] **Step 2: 璺戞祴纭澶辫触**

Run: `mvn -pl take-out-mock-wechat -Dtest=TransactionControllerTest test`  
Expected: FAIL锛?04 mapping锛?

- [ ] **Step 3: 瀹炵幇 DTO銆佸紓甯搞€丼ervice銆丆ontroller**

璇锋眰/鍝嶅簲瀛楁浣跨敤 Jackson 榛樿椹煎嘲锛汮SON 鍙敤 `@JsonProperty("out_trade_no")` 鑻ヨ鍧氭寔铔囧舰锛?*鎺ㄨ崘铔囧舰瀵归綈 V3**锛夛細

```java
// NativePayRequest 鍏抽敭瀛楁绀轰緥
@NotBlank
@JsonProperty("out_trade_no")
private String outTradeNo;

@JsonProperty("notify_url")
@NotBlank
private String notifyUrl;

@NotNull
private BigDecimal amount;
```

鍝嶅簲鍚屾牱 `@JsonProperty("prepay_id")`銆乣trade_state`銆乣out_trade_no`銆?

`TradeService.createNative` 閫昏緫锛?

1. 鑻ュ凡瀛樺湪涓?`SUCCESS` 鈫?throw 409  
2. 鑻ュ凡瀛樺湪涓?`NOTPAY` 鈫?杩斿洖鍘熶氦鏄撳搷搴旓紙骞傜瓑锛? 
3. 鍚﹀垯 `prepayId = "wx_prepay_" + UUID`锛岀姸鎬?`NOTPAY`锛宍currency` 榛樿 `CNY`锛宍save`

`queryByOutTradeNo`锛氭壘涓嶅埌 鈫?404 寮傚父

`MockWechatExceptionHandler`锛氭槧灏勫埌 `ErrorBody(code, message)` + 瀵瑰簲 HTTP 鐘舵€併€?

- [ ] **Step 4: 璺戞祴閫氳繃**

Run: `mvn -pl take-out-mock-wechat -Dtest=TransactionControllerTest test`  
Expected: PASS

鍙︽祴锛氶噸澶?native 鍚屼竴 `out_trade_no` 杩斿洖鍚屼竴 `prepay_id`锛堝彲鍔犳柇瑷€鎴栨墜宸?Postman锛夈€?

- [ ] **Step 5: Commit**

```bash
git add take-out-mock-wechat
git commit -m "feat(mock-wechat): add native pay and query APIs"
```

---

