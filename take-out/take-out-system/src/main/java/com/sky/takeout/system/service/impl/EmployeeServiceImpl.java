package com.sky.takeout.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sky.takeout.common.exception.BusinessException;
import com.sky.takeout.common.result.ErrorCode;
import com.sky.takeout.common.constant.EmployeeConstant;
import com.sky.takeout.pojo.entity.Employee;
import com.sky.takeout.pojo.dto.employee.EmployeeEnableOrDisableDTO;
import com.sky.takeout.pojo.dto.employee.EmployeeLoginDTO;
import com.sky.takeout.pojo.dto.employee.EmployeePageQueryDTO;
import com.sky.takeout.pojo.dto.employee.EmployeeSaveDTO;
import com.sky.takeout.pojo.dto.employee.EmployeeUpdateDTO;
import com.sky.takeout.system.mapper.EmployeeMapper;
import com.sky.takeout.system.security.EmployeeUserDetails;
import com.sky.takeout.system.service.EmployeeService;

import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class EmployeeServiceImpl implements EmployeeService {

    private final EmployeeMapper employeeMapper;

    private final AuthenticationManager authenticationManager;

    private static final Logger log = LoggerFactory.getLogger(EmployeeServiceImpl.class);

    private final PasswordEncoder passwordEncoder;

    public EmployeeServiceImpl(EmployeeMapper employeeMapper, AuthenticationManager authenticationManager, PasswordEncoder passwordEncoder) {
        this.employeeMapper = employeeMapper;
        this.authenticationManager = authenticationManager;
        this.passwordEncoder = passwordEncoder;
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

    @Override
    public IPage<Employee> page(EmployeePageQueryDTO pageQueryDTO) {

        // 1. 分页参数
        int pageNum = pageQueryDTO.getPage() == null || pageQueryDTO.getPage() < 1 ? 1 : pageQueryDTO.getPage();
        int pageSize = pageQueryDTO.getPageSize() == null || pageQueryDTO.getPageSize() < 1 ? 10 : pageQueryDTO.getPageSize();

        // 2. 分页构造器
        Page<Employee> page = new Page<>(pageNum, pageSize);

        // 3. 查询条件
        LambdaQueryWrapper<Employee> wrapper = new LambdaQueryWrapper<>();
        // 姓名模糊查询
        wrapper.like(StringUtils.hasText(pageQueryDTO.getName()), Employee::getName, pageQueryDTO.getName());
        // 排序
        wrapper.orderByDesc(Employee::getCreateTime);

        return employeeMapper.selectPage(page, wrapper);
    }

    @Override
    public void save(EmployeeSaveDTO saveDTO) {
        // 1. 检查用户名是否唯一
        Long count = employeeMapper.selectCount(new LambdaQueryWrapper<Employee>().eq(Employee::getUsername, saveDTO.getUsername()));
        if (count != null && count > 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "账号已存在");
        }

        // 2. DTO 转换为 Entity
        Employee employee = new Employee();
        employee.setUsername(saveDTO.getUsername());
        employee.setName(saveDTO.getName());
        employee.setPhone(saveDTO.getPhone());
        employee.setSex(saveDTO.getSex());
        // 空字符串转换为 null
        employee.setIdNumber(normalizeIdNumber(saveDTO.getIdNumber()));

        // 3. 补充前端不传字段
        employee.setPassword(passwordEncoder.encode(EmployeeConstant.DEFAULT_PASSWORD));
        employee.setStatus(1); // 默认账户启用

        // 4. 保存员工信息到数据库
        employeeMapper.insert(employee);
        log.info("新增员工成功, username={}", employee.getUsername());

    }

    @Override
    public void update(EmployeeUpdateDTO updateDTO) {
        // 1. 检查账户是否存在
        Employee employee = getById(updateDTO.getId());

        // 2. DTO 转换为 Entity
        employee.setName(updateDTO.getName());
        employee.setPhone(updateDTO.getPhone());
        employee.setSex(updateDTO.getSex());

        employee.setIdNumber(normalizeIdNumber(updateDTO.getIdNumber()));

        // 3. 更新员工信息到数据库
        employeeMapper.updateById(employee);
        log.info("更新员工成功, username={}", employee.getUsername());
    }

    private String normalizeIdNumber(String idNumber) {
        if (idNumber == null || idNumber.isBlank()) {
            return null;
        }
        return idNumber.trim();
    }

    @Override
    public void enableOrDisable(Long id, EmployeeEnableOrDisableDTO enableOrDisableDTO) {
        // 1. 检查员工是否存在
        Employee employee = getById(id);
        // 2. 更新员工状态
        employee.setStatus(enableOrDisableDTO.getStatus());
        employeeMapper.updateById(employee);
        log.info("{}{username}员工成功", enableOrDisableDTO.getStatus() == 1 ? "启用" : "禁用", employee.getUsername());
    }
}
