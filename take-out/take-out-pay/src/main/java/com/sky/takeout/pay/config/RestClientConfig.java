package com.sky.takeout.pay.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

import org.springframework.context.annotation.Bean;

/**
 * 支付中心出站http（调用微信）
 * 超时避免微信挂死拖垮Tomcat线程
 * RestClient
 */
@Configuration
public class RestClientConfig {

    /**
     * 第三方服务（微信支付）所以用Bean注入
     * @return
     */
    @Bean
    public RestClient.Builder payRestClientBuilder() {
        // 使用SimpleClientHttpRequestFactory设置超时时间
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(3));
        factory.setReadTimeout(Duration.ofSeconds(5));
        return RestClient.builder().requestFactory(factory);
    }
}
