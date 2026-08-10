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
    /** 是否已向商户发出过成功通知 */
    private boolean notifySent;
}
