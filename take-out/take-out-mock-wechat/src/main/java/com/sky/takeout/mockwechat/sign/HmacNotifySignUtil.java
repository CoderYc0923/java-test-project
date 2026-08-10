package com.sky.takeout.mockwechat.sign;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * 与 take-out-pay HmacPaySignUtil 同算法（教学副本，避免依赖 pay 模块）。
 * plain: amount={plain}&nonce={nonce}&orderNumber={orderNumber}&timestamp={timestamp}&key={secret}
 */
public final class HmacNotifySignUtil {
    private HmacNotifySignUtil() {}

    public static String buildPlain(String orderNumber, BigDecimal amount, Long timestamp, String nonce, String secret) {
        return String.format("amount=%s&nonce=%s&orderNumber=%s&timestamp=%s&key=%s",
                amount.toPlainString(), nonce, orderNumber, timestamp, secret);
    }

    public static String sign(String orderNumber, BigDecimal amount, Long timestamp, String nonce, String secret) {
        String plain = buildPlain(orderNumber, amount, timestamp, nonce, secret);
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(plain.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("HMAC 签名失败", e);
        }
    }

    public static boolean verify(String orderNumber, BigDecimal amount, Long timestamp, String nonce,
            String secret, String signFromChannel) {
        if (signFromChannel == null) {
            return false;
        }
        String expect = sign(orderNumber, amount, timestamp, nonce, secret);
        return MessageDigest.isEqual(
                expect.getBytes(StandardCharsets.UTF_8),
                signFromChannel.getBytes(StandardCharsets.UTF_8));
    }
}
