package com.sky.takeout.pay.sign;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.util.HexFormat;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * HMAC-SHA256 签名工具类 模拟微信：HMAC-SHA256 签名 / 验签
 * HmacPaySignUtil
 * 签名串（字段名字典序，末尾拼 key）：
 * amount={plain}&nonce={nonce}&orderNumber={orderNumber}&timestamp={timestamp}&key={secret}
 */
public class HmacPaySignUtil {

    /**
     * 构建签名串，拼明文（不含sign本身）
     * @param orderNumber 订单号
     * @param amount 金额
     * @param timeStamp 时间戳
     * @param nonce 随机数
     * @param secret 密钥
     * @return
     */
    public static String buildPlain(String orderNumber, BigDecimal amount, Long timeStamp, String nonce, String secret) {
        // 将金额转换为字符串
        String amountPlain = amount.toPlainString();
        // 构建签名串
        return String.format("amount=%s&nonce=%s&orderNumber=%s&timestamp=%s&key=%s", amountPlain, nonce, orderNumber, timeStamp, secret);
    }

    /**
     * 签名
     * @param orderNumber 订单号
     * @param amount 金额
     * @param timestamp 时间戳
     * @param nonce 随机数
     * @param secret 密钥
     * @return
     */
    public static String sign(String orderNumber, BigDecimal amount, Long timestamp, String nonce, String secret) {
        // 构建签名串
        String plain = buildPlain(orderNumber, amount, timestamp, nonce, secret);
        try {
            // 创建Mac实例
            Mac mac = Mac.getInstance("HmacSHA256");
            // 初始化Mac实例
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            // 生成签名 mac.doFinal 生成签名，返回字节数组
            byte[] raw = mac.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            // 返回签名 HexFormat.of().formatHex是将字节数组转换为十六进制字符串
            return HexFormat.of().formatHex(raw);
        // 如果签名失败，则抛出异常
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("HMAC 签名失败", e);
        }
    }

    /**
     * 验签
     * @param orderNumber 订单号
     * @param amount 金额
     * @param timesTamp 时间戳
     * @param nonce 随机数
     * @param secret 密钥
     * @param signFromChannel 签名来自渠道
     * @return
     */
    public static boolean verify(String orderNumber, BigDecimal amount, Long timesTamp, String nonce, String secret, String signFromChannel) {
        // 如果签名来自渠道为空，则返回false
        if (signFromChannel ==null) {
            return false;
        }
        // 构建期望的签名
        String expect = sign(orderNumber, amount, timesTamp, nonce, secret);
        // 比较签名
        // MessageDigest.isEqual 用于比较两个字节数组是否相等，避免时间攻击（常量时间比较，降低时序攻击面）
        return MessageDigest.isEqual(
            expect.getBytes(StandardCharsets.UTF_8),
            signFromChannel.getBytes(StandardCharsets.UTF_8)
        );
    }
}
