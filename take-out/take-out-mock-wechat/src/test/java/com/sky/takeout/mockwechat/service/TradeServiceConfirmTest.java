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
