package com.sky.takeout.system.security;

import com.sky.takeout.pojo.entity.Employee;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.Collections;

/**
 * 员工用户详情，用于 Spring Security 认证。
 */
public class EmployeeUserDetails implements UserDetails {

    private final Long id;
    private final String username;
    private final String password;
    private final boolean enabled;

    public EmployeeUserDetails(Long id, String username, String password, boolean enabled) {
        this.id = id;
        this.username = username;
        this.password = password;
        this.enabled = enabled;
    }

    public static EmployeeUserDetails fromEmployee(Employee employee) {
        boolean enabled = employee.getStatus() == null || employee.getStatus() != 0;
        return new EmployeeUserDetails(
                employee.getId(),
                employee.getUsername(),
                employee.getPassword(),
                enabled
        );
    }

    /** JWT 认证后重建 principal，不承载密码 */
    public static EmployeeUserDetails forId(Long id) {
        return new EmployeeUserDetails(id, String.valueOf(id), "", true);
    }

    public Long getId() {
        return id;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return Collections.emptyList();
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }
}