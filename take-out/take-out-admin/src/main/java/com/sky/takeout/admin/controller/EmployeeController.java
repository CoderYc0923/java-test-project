package com.sky.takeout.admin.controller;

import com.sky.takeout.common.result.Result;
import com.sky.takeout.pojo.entity.Employee;
import com.sky.takeout.pojo.vo.employee.EmployeeVO;
import com.sky.takeout.pojo.vo.employee.EmployeeLoginVO;
import com.sky.takeout.pojo.dto.employee.EmployeeLoginDTO;
import com.sky.takeout.pojo.dto.employee.EmployeeSaveDTO;
import com.sky.takeout.pojo.dto.employee.EmployeeUpdateDTO;
import com.sky.takeout.pojo.dto.employee.EmployeePageQueryDTO;
import com.sky.takeout.pojo.dto.employee.EmployeeEnableOrDisableDTO;
import com.sky.takeout.system.service.EmployeeService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

import com.sky.takeout.common.jwt.JwtUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.sky.takeout.common.constant.JwtClaimsConstant;
import java.util.Map;
import java.util.HashMap;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PutMapping;


@Tag(name = "员工管理")
@RestController
@RequestMapping("/admin/employee")
public class EmployeeController {

    private final EmployeeService employeeService;

    public EmployeeController(EmployeeService employeeService) {
        this.employeeService = employeeService;
    }

    @Value("${jwt.admin-secret-key}")
    private String secretKey;

    @Value("${jwt.admin-ttl}")
    private long ttl;

    @Operation(summary = "员工登录")
    @PostMapping("/login")
    public Result<EmployeeLoginVO> login(@RequestBody EmployeeLoginDTO loginDTO) {
        // 1. 登录
        Employee employee = employeeService.login(loginDTO);

        // 2. 创建 JWT
        Map<String, Object> claims = new HashMap<>();
        claims.put(JwtClaimsConstant.EMP_ID, employee.getId());
        String token = JwtUtil.createToken(secretKey, ttl, claims);

        // 3. 返回结果
        EmployeeLoginVO vo = EmployeeLoginVO.builder()
            .id(employee.getId())
            .username(employee.getUsername())
            .name(employee.getName())
            .token(token)
            .build();

        return Result.success(vo);
    }

    @Operation(summary = "分页查询员工")
    @GetMapping("/page")
    public Result<IPage<EmployeeVO>> page(EmployeePageQueryDTO pageQueryDTO) {
        IPage<Employee> page = employeeService.page(pageQueryDTO);
        IPage<EmployeeVO> voPage = page.convert(EmployeeController::toVO);
        return Result.success(voPage);
    }

    @Operation(summary = "新增员工")
    @PostMapping
    public Result<Void> save( @Valid @RequestBody EmployeeSaveDTO saveDTO) {
        employeeService.save(saveDTO);
        return Result.success();
    }

    @Operation(summary = "编辑员工")
    @PutMapping
    public Result<Void> update(@Valid @RequestBody EmployeeUpdateDTO updateDTO) {
        employeeService.update(updateDTO);
        return Result.success();
    }

    @Operation(summary = "启用禁用员工")
    @PostMapping("/{id}/status")
    public Result<Void> enableOrDisable(@PathVariable Long id, @RequestBody EmployeeEnableOrDisableDTO enableOrDisableDTO) {
        employeeService.enableOrDisable(id, enableOrDisableDTO);
        return Result.success();
    }

    @Operation(summary = "根据id查询员工")
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
