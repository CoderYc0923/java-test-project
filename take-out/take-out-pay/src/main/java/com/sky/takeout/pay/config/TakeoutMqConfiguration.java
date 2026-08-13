package com.sky.takeout.pay.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 支付中心MQ配置
 * TakeoutMqConfiguration
 * @EnableConfigurationProperties 启用配置属性
 * @Configuration 配置类
 */
@Configuration
@EnableConfigurationProperties(TakeoutMqProperties.class)
public class TakeoutMqConfiguration {

}
