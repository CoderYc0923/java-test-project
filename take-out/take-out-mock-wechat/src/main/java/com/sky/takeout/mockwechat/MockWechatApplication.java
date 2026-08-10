package com.sky.takeout.mockwechat;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class MockWechatApplication {
    public static void main(String[] args) {
        SpringApplication.run(MockWechatApplication.class, args);
    }
}
