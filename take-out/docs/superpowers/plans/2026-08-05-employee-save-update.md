# 员工新增与编辑 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 实现管理端员工新增（`POST /admin/employee`）与编辑（`PUT /admin/employee`），含 Bean Validation、默认密码 BCrypt、审计字段自动填充，以及身份证号可空。

**Architecture:** 沿用现有分层：Controller（admin）收 `@Valid` DTO → Service（system）做唯一性/映射/加密 → Mapper（MyBatis-Plus）落库；framework 提供校验异常处理与 `MetaObjectHandler`；pojo 放 Save/Update DTO 与 Entity fill 注解。编辑不改 username/password/status。

**Tech Stack:** Java 17、Spring Boot 4.1.0、MyBatis-Plus 3.5.17、Spring Security `PasswordEncoder`（BCrypt）、Jakarta Bean Validation、JUnit 5、MockMvc、Mockito

**Spec:** `docs/superpowers/specs/2026-08-05-employee-save-update-design.md`

## Global Constraints

- 依赖方向：`admin → framework → system → pojo → common`，禁止反向依赖
- Spring Boot parent：`4.1.0`；Java：`17`
- 性别编码：`"1"`=男，`"0"`=女（字符串，与种子数据一致）
- 默认密码明文常量：`"123456"`，入库必须 BCrypt
- 编辑禁止修改：`username`、`password`、`status`
- 身份证号可选；blank 规范化为 `null`；有值时校验 18 位格式
- 业务/校验错误：HTTP 200 + `Result.code != 1`（与现有全局异常风格一致）
- 本期不做：分页、启用禁用、改密、删除、RBAC、前端
- Commit：仅在用户明确要求时执行；计划中的 commit 步骤默认跳过，除非用户授权
- 工作目录：仓库内 `take-out/`（所有相对路径相对该目录）

---

## File Structure

| 路径 | 职责 |
|------|------|
| `take-out-common/.../constant/PasswordConstant.java` | `DEFAULT_PASSWORD = "123456"` |
| `take-out-pojo/pom.xml` | 增加 `jakarta.validation-api` |
| `take-out-pojo/.../dto/EmployeeSaveDTO.java` | 新增入参 + 校验注解 |
| `take-out-pojo/.../dto/EmployeeUpdateDTO.java` | 编辑入参（含 id，无 username）+ 校验 |
| `take-out-pojo/.../entity/Employee.java` | 审计 fill 注解；`idNumber` 的 `updateStrategy=ALWAYS` |
| `take-out-framework/pom.xml` | 增加 `spring-boot-starter-validation` |
| `take-out-framework/.../web/GlobalExceptionHandler.java` | 处理 `MethodArgumentNotValidException` |
| `take-out-framework/.../mybatis/MyMetaObjectHandler.java` | insert/update 审计填充 |
| `take-out-system/.../service/EmployeeService.java` | 增加 `void save(...)` / `void update(...)` |
| `take-out-system/.../service/impl/EmployeeServiceImpl.java` | 实现 save/update |
| `take-out-system/.../service/impl/EmployeeServiceImplTest.java` | Service 单测（Mock Mapper + PasswordEncoder） |
| `take-out-admin/.../controller/EmployeeController.java` | POST/PUT 接口 |
| `take-out-admin/.../controller/EmployeeControllerTest.java` | 补充 MockMvc 用例 |
| `sky.sql` | `id_number` 改为可空 |
| `docs/sql/alter-employee-id-number-nullable.sql` | 已有库迁移脚本 |

---

### Task 1: Validation 依赖与全局校验异常处理

**Files:**
- Modify: `take-out-pojo/pom.xml`
- Modify: `take-out-framework/pom.xml`
- Modify: `take-out-framework/src/main/java/com/sky/takeout/framework/web/GlobalExceptionHandler.java`
- Test: 本 Task 用编译验证；行为测试在 Task 6 的 Controller 测试中覆盖

**Interfaces:**
- Consumes: 现有 `Result.error(Integer, String)`、`ErrorCode.BAD_REQUEST`
- Produces: 运行时启用 Bean Validation；校验失败返回 `code=400` + 第一条错误消息

- [ ] **Step 1: `take-out-pojo/pom.xml` 增加 validation API**

在现有 dependencies 内追加（注解编译期需要；版本由 Spring Boot BOM 管理则可不写 version；若父 BOM 未管理则写）：

```xml
<dependency>
    <groupId>jakarta.validation</groupId>
    <artifactId>jakarta.validation-api</artifactId>
</dependency>
```

- [ ] **Step 2: `take-out-framework/pom.xml` 增加 validation starter**

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-validation</artifactId>
</dependency>
```

说明：admin 经 framework 传递获得校验实现（Hibernate Validator），`@Valid` 才会生效。

- [ ] **Step 3: 扩展 `GlobalExceptionHandler`**

在现有 `handleBusinessException` 旁增加：

```java
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.validation.FieldError;

@ExceptionHandler(MethodArgumentNotValidException.class)
public Result<Void> handleMethodArgumentNotValid(MethodArgumentNotValidException ex) {
    FieldError fieldError = ex.getBindingResult().getFieldError();
    String message = fieldError != null ? fieldError.getDefaultMessage() : "参数校验失败";
    return Result.error(ErrorCode.BAD_REQUEST, message);
}
```

保留现有 `BusinessException` / `Exception` 处理器不变。

- [ ] **Step 4: 编译验证**

Run:

```bash
mvn -pl take-out-framework -am compile -q
```

Expected: BUILD SUCCESS

- [ ] **Step 5: Commit（默认跳过，除非用户授权）**

```bash
git add take-out-pojo/pom.xml take-out-framework/pom.xml take-out-framework/src/main/java/com/sky/takeout/framework/web/GlobalExceptionHandler.java
git commit -m "feat: 接入 Bean Validation 与校验异常处理"
```

---

### Task 2: PasswordConstant、DTO、Entity 填充注解

**Files:**
- Create: `take-out-common/src/main/java/com/sky/takeout/common/constant/PasswordConstant.java`
- Create: `take-out-pojo/src/main/java/com/sky/takeout/pojo/dto/EmployeeSaveDTO.java`
- Create: `take-out-pojo/src/main/java/com/sky/takeout/pojo/dto/EmployeeUpdateDTO.java`
- Modify: `take-out-pojo/src/main/java/com/sky/takeout/pojo/entity/Employee.java`

**Interfaces:**
- Consumes: Task 1 的 `jakarta.validation-api`
- Produces:
  - `PasswordConstant.DEFAULT_PASSWORD`
  - `EmployeeSaveDTO` / `EmployeeUpdateDTO` 字段与校验消息
  - `Employee` 上 insert/update fill + `idNumber` 更新策略 ALWAYS

- [ ] **Step 1: 创建 `PasswordConstant`**

```java
package com.sky.takeout.common.constant;

public final class PasswordConstant {
    public static final String DEFAULT_PASSWORD = "123456";

    private PasswordConstant() {
    }
}
```

- [ ] **Step 2: 创建 `EmployeeSaveDTO`**

```java
package com.sky.takeout.pojo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "新增员工请求")
public class EmployeeSaveDTO {

    @NotBlank(message = "账号不能为空")
    @Size(min = 3, max = 32, message = "账号长度须为 3-32")
    @Schema(description = "账号", example = "zhangsan")
    private String username;

    @NotBlank(message = "员工姓名不能为空")
    @Size(max = 32, message = "员工姓名长度不能超过 32")
    @Schema(description = "员工姓名", example = "张三")
    private String name;

    @NotBlank(message = "手机号不能为空")
    @Pattern(regexp = "^1\\d{10}$", message = "手机号须为 11 位合法号码")
    @Schema(description = "手机号", example = "13812345678")
    private String phone;

    @NotBlank(message = "性别不能为空")
    @Pattern(regexp = "^[01]$", message = "性别须为 0 或 1")
    @Schema(description = "性别：1男 0女", example = "1")
    private String sex;

    /** 可选；空串在 Service 中规范化为 null。regexp 允许空串。 */
    @Pattern(
            regexp = "^$|^[1-9]\\d{5}(18|19|20)\\d{2}(0[1-9]|1[0-2])(0[1-9]|[12]\\d|3[01])\\d{3}[\\dXx]$",
            message = "身份证号须为合法 18 位号码")
    @Schema(description = "身份证号（可选）", example = "110101199001010047")
    private String idNumber;
}
```

- [ ] **Step 3: 创建 `EmployeeUpdateDTO`**

```java
package com.sky.takeout.pojo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "编辑员工请求")
public class EmployeeUpdateDTO {

    @NotNull(message = "员工 id 不能为空")
    @Schema(description = "员工 id", example = "1")
    private Long id;

    @NotBlank(message = "员工姓名不能为空")
    @Size(max = 32, message = "员工姓名长度不能超过 32")
    private String name;

    @NotBlank(message = "手机号不能为空")
    @Pattern(regexp = "^1\\d{10}$", message = "手机号须为 11 位合法号码")
    private String phone;

    @NotBlank(message = "性别不能为空")
    @Pattern(regexp = "^[01]$", message = "性别须为 0 或 1")
    private String sex;

    @Pattern(
            regexp = "^$|^[1-9]\\d{5}(18|19|20)\\d{2}(0[1-9]|1[0-2])(0[1-9]|[12]\\d|3[01])\\d{3}[\\dXx]$",
            message = "身份证号须为合法 18 位号码")
    private String idNumber;
}
```

- [ ] **Step 4: 更新 `Employee` 实体注解**

在现有字段上增加（保留 Lombok `@Data` / `@TableName` / `@TableId`）：

```java
import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;

// ...

@TableField(fill = FieldFill.INSERT)
private LocalDateTime createTime;

@TableField(fill = FieldFill.INSERT_UPDATE)
private LocalDateTime updateTime;

@TableField(fill = FieldFill.INSERT)
private Long createUser;

@TableField(fill = FieldFill.INSERT_UPDATE)
private Long updateUser;

/** ALWAYS：允许把身份证更新为 null（清空） */
@TableField(updateStrategy = FieldStrategy.ALWAYS)
private String idNumber;
```

注意：把原来的 `private String idNumber;` 换成带 `@TableField(updateStrategy = FieldStrategy.ALWAYS)` 的版本；其他业务字段不变。`password` / `username` / `status` 不要加 ALWAYS。

- [ ] **Step 5: 编译**

Run:

```bash
mvn -pl take-out-pojo,take-out-common -am compile -q
```

Expected: BUILD SUCCESS

- [ ] **Step 6: Commit（默认跳过）**

```bash
git add take-out-common/src/main/java/com/sky/takeout/common/constant/PasswordConstant.java \
  take-out-pojo/src/main/java/com/sky/takeout/pojo/dto/EmployeeSaveDTO.java \
  take-out-pojo/src/main/java/com/sky/takeout/pojo/dto/EmployeeUpdateDTO.java \
  take-out-pojo/src/main/java/com/sky/takeout/pojo/entity/Employee.java
git commit -m "feat: 新增员工 Save/Update DTO 与审计字段注解"
```

---

### Task 3: MetaObjectHandler 审计自动填充

**Files:**
- Create: `take-out-framework/src/main/java/com/sky/takeout/framework/mybatis/MyMetaObjectHandler.java`

**Interfaces:**
- Consumes: `BaseContext.getCurrentId()`；Entity 上 `FieldFill` 注解（Task 2）
- Produces: insert 时填充 create/update 四字段；update 时填充 updateTime/updateUser

- [ ] **Step 1: 创建 `MyMetaObjectHandler`**

```java
package com.sky.takeout.framework.mybatis;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.sky.takeout.common.context.BaseContext;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class MyMetaObjectHandler implements MetaObjectHandler {

    @Override
    public void insertFill(MetaObject metaObject) {
        LocalDateTime now = LocalDateTime.now();
        Long currentId = BaseContext.getCurrentId();
        strictInsertFill(metaObject, "createTime", LocalDateTime.class, now);
        strictInsertFill(metaObject, "updateTime", LocalDateTime.class, now);
        if (currentId != null) {
            strictInsertFill(metaObject, "createUser", Long.class, currentId);
            strictInsertFill(metaObject, "updateUser", Long.class, currentId);
        }
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        LocalDateTime now = LocalDateTime.now();
        Long currentId = BaseContext.getCurrentId();
        strictUpdateFill(metaObject, "updateTime", LocalDateTime.class, now);
        if (currentId != null) {
            strictUpdateFill(metaObject, "updateUser", Long.class, currentId);
        }
    }
}
```

说明：与 `MybatisPlusConfig` 同属 framework，由 Boot 组件扫描加载（确认 `TakeOutAdminApplication` 的 `@SpringBootApplication` 扫描根包为 `com.sky.takeout`）。

- [ ] **Step 2: 确认启动类扫描范围**

打开 `take-out-admin/src/main/java/com/sky/takeout/admin/TakeOutAdminApplication.java`，确认包名为 `com.sky.takeout.admin` 且能扫描到 `com.sky.takeout.framework`（通常父包 `com.sky.takeout` 已覆盖；若使用 `@SpringBootApplication(scanBasePackages = "com.sky.takeout")` 则满足）。

若当前仅扫描 `admin` 子包，则改为：

```java
@SpringBootApplication(scanBasePackages = "com.sky.takeout")
```

（以仓库实际文件为准；若已是全包扫描则不要改。）

- [ ] **Step 3: 编译**

Run:

```bash
mvn -pl take-out-framework -am compile -q
```

Expected: BUILD SUCCESS

- [ ] **Step 4: Commit（默认跳过）**

```bash
git add take-out-framework/src/main/java/com/sky/takeout/framework/mybatis/MyMetaObjectHandler.java
git commit -m "feat: 增加 MyBatis-Plus 审计字段自动填充"
```

---

### Task 4: 数据库 `id_number` 可空

**Files:**
- Modify: `sky.sql`（employee 表 `id_number` 定义）
- Create: `docs/sql/alter-employee-id-number-nullable.sql`

**Interfaces:**
- Consumes: 无
- Produces: 新库与已有库均允许 `id_number` 为 NULL

- [ ] **Step 1: 修改 `sky.sql` 中 employee 建表**

将：

```sql
`id_number` varchar(18) COLLATE utf8_bin NOT NULL COMMENT '身份证号',
```

改为：

```sql
`id_number` varchar(18) COLLATE utf8_bin DEFAULT NULL COMMENT '身份证号',
```

（仅改 employee 表这一处；不要误改其他表同名列。）

- [ ] **Step 2: 新增迁移脚本 `docs/sql/alter-employee-id-number-nullable.sql`**

```sql
-- 已有库：员工身份证号改为可空（新增/编辑员工可选填写）
ALTER TABLE employee MODIFY COLUMN id_number varchar(18) NULL COMMENT '身份证号';
```

- [ ] **Step 3: 对本地已有库执行迁移**

Run（按本机 Docker/MySQL 实际连接调整；与 `application.yml` 一致时示例）：

```bash
docker exec -i <mysql容器名> mysql -utakeout_rw -pTakeoutRw@123 take_out < docs/sql/alter-employee-id-number-nullable.sql
```

或用客户端执行该 `ALTER`。Expected: 语句成功；`SHOW COLUMNS FROM employee LIKE 'id_number';` 显示 `Null=YES`。

- [ ] **Step 4: Commit（默认跳过）**

```bash
git add sky.sql docs/sql/alter-employee-id-number-nullable.sql
git commit -m "chore: employee.id_number 改为可空"
```

---

### Task 5: EmployeeService.save / update（TDD）

**Files:**
- Modify: `take-out-system/src/main/java/com/sky/takeout/system/service/EmployeeService.java`
- Modify: `take-out-system/src/main/java/com/sky/takeout/system/service/impl/EmployeeServiceImpl.java`
- Create: `take-out-system/src/test/java/com/sky/takeout/system/service/impl/EmployeeServiceImplTest.java`

**Interfaces:**
- Consumes: `EmployeeSaveDTO`、`EmployeeUpdateDTO`、`PasswordConstant`、`PasswordEncoder`、`EmployeeMapper`、`BusinessException`、`ErrorCode`
- Produces:
  - `void save(EmployeeSaveDTO dto)`
  - `void update(EmployeeUpdateDTO dto)`

- [ ] **Step 1: 扩展 `EmployeeService` 接口**

```java
package com.sky.takeout.system.service;

import com.sky.takeout.pojo.dto.EmployeeLoginDTO;
import com.sky.takeout.pojo.dto.EmployeeSaveDTO;
import com.sky.takeout.pojo.dto.EmployeeUpdateDTO;
import com.sky.takeout.pojo.entity.Employee;

public interface EmployeeService {
    Employee getById(Long id);

    Employee login(EmployeeLoginDTO loginDTO);

    void save(EmployeeSaveDTO dto);

    void update(EmployeeUpdateDTO dto);
}
```

- [ ] **Step 2: 写失败用例 `EmployeeServiceImplTest`（先红）**

```java
package com.sky.takeout.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sky.takeout.common.constant.PasswordConstant;
import com.sky.takeout.common.exception.BusinessException;
import com.sky.takeout.common.result.ErrorCode;
import com.sky.takeout.pojo.dto.EmployeeSaveDTO;
import com.sky.takeout.pojo.dto.EmployeeUpdateDTO;
import com.sky.takeout.pojo.entity.Employee;
import com.sky.takeout.system.mapper.EmployeeMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmployeeServiceImplTest {

    @Mock
    private EmployeeMapper employeeMapper;
    @Mock
    private AuthenticationManager authenticationManager;
    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private EmployeeServiceImpl employeeService;

    @Test
    void save_whenUsernameExists_throwsConflict() {
        EmployeeSaveDTO dto = new EmployeeSaveDTO();
        dto.setUsername("admin");
        dto.setName("张三");
        dto.setPhone("13812345678");
        dto.setSex("1");

        when(employeeMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);

        BusinessException ex = assertThrows(BusinessException.class, () -> employeeService.save(dto));
        assertEquals(ErrorCode.CONFLICT, ex.getCode());
        assertEquals("账号已存在", ex.getMessage());
        verify(employeeMapper, never()).insert(any());
    }

    @Test
    void save_encodesDefaultPassword_andInserts() {
        EmployeeSaveDTO dto = new EmployeeSaveDTO();
        dto.setUsername("zhangsan");
        dto.setName("张三");
        dto.setPhone("13812345678");
        dto.setSex("1");
        dto.setIdNumber("  ");

        when(employeeMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(passwordEncoder.encode(PasswordConstant.DEFAULT_PASSWORD)).thenReturn("bcrypt-hash");
        when(employeeMapper.insert(any(Employee.class))).thenReturn(1);

        employeeService.save(dto);

        ArgumentCaptor<Employee> captor = ArgumentCaptor.forClass(Employee.class);
        verify(employeeMapper).insert(captor.capture());
        Employee saved = captor.getValue();
        assertEquals("zhangsan", saved.getUsername());
        assertEquals("bcrypt-hash", saved.getPassword());
        assertEquals(1, saved.getStatus());
        assertNull(saved.getIdNumber());
        verify(passwordEncoder).encode("123456");
    }

    @Test
    void update_doesNotChangeUsernamePasswordStatus() {
        Employee existing = new Employee();
        existing.setId(2L);
        existing.setUsername("zhangsan");
        existing.setPassword("old-hash");
        existing.setStatus(1);
        existing.setName("旧名");
        existing.setPhone("13800000000");
        existing.setSex("1");
        existing.setIdNumber("110101199001010047");

        when(employeeMapper.selectById(2L)).thenReturn(existing);
        when(employeeMapper.updateById(any(Employee.class))).thenReturn(1);

        EmployeeUpdateDTO dto = new EmployeeUpdateDTO();
        dto.setId(2L);
        dto.setName("新名");
        dto.setPhone("13812345678");
        dto.setSex("0");
        dto.setIdNumber("");

        employeeService.update(dto);

        ArgumentCaptor<Employee> captor = ArgumentCaptor.forClass(Employee.class);
        verify(employeeMapper).updateById(captor.capture());
        Employee updated = captor.getValue();
        assertEquals("zhangsan", updated.getUsername());
        assertEquals("old-hash", updated.getPassword());
        assertEquals(1, updated.getStatus());
        assertEquals("新名", updated.getName());
        assertEquals("0", updated.getSex());
        assertNull(updated.getIdNumber());
    }

    @Test
    void update_whenMissing_throws() {
        when(employeeMapper.selectById(999L)).thenReturn(null);
        EmployeeUpdateDTO dto = new EmployeeUpdateDTO();
        dto.setId(999L);
        dto.setName("新名");
        dto.setPhone("13812345678");
        dto.setSex("1");

        BusinessException ex = assertThrows(BusinessException.class, () -> employeeService.update(dto));
        assertEquals("员工不存在", ex.getMessage());
    }
}
```

说明：若 `selectCount` 泛型告警，可用 `@SuppressWarnings("unchecked")` 标在测试类或方法上。`EmployeeServiceImpl` 构造器稍后会增加 `PasswordEncoder` 参数，与 `@InjectMocks` 对齐。

- [ ] **Step 3: 运行测试确认失败**

Run:

```bash
mvn -pl take-out-system -am test -Dtest=EmployeeServiceImplTest -q
```

Expected: FAIL（接口方法不存在 / 实现未完成 / 构造器缺 PasswordEncoder）

- [ ] **Step 4: 实现 `EmployeeServiceImpl`**

将实现类改为注入 `PasswordEncoder`，并实现 `save` / `update`：

```java
package com.sky.takeout.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sky.takeout.common.constant.PasswordConstant;
import com.sky.takeout.common.exception.BusinessException;
import com.sky.takeout.common.result.ErrorCode;
import com.sky.takeout.pojo.dto.EmployeeLoginDTO;
import com.sky.takeout.pojo.dto.EmployeeSaveDTO;
import com.sky.takeout.pojo.dto.EmployeeUpdateDTO;
import com.sky.takeout.pojo.entity.Employee;
import com.sky.takeout.system.mapper.EmployeeMapper;
import com.sky.takeout.system.security.EmployeeUserDetails;
import com.sky.takeout.system.service.EmployeeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class EmployeeServiceImpl implements EmployeeService {

    private static final Logger log = LoggerFactory.getLogger(EmployeeServiceImpl.class);

    private final EmployeeMapper employeeMapper;
    private final AuthenticationManager authenticationManager;
    private final PasswordEncoder passwordEncoder;

    public EmployeeServiceImpl(EmployeeMapper employeeMapper,
                               AuthenticationManager authenticationManager,
                               PasswordEncoder passwordEncoder) {
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
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(loginDTO.getUsername(), loginDTO.getPassword())
            );
            EmployeeUserDetails principal = (EmployeeUserDetails) authentication.getPrincipal();
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
    public void save(EmployeeSaveDTO dto) {
        Long count = employeeMapper.selectCount(
                new LambdaQueryWrapper<Employee>().eq(Employee::getUsername, dto.getUsername())
        );
        if (count != null && count > 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "账号已存在");
        }

        Employee employee = new Employee();
        employee.setUsername(dto.getUsername());
        employee.setName(dto.getName());
        employee.setPhone(dto.getPhone());
        employee.setSex(dto.getSex());
        employee.setIdNumber(normalizeIdNumber(dto.getIdNumber()));
        employee.setPassword(passwordEncoder.encode(PasswordConstant.DEFAULT_PASSWORD));
        employee.setStatus(1);

        employeeMapper.insert(employee);
        log.info("新增员工成功, username={}", employee.getUsername());
    }

    @Override
    public void update(EmployeeUpdateDTO dto) {
        Employee employee = getById(dto.getId());
        employee.setName(dto.getName());
        employee.setPhone(dto.getPhone());
        employee.setSex(dto.getSex());
        employee.setIdNumber(normalizeIdNumber(dto.getIdNumber()));
        // 不修改 username / password / status
        employeeMapper.updateById(employee);
        log.info("编辑员工成功, id={}", employee.getId());
    }

    private static String normalizeIdNumber(String idNumber) {
        return StringUtils.hasText(idNumber) ? idNumber.trim() : null;
    }
}
```

说明：`spring-security-core` 已含 `PasswordEncoder` 接口；Bean 由 framework `SecurityConfig` 提供。`StringUtils` 使用 `org.springframework.util.StringUtils`（system 经 MyBatis-Plus starter 已有 spring-core）。

- [ ] **Step 5: 跑测试确认通过**

Run:

```bash
mvn -pl take-out-system -am test -Dtest=EmployeeServiceImplTest -q
```

Expected: BUILD SUCCESS，测试通过

- [ ] **Step 6: Commit（默认跳过）**

```bash
git add take-out-system/src/main/java/com/sky/takeout/system/service/EmployeeService.java \
  take-out-system/src/main/java/com/sky/takeout/system/service/impl/EmployeeServiceImpl.java \
  take-out-system/src/test/java/com/sky/takeout/system/service/impl/EmployeeServiceImplTest.java
git commit -m "feat: 实现员工新增与编辑业务逻辑"
```

---

### Task 6: Controller 接口与 MockMvc 测试

**Files:**
- Modify: `take-out-admin/src/main/java/com/sky/takeout/admin/controller/EmployeeController.java`
- Modify: `take-out-admin/src/test/java/com/sky/takeout/admin/controller/EmployeeControllerTest.java`

**Interfaces:**
- Consumes: `EmployeeService.save` / `update`；`EmployeeSaveDTO` / `EmployeeUpdateDTO`；`@Valid`
- Produces: `POST /admin/employee`、`PUT /admin/employee` → `Result.success()`

- [ ] **Step 1: 先补测试（预期失败）**

在 `EmployeeControllerTest` 中追加（保留现有 getById 测试）：

```java
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sky.takeout.pojo.dto.EmployeeSaveDTO;
import com.sky.takeout.pojo.dto.EmployeeUpdateDTO;
import org.springframework.http.MediaType;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

// 类内增加：
@Autowired
private ObjectMapper objectMapper;

@Test
void save_success() throws Exception {
    doNothing().when(employeeService).save(any(EmployeeSaveDTO.class));

    EmployeeSaveDTO dto = new EmployeeSaveDTO();
    dto.setUsername("zhangsan");
    dto.setName("张三");
    dto.setPhone("13812345678");
    dto.setSex("1");

    mockMvc.perform(post("/admin/employee")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(dto)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(ErrorCode.SUCCESS));

    verify(employeeService).save(any(EmployeeSaveDTO.class));
}

@Test
void save_whenInvalidPhone_returnsBadRequest() throws Exception {
    EmployeeSaveDTO dto = new EmployeeSaveDTO();
    dto.setUsername("zhangsan");
    dto.setName("张三");
    dto.setPhone("123");
    dto.setSex("1");

    mockMvc.perform(post("/admin/employee")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(dto)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(ErrorCode.BAD_REQUEST))
            .andExpect(jsonPath("$.msg").value("手机号须为 11 位合法号码"));
}

@Test
void save_whenUsernameConflict_returnsConflict() throws Exception {
    doThrow(new BusinessException(ErrorCode.CONFLICT, "账号已存在"))
            .when(employeeService).save(any(EmployeeSaveDTO.class));

    EmployeeSaveDTO dto = new EmployeeSaveDTO();
    dto.setUsername("admin");
    dto.setName("张三");
    dto.setPhone("13812345678");
    dto.setSex("1");

    mockMvc.perform(post("/admin/employee")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(dto)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(ErrorCode.CONFLICT))
            .andExpect(jsonPath("$.msg").value("账号已存在"));
}

@Test
void update_success() throws Exception {
    doNothing().when(employeeService).update(any(EmployeeUpdateDTO.class));

    EmployeeUpdateDTO dto = new EmployeeUpdateDTO();
    dto.setId(2L);
    dto.setName("李四");
    dto.setPhone("13912345678");
    dto.setSex("0");

    mockMvc.perform(put("/admin/employee")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(dto)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(ErrorCode.SUCCESS));
}

@Test
void update_whenMissing_returnsError() throws Exception {
    doThrow(new BusinessException(ErrorCode.ERROR, "员工不存在"))
            .when(employeeService).update(any(EmployeeUpdateDTO.class));

    EmployeeUpdateDTO dto = new EmployeeUpdateDTO();
    dto.setId(99999L);
    dto.setName("李四");
    dto.setPhone("13912345678");
    dto.setSex("0");

    mockMvc.perform(put("/admin/employee")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(dto)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(ErrorCode.ERROR))
            .andExpect(jsonPath("$.msg").value("员工不存在"));
}
```

- [ ] **Step 2: 跑测试确认失败**

Run:

```bash
mvn -pl take-out-admin -am test -Dtest=EmployeeControllerTest -q
```

Expected: FAIL（缺少 POST/PUT 映射或编译失败）

- [ ] **Step 3: 实现 Controller 方法**

在 `EmployeeController` 中增加（保留 login / getById / toVO）：

```java
import com.sky.takeout.pojo.dto.EmployeeSaveDTO;
import com.sky.takeout.pojo.dto.EmployeeUpdateDTO;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PutMapping;

@Operation(summary = "新增员工")
@PostMapping
public Result<Void> save(@Valid @RequestBody EmployeeSaveDTO dto) {
    employeeService.save(dto);
    return Result.success();
}

@Operation(summary = "编辑员工")
@PutMapping
public Result<Void> update(@Valid @RequestBody EmployeeUpdateDTO dto) {
    employeeService.update(dto);
    return Result.success();
}
```

注意：`@PostMapping` 无 path，映射为 `/admin/employee`；不要写成与 `/login` 冲突的路径。`/login` 更具体，仍优先匹配登录。

- [ ] **Step 4: 跑 Controller 测试**

Run:

```bash
mvn -pl take-out-admin -am test -Dtest=EmployeeControllerTest -q
```

Expected: 全部通过

若 `save_whenInvalidPhone` 未进入 `GlobalExceptionHandler`（例如返回 400 默认 body），检查：

1. framework 是否引入 `spring-boot-starter-validation`
2. `@Import(GlobalExceptionHandler.class)` 是否仍在
3. 方法参数是否有 `@Valid`

- [ ] **Step 5: 回归相关测试**

Run:

```bash
mvn -pl take-out-admin,take-out-system -am test -Dtest=EmployeeControllerTest,EmployeeServiceImplTest,EmployeeUserDetailsServiceTest -q
```

Expected: BUILD SUCCESS

- [ ] **Step 6: Commit（默认跳过）**

```bash
git add take-out-admin/src/main/java/com/sky/takeout/admin/controller/EmployeeController.java \
  take-out-admin/src/test/java/com/sky/takeout/admin/controller/EmployeeControllerTest.java
git commit -m "feat: 暴露员工新增与编辑接口"
```

---

### Task 7: 手工联调清单（可选但推荐）

**Files:** 无代码变更

- [ ] **Step 1: 启动应用**（MySQL 已 up，且已执行 Task 4 迁移）

```bash
mvn -pl take-out-admin -am spring-boot:run
```

- [ ] **Step 2: 登录取 token**

```bash
curl -s -X POST http://localhost:8080/admin/employee/login \
  -H "Content-Type: application/json" \
  -d "{\"username\":\"admin\",\"password\":\"123456\"}"
```

Expected: `code=1`，`data.token` 非空

- [ ] **Step 3: 新增员工**

```bash
curl -s -X POST http://localhost:8080/admin/employee \
  -H "Content-Type: application/json" \
  -H "Authorization: <token>" \
  -d "{\"username\":\"zhangsan\",\"name\":\"张三\",\"phone\":\"13812345678\",\"sex\":\"1\"}"
```

Expected: `code=1`

- [ ] **Step 4: 重复账号**

同上再发一次。Expected: `code=409`，`msg=账号已存在`

- [ ] **Step 5: 回显并编辑**

用 `GET /admin/employee/{id}` 拿到新员工 id，再：

```bash
curl -s -X PUT http://localhost:8080/admin/employee \
  -H "Content-Type: application/json" \
  -H "Authorization: <token>" \
  -d "{\"id\":<id>,\"name\":\"张三丰\",\"phone\":\"13812345678\",\"sex\":\"1\",\"idNumber\":\"\"}"
```

Expected: `code=1`；再次 GET 时 `name` 已变，`username` 不变，`idNumber` 为 null

- [ ] **Step 6: 校验失败**

手机号传 `"123"`。Expected: `code=400`，消息含手机号校验文案

---

## Spec Coverage Checklist（自检）

| Spec 要求 | Task |
|-----------|------|
| POST/PUT `/admin/employee` | Task 6 |
| Save/Update DTO 拆分 | Task 2 |
| Bean Validation + 全局 400 | Task 1、6 |
| username 编辑不可改 | Task 5 |
| 默认密码 BCrypt(123456) | Task 2、5 |
| status 默认 1 | Task 5 |
| MetaObjectHandler 审计 | Task 3 |
| id_number 可空 + blank→null | Task 4、5 |
| update 清空身份证（ALWAYS） | Task 2、5 |
| 账号冲突 409 | Task 5、6 |
| Controller/Service 测试 | Task 5、6 |
| 非目标（分页/启停等）未纳入 | — |

## Type Consistency Notes

- Service 方法签名统一为 `void save(EmployeeSaveDTO)` / `void update(EmployeeUpdateDTO)`
- 错误码：校验 `400`，冲突 `409`，不存在 `500`（`ErrorCode.ERROR`，与现有 getById 一致）
- 性别字段类型全程 `String`（`"0"`/`"1"`）
