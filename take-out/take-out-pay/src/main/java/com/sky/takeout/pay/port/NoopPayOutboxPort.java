package com.sky.takeout.pay.port;

import org.springframework.stereotype.Component;

@Component
public class NoopPayOutboxPort implements PayOutboxPort {

    @Override
    public void publishPendingForOrder(Long orderId) {
       // no-op：以后改成 insert into pay_outbox (...)
    }

    @Override
    public void insertOrderPaid(Long orderId, String orderNumber) {
        // no-op：以后改成发 MQ / 推厨房，并 update status=SENT
    }
}
