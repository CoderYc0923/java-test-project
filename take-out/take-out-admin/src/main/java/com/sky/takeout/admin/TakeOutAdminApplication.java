package com.sky.takeout.admin;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = "com.sky.takeout")
@EnableScheduling
public class TakeOutAdminApplication {

    public static void main(String[] args) {
        SpringApplication.run(TakeOutAdminApplication.class, args);
    }
}
