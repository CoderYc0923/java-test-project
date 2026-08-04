package com.sky.takeout.admin.controller;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sky.takeout.common.result.Result;

/**
 * 订单占位接口，返回空列表，保证工作台订单区不因 404/null 崩溃。
 */
@RestController
@RequestMapping("/admin/order")
public class OrderController {

    @GetMapping("/conditionSearch")
    public Result<Map<String, Object>> conditionSearch() {
        Map<String, Object> page = new HashMap<>();
        page.put("records", Collections.emptyList());
        page.put("total", 0);
        return Result.success(page);
    }

    @GetMapping("/statistics")
    public Result<Map<String, Object>> statistics() {
        Map<String, Object> data = new HashMap<>();
        data.put("toBeConfirmed", 0);
        data.put("confirmed", 0);
        data.put("deliveryInProgress", 0);
        return Result.success(data);
    }
}
