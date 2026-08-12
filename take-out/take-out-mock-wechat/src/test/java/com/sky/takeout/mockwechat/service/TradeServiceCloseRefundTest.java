package com.sky.takeout.mockwechat.service;

import java.math.BigDecimal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.RestClient;

import com.sky.takeout.mockwechat.api.MockWechatException;
import com.sky.takeout.mockwechat.config.MockWechatProperties;
import com.sky.takeout.mockwechat.domain.Trade;
import com.sky.takeout.mockwechat.domain.TradeState;
import com.sky.takeout.mockwechat.notify.MerchantNotifyClient;
import com.sky.takeout.mockwechat.store.TradeStore;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 关单 / 退款 / confirm 拒 CLOSED（设计 §6）。
 */
class TradeServiceCloseRefundTest {

    private TradeStore store;
    private TradeService tradeService;

    @BeforeEach
    void setUp() {
        store = new TradeStore();
        MockWechatProperties props = new MockWechatProperties();
        props.setMerchantNotifySecret("test-secret");
        MerchantNotifyClient client = new MerchantNotifyClient(RestClient.builder(), props);
        tradeService = new TradeService(store, client, props);
    }

    @Test
    void close_notPay_shouldBecomeClosed() {
        saveNotPay("ORD_CLOSE_1");
        assertEquals("CLOSED", tradeService.close("ORD_CLOSE_1").getTradeState());
        assertEquals(TradeState.CLOSED, store.findByOutTradeNo("ORD_CLOSE_1").orElseThrow().getTradeState());
    }

    @Test
    void close_alreadyClosed_shouldBeIdempotent() {
        saveNotPay("ORD_CLOSE_2");
        tradeService.close("ORD_CLOSE_2");
        assertEquals("CLOSED", tradeService.close("ORD_CLOSE_2").getTradeState());
    }

    @Test
    void close_success_should409() {
        Trade t = saveNotPay("ORD_CLOSE_3");
        t.setTradeState(TradeState.SUCCESS);
        store.save(t);
        MockWechatException ex = assertThrows(MockWechatException.class, () -> tradeService.close("ORD_CLOSE_3"));
        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        assertEquals("ORDER_PAID", ex.getCode());
    }

    @Test
    void close_missing_should404() {
        MockWechatException ex = assertThrows(MockWechatException.class, () -> tradeService.close("NO_SUCH"));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
    }

    @Test
    void refund_success_shouldBecomeRefund() {
        Trade t = saveNotPay("ORD_REF_1");
        t.setTradeState(TradeState.SUCCESS);
        store.save(t);
        assertEquals("REFUND", tradeService.refund("ORD_REF_1", "duplicate_pay").getTradeState());
    }

    @Test
    void refund_alreadyRefund_shouldBeIdempotent() {
        Trade t = saveNotPay("ORD_REF_2");
        t.setTradeState(TradeState.REFUND);
        store.save(t);
        assertEquals("REFUND", tradeService.refund("ORD_REF_2", null).getTradeState());
    }

    @Test
    void refund_notPay_should409() {
        saveNotPay("ORD_REF_3");
        MockWechatException ex = assertThrows(MockWechatException.class,
                () -> tradeService.refund("ORD_REF_3", "x"));
        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        assertEquals("NOT_SUCCESS", ex.getCode());
    }

    @Test
    void confirm_closed_should409() {
        saveNotPay("ORD_CF_CLOSED");
        tradeService.close("ORD_CF_CLOSED");
        MockWechatException ex = assertThrows(MockWechatException.class,
                () -> tradeService.confirmByOutTradeNo("ORD_CF_CLOSED"));
        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        assertEquals("ORDER_CLOSED", ex.getCode());
    }

    private Trade saveNotPay(String outTradeNo) {
        Trade trade = new Trade();
        trade.setOutTradeNo(outTradeNo);
        trade.setPrepayId("wx_prepay_" + outTradeNo);
        trade.setAmount(new BigDecimal("6.00"));
        trade.setNotifyUrl("http://127.0.0.1:8080/admin/order/mockPay/notify");
        trade.setTradeState(TradeState.NOTPAY);
        trade.setNotifySent(false);
        store.save(trade);
        return trade;
    }
}
