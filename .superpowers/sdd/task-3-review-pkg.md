# Task 3 review package


## take-out-system\src\main\java\com\sky\takeout\system\mapper\EmployeeMapper.java

package com.sky.takeout.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sky.takeout.pojo.entity.Employee;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface EmployeeMapper extends BaseMapper<Employee> {
}

## take-out-system\src\main\java\com\sky\takeout\system\service\EmployeeService.java

package com.sky.takeout.system.service;

import com.sky.takeout.pojo.entity.Employee;

public interface EmployeeService {
    Employee getById(Long id);
}

## take-out-system\src\main\java\com\sky\takeout\system\service\impl\EmployeeServiceImpl.java

package com.sky.takeout.system.service.impl;

import com.sky.takeout.common.exception.BusinessException;
import com.sky.takeout.common.result.ErrorCode;
import com.sky.takeout.pojo.entity.Employee;
import com.sky.takeout.system.mapper.EmployeeMapper;
import com.sky.takeout.system.service.EmployeeService;
import org.springframework.stereotype.Service;

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
            throw new BusinessException(ErrorCode.ERROR, "鍛樺伐涓嶅瓨鍦?);
        }
        return employee;
    }
}

## take-out-framework\src\main\java\com\sky\takeout\framework\config\MybatisPlusConfig.java

package com.sky.takeout.framework.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@MapperScan("com.sky.takeout.system.mapper")
public class MybatisPlusConfig {
}
