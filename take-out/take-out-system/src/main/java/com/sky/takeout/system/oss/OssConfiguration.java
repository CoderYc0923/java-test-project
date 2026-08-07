package com.sky.takeout.system.oss;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;

@Configuration
@EnableConfigurationProperties(OssProperties.class)
public class OssConfiguration {

    /**
     * 创建OSS客户端
     * 全局一个 OSS 客户端；容器销毁时调用 shutdown
     */
    @Bean(destroyMethod = "shutdown")
    public OSS ossClient(OssProperties ossProperties) {
        return new OSSClientBuilder().build(ossProperties.getEndpoint(), ossProperties.getAccessKeyId(),
                ossProperties.getAccessKeySecret());
    }

}
