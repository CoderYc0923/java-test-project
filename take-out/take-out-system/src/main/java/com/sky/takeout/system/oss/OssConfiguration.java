package com.sky.takeout.system.oss;

import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;

/**
 * 仅当配置了非空的 access-key-id 时才创建 OSS 客户端。
 * 本地未配密钥时应用仍可启动，上传接口会提示未配置。
 */
@Configuration
@EnableConfigurationProperties(OssProperties.class)
public class OssConfiguration {

    /**
     * 全局一个 OSS 客户端；容器销毁时调用 shutdown。
     * ConditionalOnExpression：空字符串不会创建 Bean（默认 @ConditionalOnProperty 会把空串当成“已配置”）。
     */
    @Bean(destroyMethod = "shutdown")
    @ConditionalOnExpression("T(org.springframework.util.StringUtils).hasText('${oss.access-key-id:}')")
    public OSS ossClient(OssProperties ossProperties) {
        return new OSSClientBuilder().build(
                ossProperties.getEndpoint(),
                ossProperties.getAccessKeyId(),
                ossProperties.getAccessKeySecret());
    }
}
