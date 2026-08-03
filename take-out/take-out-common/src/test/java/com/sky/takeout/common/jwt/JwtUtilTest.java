package com.sky.takeout.common.jwt;

import com.sky.takeout.common.constant.JwtClaimsConstant;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;
import java.util.HashMap;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;


class JwtUtilTest {
    private static final String SECRET_KEY = "takeout_admin_jwt_secret_key_cyrus";
    private static final long TTL_MILLIS = 7200000L;

    @Test
    void createAndParseToken_shouldKeepEmpId() {
        Map<String, Object> claims = new HashMap<>();
        claims.put(JwtClaimsConstant.EMP_ID, 1L);

        String token = JwtUtil.createToken(SECRET_KEY, TTL_MILLIS, claims);

        assertNotNull(token);
        Claims parsed = JwtUtil.parseToken(SECRET_KEY, token);
        assertEquals(1L, ((Number) parsed.get(JwtClaimsConstant.EMP_ID)).longValue());
    }

    @Test
    void pareToken_withWrongSecret_shouldFail() {
        Map<String, Object> claims = new HashMap<>();
        claims.put(JwtClaimsConstant.EMP_ID, 1L);
        String token = JwtUtil.createToken(SECRET_KEY, TTL_MILLIS, claims);

        assertThrows(JwtException.class, () -> JwtUtil.parseToken("wrong-secret-key-32-characters!!", token));
    }
}
