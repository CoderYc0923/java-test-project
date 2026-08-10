package com.sky.takeout.pay.client.dto;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Builder;
import lombok.Data;

/**
 * 微信 Native 支付请求
 * NativePayRequest
 */
@Data
@Builder
public class NativePayRequest {

    /** 商户订单号 = orders.number */
    /** JsonProperty 注解用于指定 JSON 序列化时的属性名称 */
    @JsonProperty("out_trade_no")
    private String outTradeNo;

     /** 商品描述 */
    private String description;

     /** 付成功后假微信回调的商户 URL */
    @JsonProperty("notify_url")
    private String notifyUrl;

    /** 金额：元（BigDecimal），须与订单 amount 一致 */
    private BigDecimal amount;
}
