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
     * 假微信沙箱地址，不要末尾斜杠。
     * 例：http://127.0.0.1:9090
     */
    private String mockWechatBaseUrl = "http://127.0.0.1:9090";

     /**
     * 商户支付结果通知 URL（完整路径），下单时传给假微信的 notify_url。
     * 例：http://127.0.0.1:8080/admin/order/mockPay/notify
     * <p>
     * Docker 里假微信若访问宿主机，可能要用 host.docker.internal。
     */
    private String merchantNotifyUrl = "http://127.0.0.1:8080/admin/order/mockPay/notify";
}
