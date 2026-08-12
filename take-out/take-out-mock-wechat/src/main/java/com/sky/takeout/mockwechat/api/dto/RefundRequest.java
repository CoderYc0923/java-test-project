package com.sky.takeout.mockwechat.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

/**
 * 退款请求（教学简化）。
 */
@Data
public class RefundRequest {

    /** 退款原因，如 duplicate_pay；可空，默认 duplicate_pay */
    @JsonProperty("reason")
    private String reason;
}
