package com.sky.takeout.system.service.impl;

import java.time.DateTimeException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.sky.takeout.common.exception.BusinessException;
import com.sky.takeout.common.result.ErrorCode;
import com.sky.takeout.pojo.dto.order.OrderQueryDTO;
import com.sky.takeout.system.mapper.OrderMapper;
import com.sky.takeout.system.service.OrderService;

import com.sky.takeout.pojo.entity.Order;
import com.sky.takeout.pojo.entity.OrderDetail;
import com.sky.takeout.pojo.enums.OrderStatus;
import com.sky.takeout.pojo.vo.order.OrderVO;

@Service
public class OrderServiceImpl implements OrderService {

    private final OrderMapper orderMapper;

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public OrderServiceImpl(OrderMapper orderMapper) {
        this.orderMapper = orderMapper;
    }

    @Override
    public IPage<OrderVO> page(OrderQueryDTO queryDTO) {
        Integer pageNum = queryDTO.getPage() == null || queryDTO.getPage() <= 0 ? 1 : queryDTO.getPage();
        Integer pageSize = queryDTO.getPageSize() == null || queryDTO.getPageSize() <= 0 ? 10 : queryDTO.getPageSize();

        Page<Order> page = new Page<>(pageNum, pageSize);


        LambdaQueryWrapper<Order> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.like(StringUtils.hasText(queryDTO.getNumber()), Order::getNumber, queryDTO.getNumber());
        queryWrapper.like(StringUtils.hasText(queryDTO.getPhone()), Order::getPhone, queryDTO.getPhone());
        
        LocalDateTime beginTime = parseDateTime(queryDTO.getBeginTime());
        LocalDateTime endTime = parseDateTime(queryDTO.getEndTime());
        queryWrapper.ge(beginTime != null, Order::getOrderTime, beginTime);
        queryWrapper.le(endTime != null, Order::getOrderTime, endTime);

        Integer statusCode = queryDTO.getStatus();
        if (statusCode != null && statusCode != 0) {
            queryWrapper.eq(Order::getStatus, OrderStatus.fromCode(statusCode));
        }

        queryWrapper.orderByDesc(Order::getOrderTime);

        // 查看订单头
        IPage<Order> orderPage = orderMapper.selectPage(page, queryWrapper);
        List<Order> records = orderPage.getRecords();
        if (records.isEmpty()) {
            return page.convert(order -> toVo(order, null));
        }




    }

    /**
     * Entity → VO；details 用于拼 orderDishes（可为 null）。
     */
    private OrderVO toVO(Order order, List<OrderDetail> details) {
        OrderVO vo = new OrderVO();
        BeanUtils.copyProperties(order, vo);
        vo.setOrderDishes(buildOrderDishes(details));
        return vo;
    }

    /**
     * 前端传来 yyyy-MM-dd HH:mm:ss；空串返回 null。
     */
    private LocalDateTime parseDateTime(String text) {
        if (!StringUtils.hasText(text)) {
            return null;
        }
        try {
            return LocalDateTime.parse(text.trim(), DATE_TIME_FORMATTER);
        } catch (DateTimeException e) {
            throw new BusinessException(ErrorCode.ERROR, "时间格式错误，应为 yyyy-MM-dd HH:mm:ss");
        }
    }
}
