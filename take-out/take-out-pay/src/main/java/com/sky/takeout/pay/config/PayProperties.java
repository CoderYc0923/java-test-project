package com.sky.takeout.pay.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Data;

/**
 * 支付中心配置属性
 * PayProperties
 */
@ConfigurationProperties(prefix = "pay")
@Data
public class PayProperties {

    /**
     * 模拟支付的密钥
     */
    private String mockSectet;

    /**
     * 订单幂等性过期时间（秒）
     */
    private Long orderIdempotentTtlSeconds;

    /**
     * 支付锁过期时间（秒）
     */
    private Long payLockTtlSeconds;
}
