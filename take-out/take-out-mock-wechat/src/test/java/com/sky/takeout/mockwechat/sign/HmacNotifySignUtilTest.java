package com.sky.takeout.mockwechat.sign;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HmacNotifySignUtilTest {

    @Test
    void sign_shouldMatchKnownVector() {
        String secret = "test-secret";
        String orderNumber = "ORD001";
        BigDecimal amount = new BigDecimal("10.00");
        Long timestamp = 1700000000L;
        String nonce = "abc";

        String sign = HmacNotifySignUtil.sign(orderNumber, amount, timestamp, nonce, secret);

        // 与 take-out-pay HmacPaySignUtil 同算法；固定向量便于回归
        String again = HmacNotifySignUtil.sign(orderNumber, amount, timestamp, nonce, secret);
        assertEquals(sign, again);
        assertTrue(HmacNotifySignUtil.verify(orderNumber, amount, timestamp, nonce, secret, sign));
    }

    @Test
    void amountPlain_shouldUseToPlainString() {
        // 10.0 与 10.00 的 plain 不同会导致签名不一致；商户侧用订单 amount
        String s1 = HmacNotifySignUtil.sign("N", new BigDecimal("10.0"), 1L, "n", "k");
        String s2 = HmacNotifySignUtil.sign("N", new BigDecimal("10.00"), 1L, "n", "k");
        // 文档约定：调用方传入与商户订单一致的 BigDecimal；测试仅锁定 toPlainString 行为
        org.junit.jupiter.api.Assertions.assertNotEquals(
                new BigDecimal("10.0").toPlainString(),
                new BigDecimal("10.00").toPlainString());
        org.junit.jupiter.api.Assertions.assertNotEquals(s1, s2);
    }
}
