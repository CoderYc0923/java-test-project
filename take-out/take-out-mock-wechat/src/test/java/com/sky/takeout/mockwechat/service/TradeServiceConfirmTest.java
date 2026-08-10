package com.sky.takeout.mockwechat.service;

import java.math.BigDecimal;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import com.sky.takeout.mockwechat.config.MockWechatProperties;
import com.sky.takeout.mockwechat.domain.Trade;
import com.sky.takeout.mockwechat.domain.TradeState;
import com.sky.takeout.mockwechat.notify.MerchantNotifyClient;
import com.sky.takeout.mockwechat.sign.HmacNotifySignUtil;
import com.sky.takeout.mockwechat.store.TradeStore;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TradeServiceConfirmTest {

    private static final String SECRET = "test-secret";
    private static final Pattern STRING_FIELD = Pattern.compile("\"%s\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern NUMBER_FIELD = Pattern.compile("\"%s\"\\s*:\\s*([0-9.]+)");

    private MockWebServer server;
    private TradeStore store;
    private TradeService tradeService;
    private MockWechatProperties props;

    @BeforeEach
    void setUp() throws Exception {
        server = new MockWebServer();
        server.start();
        store = new TradeStore();
        props = new MockWechatProperties();
        props.setMerchantNotifySecret(SECRET);
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
        assertNotNull(req);
        assertEquals("POST", req.getMethod());

        String body = req.getBody().readUtf8();
        assertTrue(body.contains("\"orderNumber\""));
        assertTrue(body.contains("\"amount\""));
        assertTrue(body.contains("\"timestamp\""));
        assertTrue(body.contains("\"nonce\""));
        assertTrue(body.contains("\"sign\""));

        String orderNumber = extractString(body, "orderNumber");
        String amountText = extractNumberOrString(body, "amount");
        String timestampText = extractNumberOrString(body, "timestamp");
        String nonce = extractString(body, "nonce");
        String sign = extractString(body, "sign");
        assertEquals("ORD_C1", orderNumber);
        assertTrue(HmacNotifySignUtil.verify(
                orderNumber,
                new BigDecimal(amountText),
                Long.parseLong(timestampText),
                nonce,
                SECRET,
                sign));

        tradeService.confirmByOutTradeNo("ORD_C1");
        assertEquals(1, server.getRequestCount());
        assertEquals(TradeState.SUCCESS, store.findByOutTradeNo("ORD_C1").orElseThrow().getTradeState());
        assertTrue(store.findByOutTradeNo("ORD_C1").orElseThrow().isNotifySent());
    }

    @Test
    void confirm_shouldRetryNotify_whenFirstResponseIs500() throws Exception {
        props.setNotifyMaxRetries(1); // 1 extra → total 2 attempts
        MerchantNotifyClient client = new MerchantNotifyClient(RestClient.builder(), props);
        tradeService = new TradeService(store, client, props);

        server.enqueue(new MockResponse().setResponseCode(500));
        server.enqueue(new MockResponse().setResponseCode(200));
        String notifyUrl = server.url("/admin/order/mockPay/notify").toString();

        Trade trade = new Trade();
        trade.setOutTradeNo("ORD_C2");
        trade.setPrepayId("wx_prepay_c2");
        trade.setAmount(new BigDecimal("3.50"));
        trade.setNotifyUrl(notifyUrl);
        trade.setTradeState(TradeState.NOTPAY);
        trade.setNotifySent(false);
        store.save(trade);

        tradeService.confirmByOutTradeNo("ORD_C2");

        assertEquals(2, server.getRequestCount());
        assertEquals(TradeState.SUCCESS, store.findByOutTradeNo("ORD_C2").orElseThrow().getTradeState());
        assertTrue(store.findByOutTradeNo("ORD_C2").orElseThrow().isNotifySent());
    }

    private static String extractString(String json, String field) {
        Matcher m = Pattern.compile(String.format(STRING_FIELD.pattern(), field)).matcher(json);
        assertTrue(m.find(), "missing string field: " + field);
        return m.group(1);
    }

    private static String extractNumberOrString(String json, String field) {
        Matcher asString = Pattern.compile(String.format(STRING_FIELD.pattern(), field)).matcher(json);
        if (asString.find()) {
            return asString.group(1);
        }
        Matcher asNumber = Pattern.compile(String.format(NUMBER_FIELD.pattern(), field)).matcher(json);
        assertTrue(asNumber.find(), "missing number field: " + field);
        return asNumber.group(1);
    }
}
