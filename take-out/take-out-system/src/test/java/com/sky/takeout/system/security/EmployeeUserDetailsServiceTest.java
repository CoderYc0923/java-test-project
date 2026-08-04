package com.sky.takeout.system.security;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sky.takeout.pojo.entity.Employee;
import com.sky.takeout.system.mapper.EmployeeMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmployeeUserDetailsServiceTest {

    @Mock
    private EmployeeMapper employeeMapper;

    @InjectMocks
    private EmployeeUserDetailsService service;

    /**
     * 测试 loadUserByUsername 方法，返回 EmployeeUserDetails 对象
     */
    @Test
    void loadUserByUsername_returnsDetails() {
        Employee employee = new Employee();
        employee.setId(1L);
        employee.setUsername("admin");
        employee.setPassword("password");
        employee.setStatus(1);
        when(employeeMapper.selectOne(ArgumentMatchers.<LambdaQueryWrapper<Employee>>any()))
                .thenReturn(employee);

        var details = (EmployeeUserDetails) service.loadUserByUsername("admin");
        assertEquals(1L, details.getId());
        assertEquals("admin", details.getUsername());
        assertEquals("password", details.getPassword());
        assertTrue(details.isEnabled());
    }

    /**
     * 测试 loadUserByUsername 方法，当用户不存在时抛出 UsernameNotFoundException
     */
    @Test
    void loadUserByUsername_whenMissing_throws() {
        when(employeeMapper.selectOne(ArgumentMatchers.<LambdaQueryWrapper<Employee>>any()))
                .thenReturn(null);
        assertThrows(UsernameNotFoundException.class,
                () -> service.loadUserByUsername("nope"));
    }

    /**
     * 测试 loadUserByUsername 方法，当用户被禁用时返回的 EmployeeUserDetails 对象的 isEnabled 方法返回 false
     */
    @Test
    void loadUserByUsername_whenDisabled_isNotEnabled() {
        Employee employee = new Employee();
        employee.setId(2L);
        employee.setUsername("bad");
        employee.setPassword("hash");
        employee.setStatus(0);
        when(employeeMapper.selectOne(ArgumentMatchers.<LambdaQueryWrapper<Employee>>any()))
                .thenReturn(employee);

        var details = (EmployeeUserDetails) service.loadUserByUsername("bad");
        assertFalse(details.isEnabled());
    }
}
