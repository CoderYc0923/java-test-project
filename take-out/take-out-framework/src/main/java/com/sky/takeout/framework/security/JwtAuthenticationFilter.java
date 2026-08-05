package com.sky.takeout.framework.security;

import java.io.IOException;
import java.nio.file.PathMatcher;

import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import com.sky.takeout.common.constant.JwtClaimsConstant;
import com.sky.takeout.common.context.BaseContext;
import com.sky.takeout.common.jwt.JwtUtil;
import com.sky.takeout.system.security.EmployeeUserDetails;
import com.sky.takeout.framework.security.SecurityConstant;

import io.jsonwebtoken.JwtException;

import org.springframework.http.HttpHeaders;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 基于 JWT 的认证过滤器。
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final AuthenticationEntryPoint authenticationEntryPoint; // 认证失败时的处理
    private final String secretKey; // JWT 密钥
    private final String tokenHeaderName; // JWT 令牌头名称

    public JwtAuthenticationFilter(AuthenticationEntryPoint authenticationEntryPoint, String secretKey, String tokenHeaderName) {
        this.authenticationEntryPoint = authenticationEntryPoint;
        this.secretKey = secretKey;
        this.tokenHeaderName = tokenHeaderName;
    }

    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        for (String path : SecurityConstant.WHITE_LIST) {
            if (PATH_MATCHER.match(path, uri)) {
                return true;
            }
        }
        return false;
    }


    /**
     * 过滤器逻辑：
     * 1. 获取请求头中的 JWT 令牌
     * 2. 解析 JWT 令牌，获取用户 ID
     * 3. 设置当前用户 ID
     * 4. 执行后续过滤器
     * 5. 清理当前用户 ID
     */
    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain
    ) throws ServletException, IOException {
        // 获取请求头中的 JWT 令牌
        String headerName = (tokenHeaderName == null || tokenHeaderName.isBlank()) ? HttpHeaders.AUTHORIZATION : tokenHeaderName;
        String header = request.getHeader(headerName);

        // 如果请求头中没有 JWT 令牌，则放行（登录等匿名接口依赖此处 return）
        if (header == null || header.isBlank()) {
            filterChain.doFilter(request, response);
            return;
        }

        // 提取 JWT 令牌
        String token = header.startsWith("Bearer ") ? header.substring(7).trim() : header.trim();

        try {
            // 解析 JWT 令牌，获取用户 ID
            var claims = JwtUtil.parseToken(secretKey, token);
            Long empId = ((Number) claims.get(JwtClaimsConstant.EMP_ID)).longValue();
            // 创建用户详情对象
            EmployeeUserDetails principal = EmployeeUserDetails.forId(empId);
            // 创建认证对象并写入 SecurityContext（后续 authenticated() 依赖此上下文）
            var authentication = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
            SecurityContextHolder.getContext().setAuthentication(authentication);
            BaseContext.setCurrentId(empId);
            filterChain.doFilter(request, response);
        } catch (JwtException | IllegalArgumentException | ClassCastException | NullPointerException e) {
            SecurityContextHolder.clearContext();
            authenticationEntryPoint.commence(request, response, new BadCredentialsException("无效的token", e));
        } finally {
            BaseContext.removeCurrentId();
            // SecurityContext 由 Spring Security 的策略在请求结束清理；此处清 BaseContext 即可。
        }
    }
}
