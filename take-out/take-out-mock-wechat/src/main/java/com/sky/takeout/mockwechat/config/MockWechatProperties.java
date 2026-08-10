package com.sky.takeout.mockwechat.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Data;

@Data
@ConfigurationProperties(prefix = "mock-wechat")
public class MockWechatProperties {
    /** 与 take-out pay.mock-secret 保持一致 */
    private String merchantNotifySecret = "change-me";
    private int notifyMaxRetries = 2;
    private long notifyRetryDelayMs = 500L;
}
