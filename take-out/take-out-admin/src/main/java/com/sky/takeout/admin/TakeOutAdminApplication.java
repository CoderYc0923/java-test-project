package com.sky.takeout.admin;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.sky.takeout")
public class TakeOutAdminApplication {

    public static void main(String[] args) {
        SpringApplication.run(TakeOutAdminApplication.class, args);
    }
}
