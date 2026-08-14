package com.sky.takeout.pay.port;

/**
 * 出账端口
 * PayOutboxPort
 */
public interface PayOutboxPort {

    /**
     * 插入订单ORDER_PAID消息
     * @param orderId
     * @param orderNumber
     */
    void insertOrderPaid(Long orderId, String orderNumber);

    /**
     * 发布订单消息
     * @param orderId
     */
    void publishPendingForOrder(Long orderId);

    /**
     * 发布批量新消息
     * @param limit
     * @return
     */
    int publishBatchNew(int limit);
}
