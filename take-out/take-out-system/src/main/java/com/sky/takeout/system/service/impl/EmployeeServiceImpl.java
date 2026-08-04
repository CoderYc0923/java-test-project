package com.sky.takeout.system.service.impl;

import com.sky.takeout.common.exception.BusinessException;
import com.sky.takeout.common.result.ErrorCode;
import com.sky.takeout.pojo.entity.Employee;
import com.sky.takeout.pojo.dto.EmployeeLoginDTO;
import com.sky.takeout.system.mapper.EmployeeMapper;
import com.sky.takeout.system.security.EmployeeUserDetails;
import com.sky.takeout.system.service.EmployeeService;

import org.springframework.security.core.Authentication;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

@Service
public class EmployeeServiceImpl implements EmployeeService {

    private final EmployeeMapper employeeMapper;

    private final AuthenticationManager authenticationManager;

    public EmployeeServiceImpl(EmployeeMapper employeeMapper, AuthenticationManager authenticationManager) {
        this.employeeMapper = employeeMapper;
        this.authenticationManager = authenticationManager;
    }

    @Override
    public Employee getById(Long id) {
        Employee employee = employeeMapper.selectById(id);
        if (employee == null) {
            throw new BusinessException(ErrorCode.ERROR, "员工不存在");
        }
        return employee;
    }

    @Override
    public Employee login(EmployeeLoginDTO loginDTO) {
       try {
        
        // 交给认证管理器去认证，如果认证失败，会抛出异常
        Authentication authentication = authenticationManager.authenticate(
            // 把用户名和密码包装成 Authentication 对象
            new UsernamePasswordAuthenticationToken(loginDTO.getUsername(), loginDTO.getPassword())
        );

        // 获取认证后的 principal
        EmployeeUserDetails principal = (EmployeeUserDetails) authentication.getPrincipal();
        // 根据 principal 的 id 查询员工
        Employee employee = employeeMapper.selectById(principal.getId());
        if (employee == null) {
            throw new BusinessException(ErrorCode.ERROR, "用户名或密码错误");
        }

        return employee;

       } catch (BadCredentialsException e) {
        throw new BusinessException(ErrorCode.ERROR, "用户名或密码错误");
       } catch (DisabledException e) {
        throw new BusinessException(ErrorCode.ERROR, "账号已禁用");
       }
    }
}
