package com.sky.takeout.mockwechat.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ConfirmRequest {

    @NotBlank
    @JsonProperty("out_trade_no")
    private String outTradeNo;
}
