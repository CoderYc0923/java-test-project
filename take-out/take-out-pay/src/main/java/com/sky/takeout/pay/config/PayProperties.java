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
     * 模拟微信API密钥（签名用）
     */
    private String mockSecret;

    /**
     * 订单幂等性过期时间（秒）
     */
    private Long orderIdempotentTtlSeconds;

    /**
     * 支付锁过期时间（秒）
     */
    private Long payLockTtlSeconds;

    /**
     * 回调nonce去重TTL（秒）
     */
    private Long nonceTtlSeconds = 600L;

    /**
     * 允许的时间戳偏差（秒），防重放
     */
    private Long timestampSkewSeconds = 300L;

    /**
     * 微信延迟回调（毫秒）
     */
    private Long notifyDelayMs = 1500L;
}
