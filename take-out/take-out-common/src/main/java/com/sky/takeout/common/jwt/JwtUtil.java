package com.sky.takeout.common.jwt;

import javax.crypto.SecretKey;
import java.util.Map;
import java.util.Date;
import java.nio.charset.StandardCharsets;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

public final class JwtUtil { // final：不许被继承

    private JwtUtil() {} // 私有构造：不许 new JwtUtil()，只通过静态方法用 → 典型工具类写法

    /**
     * 创建 JWT
     * @param secretKey 密钥（建议 ≥ 32 字符，对应 yml 里 jwt.admin-secret-key）
     * @param ttlMillis 过期毫秒数（对应 jwt.admin-ttl）
     * @param claims 自定义载荷，如 empId
     * @return JWT
     */
    public static String createToken(String secretKey, long ttlMillis, Map<String, Object> claims) {
        Date now = new Date();
        Date expiration = new Date(now.getTime() + ttlMillis);

        return Jwts.builder()
            .claims(claims) // 设置自定义载荷，如 empId
            .issuedAt(now) // 设置签发时间 iat
            .expiration(expiration) // 设置过期时间 exp
            .signWith(getSecretKey(secretKey)) // 设置签名算法和密钥
            .compact(); // 生成 JWT,打成三段式字符串 header.payload.signatures
    }

    /**
     * 解析并校验签名/过期；失败会抛 JwtException
     * @param secretKey 密钥（对应 yml 里 jwt.admin-secret-key）
     * @param token JWT
     * @return 载荷
     * 验签失败或过期会抛异常，拦截器里捕获后返回「未登录 / token 无效」。
     */
    public static Claims parseToken(String secretKey, String token) {
        return Jwts.parser()
            .verifyWith(getSecretKey(secretKey)) // 设置签名算法和密钥,同一把密钥验签
            .build() // 构建解析器
            .parseSignedClaims(token) // 验签 + 校验是否过期
            .getPayload(); // 取出 Claims（可 get empId）
    }

    /**
     * 根据密钥字符串生成 SecretKey 对象
     * @param secretKey 密钥字符串
     * @return SecretKey 对象
     */
    private static SecretKey getSecretKey(String secretKey) {
        return Keys.hmacShaKeyFor(secretKey.getBytes(StandardCharsets.UTF_8)); // 把 yml 里的字符串转成 HMAC 用的 SecretKey。字符串太短会在这里或签名时报错，所以 yml 里密钥要够长。
    }

}
