package com.sky.takeout.system.service;

import com.sky.takeout.pojo.entity.Employee;

import com.sky.takeout.pojo.dto.EmployeeLoginDTO;

public interface EmployeeService {
    Employee getById(Long id);

    Employee login(EmployeeLoginDTO loginDTO);
}
