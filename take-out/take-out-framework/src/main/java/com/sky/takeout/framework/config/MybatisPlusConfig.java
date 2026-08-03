package com.sky.takeout.framework.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@MapperScan("com.sky.takeout.system.mapper")
public class MybatisPlusConfig {
}
