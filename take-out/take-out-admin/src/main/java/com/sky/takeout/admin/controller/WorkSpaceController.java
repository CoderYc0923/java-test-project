package com.sky.takeout.admin.controller;

import java.util.HashMap;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sky.takeout.common.result.Result;

/**
 * 工作台占位接口，返回空数据，保证前端登录后可进入首页。
 */
@RestController
@RequestMapping("/admin/workspace")
public class WorkSpaceController {

    @GetMapping("/businessData")
    public Result<Map<String, Object>> businessData() {
        Map<String, Object> data = new HashMap<>();
        data.put("turnover", 0D);
        data.put("validOrderCount", 0);
        data.put("orderCompletionRate", 0D);
        data.put("unitPrice", 0D);
        data.put("newUsers", 0);
        return Result.success(data);
    }

    @GetMapping("/overviewOrders")
    public Result<Map<String, Object>> overviewOrders() {
        Map<String, Object> data = new HashMap<>();
        data.put("waitingOrders", 0);
        data.put("deliveredOrders", 0);
        data.put("completedOrders", 0);
        data.put("cancelledOrders", 0);
        data.put("allOrders", 0);
        return Result.success(data);
    }

    @GetMapping("/overviewDishes")
    public Result<Map<String, Object>> overviewDishes() {
        Map<String, Object> data = new HashMap<>();
        data.put("sold", 0);
        data.put("discontinued", 0);
        return Result.success(data);
    }

    @GetMapping("/overviewSetmeals")
    public Result<Map<String, Object>> overviewSetmeals() {
        Map<String, Object> data = new HashMap<>();
        data.put("sold", 0);
        data.put("discontinued", 0);
        return Result.success(data);
    }
}
