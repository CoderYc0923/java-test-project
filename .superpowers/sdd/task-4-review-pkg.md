# Task 4 review package

 M take-out-admin/src/main/resources/application.yml
?? take-out-admin/src/main/java/com/sky/takeout/admin/controller/EmployeeController.java
?? take-out-admin/src/test/java/com/sky/takeout/admin/controller/EmployeeControllerTest.java

## application.yml

spring:
  application:
    name: take-out-admin
  datasource:
    url: jdbc:mysql://127.0.0.1:3307/take_out?useUnicode=true&characterEncoding=utf-8&serverTimezone=Asia/Shanghai
    username: takeout_rw
    password: TakeoutRw@123
    driver-class-name: com.mysql.cj.jdbc.Driver

mybatis-plus:
  configuration:
    map-underscore-to-camel-case: true
  global-config:
    db-config:
      id-type: auto

## EmployeeController.java

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

## EmployeeControllerTest.java

package com.sky.takeout.admin.controller;

import com.sky.takeout.common.exception.BusinessException;
import com.sky.takeout.common.result.ErrorCode;
import com.sky.takeout.framework.web.GlobalExceptionHandler;
import com.sky.takeout.pojo.entity.Employee;
import com.sky.takeout.system.service.EmployeeService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = EmployeeController.class)
@Import(GlobalExceptionHandler.class)
class EmployeeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private EmployeeService employeeService;

    @Test
    void getById_returnsEmployeeWithoutPassword() throws Exception {
        Employee employee = new Employee();
        employee.setId(1L);
        employee.setName("绠＄悊鍛?);
        employee.setUsername("admin");
        employee.setPassword("123456");
        employee.setPhone("13812312312");
        employee.setSex("1");
        employee.setIdNumber("110101199001010047");
        employee.setStatus(1);
        employee.setCreateTime(LocalDateTime.of(2022, 2, 15, 15, 51, 20));
        employee.setUpdateTime(LocalDateTime.of(2022, 2, 17, 9, 16, 20));
        employee.setCreateUser(10L);
        employee.setUpdateUser(1L);
        when(employeeService.getById(1L)).thenReturn(employee);

        mockMvc.perform(get("/api/employees/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCode.SUCCESS))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.username").value("admin"))
                .andExpect(jsonPath("$.data.password").doesNotExist());
    }

    @Test
    void getById_whenMissing_returnsBusinessError() throws Exception {
        when(employeeService.getById(99999L))
                .thenThrow(new BusinessException(ErrorCode.ERROR, "鍛樺伐涓嶅瓨鍦?));

        mockMvc.perform(get("/api/employees/99999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCode.ERROR))
                .andExpect(jsonPath("$.msg").value("鍛樺伐涓嶅瓨鍦?));
    }
}
