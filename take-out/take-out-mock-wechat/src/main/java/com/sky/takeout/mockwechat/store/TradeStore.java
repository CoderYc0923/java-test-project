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
