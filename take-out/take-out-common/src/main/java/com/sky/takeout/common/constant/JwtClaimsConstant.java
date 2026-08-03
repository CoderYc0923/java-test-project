package com.sky.takeout.common.constant;

/**
 * JWT claims常量
 * 往 JWT 里放员工 id 时用统一 key，避免拦截器和登录接口各写各的字符串。
 */
public final class JwtClaimsConstant {
    public static final String EMP_ID = "empId";

    private JwtClaimsConstant() {
    }

}
