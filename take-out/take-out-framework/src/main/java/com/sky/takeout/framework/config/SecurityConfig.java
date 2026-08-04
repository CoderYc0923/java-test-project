package com.sky.takeout.framework.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import com.sky.takeout.framework.security.JwtAccessDeniedHandler;
import com.sky.takeout.framework.security.JwtAuthenticationEntryPoint;
import com.sky.takeout.framework.security.JwtAuthenticationFilter;

/**
 * 配置 Spring Security，用 BCryptPasswordEncoder 加密密码
 */
@Configuration
public class SecurityConfig {

    /**
     * 配置 PasswordEncoder，用 BCryptPasswordEncoder 加密密码
     * 
     * @return PasswordEncoder
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * 配置 AuthenticationManager，用于认证管理
     * 
     * @param authenticationConfiguration
     * @return AuthenticationManager
     * @throws Exception
     *                   把 Spring 组装好的认证管理器当成 Bean 交给业务层注入
     *                   它会自动用你已有的 EmployeeUserDetailsService + PasswordEncoder
     *                   来完成认证工作
     */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authenticationConfiguration)
            throws Exception {
        return authenticationConfiguration.getAuthenticationManager();
    }

    /**
     * 配置 JWT 认证过滤器
     * 
     * @param entryPoint
     * @param secretKey
     * @param tokenHeaderName
     * @return JwtAuthenticationFilter
     */
    @Bean
    public JwtAuthenticationFilter jwtAuthenticationFilter(
            JwtAuthenticationEntryPoint entryPoint,
            @Value("${jwt.admin-secret-key}") String secretKey,
            @Value("${jwt.admin-token-name}") String tokenHeaderName) {
        return new JwtAuthenticationFilter(entryPoint, secretKey, tokenHeaderName);
    }

    /**
     * 配置 SecurityFilterChain
     * 
     * @param http HttpSecurity 对象
     * @param filter JWT 认证过滤器
     * @param entryPoint 认证失败时，返回自定义的响应
     * @param accessDeniedHandler 权限不足时，返回自定义的响应
     * @return SecurityFilterChain 安全过滤链
     * @throws Exception 异常
     */
    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtAuthenticationFilter filter,
            JwtAuthenticationEntryPoint entryPoint,
            JwtAccessDeniedHandler accessDeniedHandler) throws Exception {
        // 禁用 CSRF 保护
        http.csrf(csrf -> csrf.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS)) // 禁用 Session 管理
                .authorizeHttpRequests(
                        auth -> auth.requestMatchers("/admin/employee/login").permitAll().anyRequest().authenticated()) // 登录接口放行，其他接口需要认证
                .exceptionHandling(
                        ex -> ex.authenticationEntryPoint(entryPoint).accessDeniedHandler(accessDeniedHandler)) // 认证失败和权限不足时，返回自定义的响应
                .addFilterBefore(filter, UsernamePasswordAuthenticationFilter.class); // 将 JWT 认证过滤器添加到 UsernamePasswordAuthenticationFilter 之前

        return http.build();
    }
}
