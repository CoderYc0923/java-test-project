# MyBatis-Plus 持久化底座 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在现有多模块骨架上接入 MyBatis-Plus，打通 `Employee` 按 id 只读查询（`GET /api/employees/{id}` → 无密码 VO）。

**Architecture:** 父 POM 锁定 MyBatis-Plus 版本；`system` 引入 starter + MySQL 驱动并实现 Mapper/Service；`pojo` 提供实体与 VO（Lombok）；`framework` 负责 `@MapperScan`；`admin` 配置数据源并暴露只读接口。依赖方向保持 `admin → framework → system → pojo → common`。

**Tech Stack:** Java 17、Spring Boot 4.1.0、MyBatis-Plus `mybatis-plus-spring-boot4-starter` 3.5.17、MySQL Connector/J、Lombok、JUnit 5、MockMvc

## Global Constraints

- Spring Boot parent：`4.1.0`；Java：`17`
- MyBatis-Plus：`com.baomidou:mybatis-plus-spring-boot4-starter:3.5.17`
- 依赖方向：`admin → framework → system → pojo → common`，禁止反向依赖
- 数据源仅写在 `take-out-admin` 的 `application.yml`（`127.0.0.1:3307` / `takeout_rw` / `TakeoutRw@123` / 库 `take_out`）
- 包名：`com.sky.takeout.*`
- 本次不做：分页插件、CRUD、登录鉴权、XML Mapper、H2/Testcontainers、多 profile
- Commit：仅在用户明确要求时执行；计划中的 commit 步骤默认跳过，除非用户授权
- 跑全量 `@SpringBootTest` 前须已 `docker compose up -d` 且 MySQL healthy

---

## File Structure

| 路径 | 职责 |
|------|------|
| `pom.xml` | 父 POM：`mybatis-plus` BOM/版本 + Lombok（如需）dependencyManagement |
| `take-out-pojo/pom.xml` | 增加 Lombok、`mybatis-plus-annotation` |
| `take-out-pojo/.../entity/Employee.java` | 员工实体，`@TableName("employee")` |
| `take-out-pojo/.../vo/EmployeeVO.java` | 对外 VO，无 password |
| `take-out-system/pom.xml` | MyBatis-Plus Boot4 starter + `mysql-connector-j` |
| `take-out-system/.../mapper/EmployeeMapper.java` | `BaseMapper<Employee>` |
| `take-out-system/.../service/EmployeeService.java` | 接口：`Employee getById(Long id)` |
| `take-out-system/.../service/impl/EmployeeServiceImpl.java` | `selectById`；空则抛 `BusinessException` |
| `take-out-framework/.../config/MybatisPlusConfig.java` | `@MapperScan("com.sky.takeout.system.mapper")` |
| `take-out-admin/.../application.yml` | datasource + mybatis-plus 配置 |
| `take-out-admin/.../controller/EmployeeController.java` | `GET /api/employees/{id}` |
| `take-out-admin/.../controller/EmployeeControllerTest.java` | WebMvcTest + mock Service，不连库 |
| 删除（可选）：空 `package-info.java` 若与新类冲突则保留无妨 | — |

---

### Task 1: 父 POM 与子模块依赖

**Files:**
- Modify: `pom.xml`
- Modify: `take-out-pojo/pom.xml`
- Modify: `take-out-system/pom.xml`

**Interfaces:**
- Consumes: 无
- Produces: 反应堆可解析 `mybatis-plus-spring-boot4-starter:3.5.17`、`mybatis-plus-annotation`、Lombok、`mysql-connector-j`

- [ ] **Step 1: 在父 `pom.xml` 的 `dependencyManagement` 增加 MyBatis-Plus**

在现有 `<dependencyManagement><dependencies>` 内追加：

```xml
<dependency>
    <groupId>com.baomidou</groupId>
    <artifactId>mybatis-plus-bom</artifactId>
    <version>3.5.17</version>
    <type>pom</type>
    <scope>import</scope>
</dependency>
```

说明：用 BOM 统一 Plus 相关构件版本；子模块引入 starter / annotation 时可不写 version。

- [ ] **Step 2: 更新 `take-out-pojo/pom.xml` 依赖**

在现有 `take-out-common` 依赖旁追加：

```xml
<dependency>
    <groupId>org.projectlombok</groupId>
    <artifactId>lombok</artifactId>
    <optional>true</optional>
</dependency>
<dependency>
    <groupId>com.baomidou</groupId>
    <artifactId>mybatis-plus-annotation</artifactId>
</dependency>
```

- [ ] **Step 3: 更新 `take-out-system/pom.xml` 依赖**

在现有 `take-out-pojo` 依赖旁追加：

```xml
<dependency>
    <groupId>com.baomidou</groupId>
    <artifactId>mybatis-plus-spring-boot4-starter</artifactId>
</dependency>
<dependency>
    <groupId>com.mysql</groupId>
    <artifactId>mysql-connector-j</artifactId>
    <scope>runtime</scope>
</dependency>
```

- [ ] **Step 4: 验证依赖可解析**

Run:

```powershell
.\mvnw.cmd -q dependency:resolve -pl take-out-system -am
```

Expected: exit code `0`，无 unresolved dependency 错误。

- [ ] **Step 5: Commit（仅当用户要求）**

```bash
git add pom.xml take-out-pojo/pom.xml take-out-system/pom.xml
git commit -m "build: add MyBatis-Plus and Lombok dependencies"
```

---

### Task 2: Employee 实体与 VO

**Files:**
- Create: `take-out-pojo/src/main/java/com/sky/takeout/pojo/entity/Employee.java`
- Create: `take-out-pojo/src/main/java/com/sky/takeout/pojo/vo/EmployeeVO.java`

**Interfaces:**
- Consumes: Lombok、`@TableName`（mybatis-plus-annotation）
- Produces: `Employee`（含 password）、`EmployeeVO`（无 password）；字段名与下表一致

- [ ] **Step 1: 创建 `Employee.java`**

```java
package com.sky.takeout.pojo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("employee")
public class Employee {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String username;
    private String password;
    private String phone;
    private String sex;
    private String idNumber;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private Long createUser;
    private Long updateUser;
}
```

- [ ] **Step 2: 创建 `EmployeeVO.java`**

```java
package com.sky.takeout.pojo.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class EmployeeVO {
    private Long id;
    private String name;
    private String username;
    private String phone;
    private String sex;
    private String idNumber;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private Long createUser;
    private Long updateUser;
}
```

- [ ] **Step 3: 编译 pojo 模块**

Run:

```powershell
.\mvnw.cmd -q -pl take-out-pojo compile
```

Expected: BUILD SUCCESS。

- [ ] **Step 4: Commit（仅当用户要求）**

```bash
git add take-out-pojo/src/main/java/com/sky/takeout/pojo/entity/Employee.java take-out-pojo/src/main/java/com/sky/takeout/pojo/vo/EmployeeVO.java
git commit -m "feat: add Employee entity and EmployeeVO"
```

---

### Task 3: Mapper、Service 与 MapperScan

**Files:**
- Create: `take-out-system/src/main/java/com/sky/takeout/system/mapper/EmployeeMapper.java`
- Create: `take-out-system/src/main/java/com/sky/takeout/system/service/EmployeeService.java`
- Create: `take-out-system/src/main/java/com/sky/takeout/system/service/impl/EmployeeServiceImpl.java`
- Create: `take-out-framework/src/main/java/com/sky/takeout/framework/config/MybatisPlusConfig.java`

**Interfaces:**
- Consumes: `Employee`；`BaseMapper`；`BusinessException` / `ErrorCode`
- Produces:
  - `EmployeeMapper extends BaseMapper<Employee>`
  - `EmployeeService#Employee getById(Long id)` — 不存在时抛 `BusinessException(ErrorCode.ERROR, "员工不存在")`
  - `MybatisPlusConfig` 带 `@MapperScan("com.sky.takeout.system.mapper")`

- [ ] **Step 1: 创建 `EmployeeMapper.java`**

```java
package com.sky.takeout.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sky.takeout.pojo.entity.Employee;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface EmployeeMapper extends BaseMapper<Employee> {
}
```

- [ ] **Step 2: 创建 `EmployeeService.java`**

```java
package com.sky.takeout.system.service;

import com.sky.takeout.pojo.entity.Employee;

public interface EmployeeService {
    Employee getById(Long id);
}
```

- [ ] **Step 3: 创建 `EmployeeServiceImpl.java`**

```java
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
            throw new BusinessException(ErrorCode.ERROR, "员工不存在");
        }
        return employee;
    }
}
```

说明：`system` 模块需能编译到 Spring 的 `@Service`。若当前 `take-out-system/pom.xml` 尚无 spring-context，通过 `mybatis-plus-spring-boot4-starter` 传递依赖通常已足够；若编译报找不到 `@Service`，在 system 的 pom 增加：

```xml
<dependency>
    <groupId>org.springframework</groupId>
    <artifactId>spring-context</artifactId>
</dependency>
```

- [ ] **Step 4: 创建 `MybatisPlusConfig.java`**

```java
package com.sky.takeout.framework.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@MapperScan("com.sky.takeout.system.mapper")
public class MybatisPlusConfig {
}
```

说明：包路径用明确包名（不用 `**`），后续业务模块再往 `@MapperScan` 的 `value` 数组追加。

- [ ] **Step 5: 编译 system + framework**

Run:

```powershell
.\mvnw.cmd -q -pl take-out-framework -am compile
```

Expected: BUILD SUCCESS。

- [ ] **Step 6: Commit（仅当用户要求）**

```bash
git add take-out-system/src/main/java/com/sky/takeout/system/mapper/EmployeeMapper.java take-out-system/src/main/java/com/sky/takeout/system/service/EmployeeService.java take-out-system/src/main/java/com/sky/takeout/system/service/impl/EmployeeServiceImpl.java take-out-framework/src/main/java/com/sky/takeout/framework/config/MybatisPlusConfig.java take-out-system/pom.xml
git commit -m "feat: add EmployeeMapper, EmployeeService, and MapperScan"
```

---

### Task 4: 数据源配置与 EmployeeController（含 WebMvc 测试）

**Files:**
- Modify: `take-out-admin/src/main/resources/application.yml`
- Create: `take-out-admin/src/main/java/com/sky/takeout/admin/controller/EmployeeController.java`
- Test: `take-out-admin/src/test/java/com/sky/takeout/admin/controller/EmployeeControllerTest.java`

**Interfaces:**
- Consumes: `EmployeeService#getById(Long)`；`Employee`；`EmployeeVO`；`Result`
- Produces: `GET /api/employees/{id}` → `Result<EmployeeVO>`（无 password）

- [ ] **Step 1: 先写失败的 `EmployeeControllerTest`（不连库）**

```java
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
                .thenThrow(new BusinessException(ErrorCode.ERROR, "员工不存在"));

        mockMvc.perform(get("/api/employees/99999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCode.ERROR))
                .andExpect(jsonPath("$.msg").value("员工不存在"));
    }
}
```

- [ ] **Step 2: 运行测试，确认因缺少 Controller 而失败**

Run:

```powershell
.\mvnw.cmd -pl take-out-admin -am test -Dtest=EmployeeControllerTest
```

Expected: FAIL（找不到 `EmployeeController` 或上下文无法加载该类）。

- [ ] **Step 3: 实现 `EmployeeController.java`**

```java
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
```

- [ ] **Step 4: 更新 `application.yml`**

完整文件内容：

```yaml
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
```

- [ ] **Step 5: 再跑 `EmployeeControllerTest`，期望通过**

Run:

```powershell
.\mvnw.cmd -pl take-out-admin -am test -Dtest=EmployeeControllerTest
```

Expected: Tests run: 2, Failures: 0, Errors: 0。

- [ ] **Step 6: 确保 Docker MySQL 已启动后跑 admin 全量测试**

Run:

```powershell
docker compose ps
.\mvnw.cmd clean test -pl take-out-admin -am
```

Expected: `take-out-mysql` 为 healthy；全部测试 PASS（含既有 `DemoControllerTest` / `contextLoads`）。

- [ ] **Step 7: 手工验证（可选但推荐）**

```powershell
.\mvnw.cmd spring-boot:run -pl take-out-admin
```

另开终端：

```powershell
curl http://localhost:8080/api/employees/1
curl http://localhost:8080/api/employees/99999
```

Expected: 前者 JSON 含 `"username":"admin"` 且无 `password`；后者 `code` 为错误码、`msg` 为「员工不存在」。

- [ ] **Step 8: Commit（仅当用户要求）**

```bash
git add take-out-admin/src/main/resources/application.yml take-out-admin/src/main/java/com/sky/takeout/admin/controller/EmployeeController.java take-out-admin/src/test/java/com/sky/takeout/admin/controller/EmployeeControllerTest.java
git commit -m "feat: add employee read API with MyBatis-Plus datasource"
```

---

## Spec Coverage Checklist

| Spec 要求 | 对应 Task |
|-----------|-----------|
| MyBatis-Plus Boot4 starter 3.5.17 | Task 1 |
| Lombok | Task 1–2 |
| Employee + EmployeeVO（无 password） | Task 2 |
| Mapper / Service / 查无抛 BusinessException | Task 3 |
| MapperScan 在 framework | Task 3 |
| admin yml 数据源 | Task 4 |
| GET /api/employees/{id} | Task 4 |
| EmployeeControllerTest mock、不连库 | Task 4 |
| 全量测试约定需 Docker | Task 4 Step 6 |
| 非目标（分页/CRUD/鉴权等） | 未列入任何 Task |

---

## Self-Review Notes

- 无 TBD/占位步骤；签名统一为 `Employee getById(Long id)`，Controller 内手工映射 VO。
- `@MapperScan` 使用 `com.sky.takeout.system.mapper`（比 spec 中的 `**` 更稳妥，语义等价于当前范围）。
- `pojo` 仅依赖 `mybatis-plus-annotation`，避免把 starter 拉进模型层。
