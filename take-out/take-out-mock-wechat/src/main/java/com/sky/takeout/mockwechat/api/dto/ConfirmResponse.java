package com.sky.takeout.mockwechat.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ConfirmResponse {

    @JsonProperty("out_trade_no")
    private String outTradeNo;

    @JsonProperty("trade_state")
    private String tradeState;
}
