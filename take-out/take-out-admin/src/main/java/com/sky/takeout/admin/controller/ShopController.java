package com.sky.takeout.admin.controller;

import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sky.takeout.common.result.Result;

/**
 * 店铺营业状态（内存占位，后续可换 Redis）。
 */
@RestController
@RequestMapping("/admin/shop")
public class ShopController {

    private final AtomicInteger status = new AtomicInteger(1);

    @GetMapping("/status")
    public Result<Integer> getStatus() {
        return Result.success(status.get());
    }

    @PutMapping("/{status}")
    public Result<Void> setStatus(@PathVariable Integer status) {
        this.status.set(status == null ? 0 : status);
        return Result.success();
    }
}
