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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class EmployeeServiceImpl implements EmployeeService {

    private final EmployeeMapper employeeMapper;

    private final AuthenticationManager authenticationManager;

    private static final Logger log = LoggerFactory.getLogger(EmployeeServiceImpl.class);

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
            log.error("登录失败:用户不存在, empId={}", principal.getId());
            throw new BusinessException(ErrorCode.ERROR, "用户名或密码错误");
        }

        log.info("登录成功, empId={}, username={}", employee.getId(), employee.getUsername());

        return employee;

       } catch (BadCredentialsException e) {
        log.error("登录失败:用户名或密码错误, username={}", loginDTO.getUsername());
        throw new BusinessException(ErrorCode.ERROR, "用户名或密码错误");
       } catch (DisabledException e) {
        log.error("登录失败:账号已禁用, username={}", loginDTO.getUsername());
        throw new BusinessException(ErrorCode.ERROR, "账号已禁用");
       }
    }
}
