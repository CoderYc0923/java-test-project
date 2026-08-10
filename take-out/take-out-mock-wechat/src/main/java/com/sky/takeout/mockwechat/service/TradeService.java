package com.sky.takeout.mockwechat.service;

import java.time.Instant;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.sky.takeout.mockwechat.api.MockWechatException;
import com.sky.takeout.mockwechat.api.dto.NativePayRequest;
import com.sky.takeout.mockwechat.api.dto.TransactionResponse;
import com.sky.takeout.mockwechat.domain.Trade;
import com.sky.takeout.mockwechat.domain.TradeState;
import com.sky.takeout.mockwechat.store.TradeStore;

@Service
public class TradeService {

    private final TradeStore tradeStore;

    public TradeService(TradeStore tradeStore) {
        this.tradeStore = tradeStore;
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
