package com.sky.takeout.system.service;

import com.sky.takeout.pojo.entity.Employee;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.sky.takeout.pojo.dto.employee.EmployeeLoginDTO;
import com.sky.takeout.pojo.dto.employee.EmployeePageQueryDTO;
import com.sky.takeout.pojo.dto.employee.EmployeeSaveDTO;
import com.sky.takeout.pojo.dto.employee.EmployeeUpdateDTO;
import com.sky.takeout.pojo.dto.employee.EmployeeEnableOrDisableDTO;

public interface EmployeeService {
    Employee getById(Long id);

    Employee login(EmployeeLoginDTO loginDTO);

    IPage<Employee> page(EmployeePageQueryDTO pageQueryDTO);

    void save(EmployeeSaveDTO saveDTO);

    void update(EmployeeUpdateDTO updateDTO);

    void enableOrDisable(Long id, EmployeeEnableOrDisableDTO enableOrDisableDTO);
}
