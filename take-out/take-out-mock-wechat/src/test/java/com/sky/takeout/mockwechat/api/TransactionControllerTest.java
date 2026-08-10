package com.sky.takeout.mockwechat.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

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

    @Test
    void nativePay_sameOutTradeNo_shouldBeIdempotent() throws Exception {
        String body = """
                {
                  "out_trade_no": "ORD_IDEM_001",
                  "description": "幂等测试",
                  "notify_url": "http://127.0.0.1:8080/admin/order/mockPay/notify",
                  "amount": 10.00
                }
                """;

        String first = mockMvc.perform(post("/v3/pay/transactions/native")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.prepay_id").isNotEmpty())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String second = mockMvc.perform(post("/v3/pay/transactions/native")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.out_trade_no").value("ORD_IDEM_001"))
                .andExpect(jsonPath("$.trade_state").value("NOTPAY"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        // same prepay_id on repeat native for same out_trade_no
        org.junit.jupiter.api.Assertions.assertEquals(first, second);
    }
}
