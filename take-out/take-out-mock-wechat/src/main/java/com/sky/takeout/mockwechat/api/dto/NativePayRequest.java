package com.sky.takeout.mockwechat.api.dto;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class NativePayRequest {

    @NotBlank
    @JsonProperty("out_trade_no")
    private String outTradeNo;

    @NotBlank
    private String description;

    @NotBlank
    @JsonProperty("notify_url")
    private String notifyUrl;

    @NotNull
    private BigDecimal amount;
}
