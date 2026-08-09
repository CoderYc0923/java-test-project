package com.sky.takeout.framework.security;

public final class SecurityConstant {

    public static final String[] WHITE_LIST = {
        "/admin/employee/login",
        "/admin/order/mockPay/notify",
        "/v3/api-docs/**",
        "/swagger-ui/**",
        "/swagger-ui.html"
    };

    private SecurityConstant() {}
}
