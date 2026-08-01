package com.sky.takeout.admin.controller;

import com.sky.takeout.common.result.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class DemoController {

    @GetMapping("/hello")
    public Result<String> hello() {
        return Result.success("Hello, World!");
    }
}
