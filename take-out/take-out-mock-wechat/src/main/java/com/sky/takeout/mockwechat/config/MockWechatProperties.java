package com.sky.takeout.mockwechat.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Data;

@Data
@ConfigurationProperties(prefix = "mock-wechat")
public class MockWechatProperties {
    /** 与 take-out pay.mock-secret 保持一致 */
    private String merchantNotifySecret = "change-me";
    /**
     * 首次失败后的额外重试次数（不含首次）。总尝试次数 = 1 + max(0, notifyMaxRetries)。
     * 默认 2 → 最多 3 次尝试。
     */
    private int notifyMaxRetries = 2;
    private long notifyRetryDelayMs = 500L;
}
