package com.sky.takeout.admin.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.sky.takeout.common.result.Result;
import com.sky.takeout.pojo.dto.order.OrderCancelDTO;
import com.sky.takeout.pojo.dto.order.OrderConfirmDTO;
import com.sky.takeout.pojo.dto.order.OrderQueryDTO;
import com.sky.takeout.pojo.dto.order.OrderRejectionDTO;
import com.sky.takeout.pojo.dto.order.OrderMockDTO;
import com.sky.takeout.pojo.vo.order.OrderMockVO;
import com.sky.takeout.pojo.vo.order.OrderStatisticsVO;
import com.sky.takeout.pojo.vo.order.OrderVO;
import com.sky.takeout.system.service.OrderService;

import jakarta.validation.Valid;


/**
 * 订单占位接口，返回空列表，保证工作台订单区不因 404/null 崩溃。
 */
@RestController
@RequestMapping("/admin/order")
public class OrderController {

    @Autowired
    private OrderService orderService;


    @GetMapping("/conditionSearch")
    public Result<IPage<OrderVO>> conditionSearch(@Valid OrderQueryDTO queryDTO) {
        IPage<OrderVO> page = orderService.page(queryDTO);

        return Result.success(page);
    }

    @GetMapping("/statistics")
    public Result<OrderStatisticsVO> statistics() {
        OrderStatisticsVO statistics = orderService.statistics();

        return Result.success(statistics);
    }

    @GetMapping("/details/{id}")
    public Result<OrderVO> details(@PathVariable Long id) {
        return Result.success(orderService.getById(id));
    }

    @PutMapping("/confirm")
    public Result<Void> confirm(@Valid @RequestBody OrderConfirmDTO confirmDTO) {
        orderService.confirm(confirmDTO);

        return Result.success();
    }

    @PutMapping("/rejection")
    public Result<Void> rejection(@Valid @RequestBody OrderRejectionDTO rejectionDTO) {
        orderService.rejection(rejectionDTO);

        return Result.success();
    }

    @PutMapping("/delivery/{id}")
    public Result<Void> delivery(@PathVariable Long id) {
        orderService.delivery(id);
        
        return Result.success();
    }

    @PutMapping("/complete/{id}")
    public Result<Void> complete(@PathVariable Long id) {
        orderService.complete(id);
        
        return Result.success();
    }

    @PutMapping("/cancel")
    public Result<Void> cancel(@Valid @RequestBody OrderCancelDTO cancelDTO) {
        orderService.cancel(cancelDTO);
        
        return Result.success();
    }

    @PostMapping("/mock")
    public Result<OrderMockVO> mock(@Valid @RequestBody OrderMockDTO mockDTO) {
        return Result.success(orderService.mock(mockDTO));
    }
}
