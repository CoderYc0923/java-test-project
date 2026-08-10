package com.sky.takeout.mockwechat.notify;

import java.math.BigDecimal;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class MerchantNotifyPayload {
    private String orderNumber;
    private BigDecimal amount;
    private Long timestamp;
    private String nonce;
    private String sign;
}
