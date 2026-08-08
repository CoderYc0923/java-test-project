package com.sky.takeout.pay.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 支付中心自动配置
 * PayAutoConfiguration
 * @EnableConfigurationProperties 启用配置属性
 * @Configuration 配置类
 */
@Configuration
@EnableConfigurationProperties(PayProperties.class)
public class PayAutoConfiguration {

}
