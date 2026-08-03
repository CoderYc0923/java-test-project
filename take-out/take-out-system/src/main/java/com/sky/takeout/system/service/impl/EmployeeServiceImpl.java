package com.sky.takeout.system.service.impl;

import com.sky.takeout.common.exception.BusinessException;
import com.sky.takeout.common.result.ErrorCode;
import com.sky.takeout.pojo.entity.Employee;
import com.sky.takeout.pojo.dto.EmployeeLoginDTO;
import com.sky.takeout.system.mapper.EmployeeMapper;
import com.sky.takeout.system.service.EmployeeService;
import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

@Service
public class EmployeeServiceImpl implements EmployeeService {

    private final EmployeeMapper employeeMapper;

    public EmployeeServiceImpl(EmployeeMapper employeeMapper) {
        this.employeeMapper = employeeMapper;
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
        // 1. 根据用户名查询员工
        Employee employee = employeeMapper.selectOne(new LambdaQueryWrapper<Employee>().eq(Employee::getUsername, loginDTO.getUsername()));

        if (employee == null) {
            throw new BusinessException(ErrorCode.ERROR, "用户名或密码错误");
        }

        if (!loginDTO.getPassword().equals(employee.getPassword())) {
            throw new BusinessException(ErrorCode.ERROR, "用户名或密码错误");
        }

        if (employee.getStatus() != null && employee.getStatus() == 0) {
            throw new BusinessException(ErrorCode.ERROR, "账号已禁用");
        }

        return employee;
    }
}
