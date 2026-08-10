package com.sky.takeout.mockwechat.service;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.sky.takeout.mockwechat.api.MockWechatException;
import com.sky.takeout.mockwechat.api.dto.ConfirmRequest;
import com.sky.takeout.mockwechat.api.dto.ConfirmResponse;
import com.sky.takeout.mockwechat.api.dto.NativePayRequest;
import com.sky.takeout.mockwechat.api.dto.TransactionResponse;
import com.sky.takeout.mockwechat.config.MockWechatProperties;
import com.sky.takeout.mockwechat.domain.Trade;
import com.sky.takeout.mockwechat.domain.TradeState;
import com.sky.takeout.mockwechat.notify.MerchantNotifyClient;
import com.sky.takeout.mockwechat.store.TradeStore;

@Service
public class TradeService {

    private final TradeStore tradeStore;
    private final MerchantNotifyClient merchantNotifyClient;
    @SuppressWarnings("unused")
    private final MockWechatProperties properties;
    private final ConcurrentHashMap<String, Object> confirmLocks = new ConcurrentHashMap<>();

    public TradeService(TradeStore tradeStore, MerchantNotifyClient merchantNotifyClient,
            MockWechatProperties properties) {
        this.tradeStore = tradeStore;
        this.merchantNotifyClient = merchantNotifyClient;
        this.properties = properties;
    }

    public TransactionResponse createNative(NativePayRequest request) {
        return tradeStore.findByOutTradeNo(request.getOutTradeNo())
                .map(existing -> {
                    if (existing.getTradeState() == TradeState.SUCCESS) {
                        throw new MockWechatException(
                                HttpStatus.CONFLICT,
                                "ORDER_PAID",
                                "out_trade_no already paid");
                    }
                    // NOTPAY: idempotent return
                    return toResponse(existing);
                })
                .orElseGet(() -> {
                    Trade trade = new Trade();
                    trade.setOutTradeNo(request.getOutTradeNo());
                    trade.setPrepayId("wx_prepay_" + UUID.randomUUID());
                    trade.setDescription(request.getDescription());
                    trade.setNotifyUrl(request.getNotifyUrl());
                    trade.setAmount(request.getAmount());
                    trade.setCurrency("CNY");
                    trade.setTradeState(TradeState.NOTPAY);
                    trade.setCreatedAt(Instant.now());
                    trade.setNotifySent(false);
                    tradeStore.save(trade);
                    return toResponse(trade);
                });
    }

    public TransactionResponse queryByOutTradeNo(String outTradeNo) {
        Trade trade = tradeStore.findByOutTradeNo(outTradeNo)
                .orElseThrow(() -> new MockWechatException(
                        HttpStatus.NOT_FOUND,
                        "ORDER_NOT_FOUND",
                        "out_trade_no not found"));
        return toResponse(trade);
    }

    public ConfirmResponse confirm(ConfirmRequest request) {
        return confirmByOutTradeNo(request.getOutTradeNo());
    }

    public ConfirmResponse confirmByOutTradeNo(String outTradeNo) {
        Object lock = confirmLocks.computeIfAbsent(outTradeNo, k -> new Object());
        Trade toNotify = null;

        synchronized (lock) {
            Trade trade = tradeStore.findByOutTradeNo(outTradeNo)
                    .orElseThrow(() -> new MockWechatException(
                            HttpStatus.NOT_FOUND,
                            "ORDER_NOT_FOUND",
                            "out_trade_no not found"));

            if (trade.getTradeState() == TradeState.SUCCESS) {
                // Spec: already SUCCESS — do not POST notify again
                return toConfirmResponse(trade);
            }

            if (!StringUtils.hasText(trade.getNotifyUrl())) {
                throw new MockWechatException(
                        HttpStatus.BAD_REQUEST,
                        "NOTIFY_URL_BLANK",
                        "notify_url is blank");
            }

            trade.setTradeState(TradeState.SUCCESS);
            trade.setPaidAt(Instant.now());
            tradeStore.save(trade);
            toNotify = trade;
        }

        // Notify outside the lock so concurrent confirms are not blocked on HTTP I/O
        boolean notified = merchantNotifyClient.send(toNotify);
        if (notified) {
            synchronized (lock) {
                Trade trade = tradeStore.findByOutTradeNo(outTradeNo).orElse(toNotify);
                trade.setNotifySent(true);
                tradeStore.save(trade);
                return toConfirmResponse(trade);
            }
        }
        return toConfirmResponse(toNotify);
    }

    private static ConfirmResponse toConfirmResponse(Trade trade) {
        return ConfirmResponse.builder()
                .outTradeNo(trade.getOutTradeNo())
                .tradeState(trade.getTradeState().name())
                .build();
    }

    private static TransactionResponse toResponse(Trade trade) {
        return TransactionResponse.builder()
                .outTradeNo(trade.getOutTradeNo())
                .prepayId(trade.getPrepayId())
                .description(trade.getDescription())
                .amount(trade.getAmount())
                .currency(trade.getCurrency())
                .tradeState(trade.getTradeState().name())
                .build();
    }
}
