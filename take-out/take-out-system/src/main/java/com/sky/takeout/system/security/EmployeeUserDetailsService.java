package com.sky.takeout.system.security;

import org.springframework.stereotype.Service;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import com.sky.takeout.system.mapper.EmployeeMapper;
import com.sky.takeout.pojo.entity.Employee;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

/**
 * 实现 UserDetailsService，用 EmployeeMapper 按用户名查库，转成 EmployeeUserDetails。
 * 它用 EmployeeUserDetails 做 principal，和 JWT 里的 payload 一致。
 */
@Service
public class EmployeeUserDetailsService implements UserDetailsService {
    private final EmployeeMapper employeeMapper;

    public EmployeeUserDetailsService(EmployeeMapper employeeMapper) {
        this.employeeMapper = employeeMapper;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        Employee employee = employeeMapper.selectOne(
            new LambdaQueryWrapper<Employee>().eq(Employee::getUsername, username)
        );

        if (employee == null) {
            throw new UsernameNotFoundException("用户不存在");
        }

        return EmployeeUserDetails.fromEmployee(employee);
    }
}
