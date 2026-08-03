package com.sky.takeout.admin.controller;

import com.sky.takeout.common.result.Result;
import com.sky.takeout.pojo.entity.Employee;
import com.sky.takeout.pojo.vo.EmployeeVO;
import com.sky.takeout.system.service.EmployeeService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/employees")
public class EmployeeController {

    private final EmployeeService employeeService;

    public EmployeeController(EmployeeService employeeService) {
        this.employeeService = employeeService;
    }

    @GetMapping("/{id}")
    public Result<EmployeeVO> getById(@PathVariable Long id) {
        Employee employee = employeeService.getById(id);
        return Result.success(toVO(employee));
    }

    private static EmployeeVO toVO(Employee employee) {
        EmployeeVO vo = new EmployeeVO();
        vo.setId(employee.getId());
        vo.setName(employee.getName());
        vo.setUsername(employee.getUsername());
        vo.setPhone(employee.getPhone());
        vo.setSex(employee.getSex());
        vo.setIdNumber(employee.getIdNumber());
        vo.setStatus(employee.getStatus());
        vo.setCreateTime(employee.getCreateTime());
        vo.setUpdateTime(employee.getUpdateTime());
        vo.setCreateUser(employee.getCreateUser());
        vo.setUpdateUser(employee.getUpdateUser());
        return vo;
    }
}
