package com.sky.takeout.mockwechat.api.dto;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TransactionResponse {

    @JsonProperty("out_trade_no")
    private String outTradeNo;

    @JsonProperty("prepay_id")
    private String prepayId;

    private String description;

    private BigDecimal amount;

    private String currency;

    @JsonProperty("trade_state")
    private String tradeState;
}
