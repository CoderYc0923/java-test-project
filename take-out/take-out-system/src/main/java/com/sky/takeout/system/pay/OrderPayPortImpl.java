package com.sky.takeout.system.pay;

import java.time.LocalDateTime;

import org.springframework.stereotype.Component;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sky.takeout.pay.port.OrderPayPort;
import com.sky.takeout.pojo.entity.Order;
import com.sky.takeout.pojo.enums.OrderStatus;
import com.sky.takeout.pojo.enums.PayStatus;
import com.sky.takeout.system.mapper.OrderMapper;

@Component
public class OrderPayPortImpl implements OrderPayPort {

    private final OrderMapper orderMapper;

    public OrderPayPortImpl(OrderMapper orderMapper) {
        this.orderMapper = orderMapper;
    }

    @Override
    public Order findOrderById(Long orderId) {
        Order order = orderMapper.selectById(orderId);

        return order;
    }

    @Override
    public Order findOrderByNumber(String orderNumber) {
        if (orderNumber == null || orderNumber.isBlank()) {
            return null;
        }

        return orderMapper.selectOne(new LambdaQueryWrapper<Order>().eq(Order::getNumber, orderNumber).last("limit 1"));
    }

    /**
     * 原理：CAS：Compare And Swap
     * 
     * 先构造一个理想状态的更新对象
     * 
     * 再拿着更新对象和更新条件去数据库更新
     * 
     * 如果数据库中订单状态和支付状态不符合更新对象，则更新失败，返回0
     * 
     * 这就是CAS：Compare And Swap
     * SQL：UPDATE order SET status = ?, pay_status = ?, checkout_time = ? WHERE id =
     * ? AND status = ? AND pay_status = ?
     * 
     */
    @Override
    public int casMarkPaid(Long orderId) {
        // 构造更新对象
        Order patch = new Order();
        patch.setStatus(OrderStatus.TO_BE_CONFIRMED);
        patch.setPayStatus(PayStatus.PAID);
        patch.setCheckoutTime(LocalDateTime.now());

        // 构造更新条件
        return orderMapper.update(patch, new LambdaQueryWrapper<Order>().eq(Order::getId, orderId)
                .eq(Order::getStatus, OrderStatus.PENDING_PAYMENT).eq(Order::getPayStatus, PayStatus.UNPAID));
    }
}
