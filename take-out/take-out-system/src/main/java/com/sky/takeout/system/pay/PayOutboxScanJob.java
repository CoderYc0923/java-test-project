package com.sky.takeout.system.pay;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.sky.takeout.pay.port.PayOutboxPort;

@Component
public class PayOutboxScanJob {

    private static final Logger log = LoggerFactory.getLogger(PayOutboxScanJob.class);

    private final PayOutboxPort payOutboxPort;

    public PayOutboxScanJob(PayOutboxPortImpl payOutboxPort) {
        this.payOutboxPort = payOutboxPort;
    }

    /**
     * 出站扫描,每15秒执行一次
     */
    @Scheduled(fixedDelayString = "${pay.outbox-scan-delay-ms:15000}")
    public void scan() {
        int n = payOutboxPort.publishBatchNew(50);
        if (n > 0) {
            log.info("出站扫描，已发送 {} 条消息", n);
        }
    }
}
