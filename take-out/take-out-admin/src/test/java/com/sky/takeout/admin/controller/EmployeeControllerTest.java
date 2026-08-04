package com.sky.takeout.admin.controller;

import com.sky.takeout.common.exception.BusinessException;
import com.sky.takeout.common.result.ErrorCode;
import com.sky.takeout.framework.web.GlobalExceptionHandler;
import com.sky.takeout.pojo.entity.Employee;
import com.sky.takeout.system.service.EmployeeService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = EmployeeController.class) // 只测试 EmployeeController 类
@AutoConfigureMockMvc(addFilters = false) // 禁用 MockMvc 的过滤器
@Import(GlobalExceptionHandler.class) // 导入 GlobalExceptionHandler 类
class EmployeeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private EmployeeService employeeService;

    @Test
    void getById_returnsEmployeeWithoutPassword() throws Exception {
        Employee employee = new Employee();
        employee.setId(1L);
        employee.setName("管理员");
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

        mockMvc.perform(get("/admin/employee/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCode.SUCCESS))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.username").value("admin"))
                .andExpect(jsonPath("$.data.password").doesNotExist());
    }

    @Test
    void getById_whenMissing_returnsBusinessError() throws Exception {
        when(employeeService.getById(99999L))
                .thenThrow(new BusinessException(ErrorCode.ERROR, "员工不存在"));

        mockMvc.perform(get("/admin/employee/99999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCode.ERROR))
                .andExpect(jsonPath("$.msg").value("员工不存在"));
    }
}
