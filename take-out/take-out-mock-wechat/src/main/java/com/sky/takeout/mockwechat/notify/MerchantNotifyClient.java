package com.sky.takeout.mockwechat.notify;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import com.sky.takeout.mockwechat.config.MockWechatProperties;
import com.sky.takeout.mockwechat.domain.Trade;
import com.sky.takeout.mockwechat.sign.HmacNotifySignUtil;

@Component
public class MerchantNotifyClient {

    private static final Logger log = LoggerFactory.getLogger(MerchantNotifyClient.class);

    private final RestClient restClient;
    private final MockWechatProperties properties;

    public MerchantNotifyClient(RestClient.Builder restClientBuilder, MockWechatProperties properties) {
        this.restClient = restClientBuilder.build();
        this.properties = properties;
    }

    /**
     * POST notifyUrl with HMAC-signed payload. Retries on non-2xx / exceptions.
     * Total attempts = 1 + max(0, notifyMaxRetries).
     *
     * @return true if at least one attempt returned 2xx
     */
    public boolean send(Trade trade) {
        int maxAttempts = 1 + Math.max(0, properties.getNotifyMaxRetries());
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                Long timestamp = System.currentTimeMillis() / 1000;
                String nonce = UUID.randomUUID().toString().replace("-", "");
                String sign = HmacNotifySignUtil.sign(
                        trade.getOutTradeNo(),
                        trade.getAmount(),
                        timestamp,
                        nonce,
                        properties.getMerchantNotifySecret());

                MerchantNotifyPayload payload = MerchantNotifyPayload.builder()
                        .orderNumber(trade.getOutTradeNo())
                        .amount(trade.getAmount())
                        .timestamp(timestamp)
                        .nonce(nonce)
                        .sign(sign)
                        .build();

                restClient.post()
                        .uri(trade.getNotifyUrl())
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(payload)
                        .retrieve()
                        .toBodilessEntity();

                log.info("merchant notify ok outTradeNo={} attempt={}", trade.getOutTradeNo(), attempt);
                return true;
            } catch (RestClientResponseException e) {
                log.warn("merchant notify non-2xx outTradeNo={} attempt={} status={}: {}",
                        trade.getOutTradeNo(), attempt, e.getStatusCode().value(), e.getMessage());
            } catch (Exception e) {
                log.warn("merchant notify failed outTradeNo={} attempt={}: {}",
                        trade.getOutTradeNo(), attempt, e.getMessage());
            }

            if (attempt < maxAttempts) {
                try {
                    Thread.sleep(properties.getNotifyRetryDelayMs());
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        log.error("merchant notify exhausted retries outTradeNo={} attempts={}",
                trade.getOutTradeNo(), maxAttempts);
        return false;
    }
}
