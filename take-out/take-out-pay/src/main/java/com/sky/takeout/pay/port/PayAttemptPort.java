package com.sky.takeout.pay.port;

import java.util.List;

import com.sky.takeout.pojo.entity.PayAttempt;
import com.sky.takeout.pojo.enums.PayAttemptStatus;

/**
 * 支付尝试端口
 * PayAttemptPort
 */
public interface PayAttemptPort {

    /**
     * 根据商户订单号查询支付尝试
     * @param outTradeNo
     * @return
     */
    PayAttempt findByOutTradeNo(String outTradeNo);

    /**
     * 根据订单ID查询PAYING状态的支付尝试
     * @param orderId
     * @return
     */
    PayAttempt findPayingByOrderId(Long orderId);

    /**
     * 根据订单ID查询对应支付尝试列表
     * @param orderId
     * @return
     */
    List<PayAttempt> listByOrderId(Long orderId);

    /**
     * 插入支付尝试
     * @param payAttempt
     * @return
     */
    int insertPaying(PayAttempt payAttempt);

    /**
     * 更新支付尝试的支付
     * @param id
     * @param statusFrom
     * @param statusTo
     * @param payingFlag
     * @return
     */
    int updateStatus(Long id, PayAttemptStatus statusFrom,PayAttemptStatus statusTo, Integer payingFlag);

    /**
     * 更新支付尝试的预支付ID
     * @param id
     * @param prepayId
     * @return
     */
    int updatePrepayId(Long id, String prepayId);
}
