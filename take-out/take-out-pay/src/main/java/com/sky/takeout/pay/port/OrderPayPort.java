package com.sky.takeout.pay.port;

import com.sky.takeout.pojo.entity.Order;

/**
 * 支付中心访问订单的端口（由take-out-system模块实现）
 */
public interface OrderPayPort {

    Order findOrderById(Long orderId);

    /**
     * 回调里只有商户订单号
     * 根据订单号查询订单
     * @param orderNumber
     * @return 订单
     */
    Order findOrderByNumber(String orderNumber);

    /**
     * CAS：仅付款+未支付 -> 待接单+已支付
     * @param orderId
     * @return 影响行数，1=成功，0=状态已变
     */
    int casMarkPaid(Long orderId);
}
