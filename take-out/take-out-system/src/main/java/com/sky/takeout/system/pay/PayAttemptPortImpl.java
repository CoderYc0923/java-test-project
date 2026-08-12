package com.sky.takeout.system.pay;

import java.util.List;

import org.springframework.stereotype.Component;

import com.sky.takeout.pay.port.PayAttemptPort;
import com.sky.takeout.pojo.entity.PayAttempt;
import com.sky.takeout.pojo.enums.PayAttemptStatus;

@Component
public class PayAttemptPortImpl implements PayAttemptPort {

    @Override
    public PayAttempt findByOutTradeNo(String outTradeNo) {
        return null;
    }

    @Override
    public PayAttempt findPayingByOrderId(Long orderId) {
        return null;
    }

    @Override
    public List<PayAttempt> listByOrderId(Long orderId) {
        return null;
    }

    @Override
    public int insertPaying(PayAttempt payAttempt) {
        return 1;
    }

    @Override
    public int updateStatus(Long id, PayAttemptStatus statusFrom,PayAttemptStatus statusTo, Integer payingFlag) {
        return 1;
    }

    @Override
    public int updatePrepayId(Long id, String prepayId) {
        return 1;
    }

}
