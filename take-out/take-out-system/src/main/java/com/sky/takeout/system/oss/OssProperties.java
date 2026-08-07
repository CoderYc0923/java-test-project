package com.sky.takeout.system.oss;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Data;

/**
 * 对应yml里的oss配置
 * 一个Bucket: 业务用前缀区分，例如dish、setmeal
 * OssProperties
 * @author Cyrus
 */

@ConfigurationProperties(prefix = "oss")
@Data
public class OssProperties {

    // 如https://oss-cn-beijing.aliyuncs.com */
    private String endpoint;

    private String bucket;

    private String accessKeyId;

    private String accessKeySecret;

    // 可选：自定义域名；为空则用 https://{bucket}.{endpoint主机}/{key}
    private String domain;

    // 签名 URL 有效期，1 小时
    private Long signExpireSeconds = 3600L;
}
