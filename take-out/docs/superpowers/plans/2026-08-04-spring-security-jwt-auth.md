# Spring Security + JWT 认证 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.
>
> **For hand-coding:** 按 Task 顺序自己敲；每完成一个 Task 跑该 Task 的验证命令。Commit 步骤默认跳过，除非你明确要求提交。

**Goal:** 用 Spring Security Filter 链完成管理端 JWT 认证：登录走 `AuthenticationManager`，业务请求验 JWT，未登录返回统一 `Result` 401。

**Architecture:** `framework` 放 Security 配置与 JWT Filter / EntryPoint；`system` 提供 `UserDetails` 与登录认证；`admin` Controller 仍负责签发 JWT。无 Session；密码 BCrypt；不做 RBAC。

**Tech Stack:** Java 17、Spring Boot 4.1.0、`spring-boot-starter-security`、现有 `JwtUtil`（jjwt）、MyBatis-Plus、JUnit 5、MockMvc

**Spec:** `docs/superpowers/specs/2026-08-04-spring-security-jwt-auth-design.md`

## Global Constraints

- Spring Boot parent：`4.1.0`；Java：`17`
- 依赖方向：`admin → framework → system → pojo → common`，禁止反向依赖
- 包名：`com.sky.takeout.*`
- JWT 配置沿用 `application.yml`：`jwt.admin-secret-key` / `jwt.admin-ttl` / `jwt.admin-token-name`
- Claim key：`JwtClaimsConstant.EMP_ID`（`"empId"`）
- 本期不做：RBAC、`@PreAuthorize`、刷新 token、OAuth2、HandlerInterceptor 鉴权
- Commit：仅在用户明确要求时执行；计划中的 commit 步骤默认跳过
- 工作目录：所有 Maven 命令在 `take-out/` 下执行

---

## File Structure

| 路径 | 职责 |
|------|------|
| `take-out-framework/pom.xml` | 增加 `spring-boot-starter-security` |
| `take-out-system/pom.xml` | 增加 `spring-security-core` |
| `take-out-system/.../security/EmployeeUserDetails.java` | `UserDetails` 实现，携带 `id` |
| `take-out-system/.../security/EmployeeUserDetailsService.java` | 按用户名加载员工 |
| `take-out-system/.../service/impl/EmployeeServiceImpl.java` | `login` 改为 `AuthenticationManager` |
| `take-out-framework/.../security/JwtAuthenticationEntryPoint.java` | 401 + `Result` |
| `take-out-framework/.../security/JwtAccessDeniedHandler.java` | 403 + `Result` |
| `take-out-framework/.../security/JwtAuthenticationFilter.java` | 验 JWT，写 `SecurityContext` / `BaseContext` |
| `take-out-framework/.../config/SecurityConfig.java` | `SecurityFilterChain`、`PasswordEncoder`、`AuthenticationManager` |
| `take-out-admin/.../controller/EmployeeController.java` | 登录发 JWT 逻辑保持；路径已是 `/admin/employee` |
| `sky.sql` + 运行库 `employee.password` | 明文改为 BCrypt |
| `take-out-admin/.../controller/EmployeeControllerTest.java` | 关闭 Security Filter，避免纯 Controller 单测被拦 |
| `take-out-admin/.../security/AdminSecurityIT.java` | 集成验收：无 token→401；登录→带 token 访问 |

---

### Task 1: 引入 Security 依赖

**Files:**
- Modify: `take-out-framework/pom.xml`
- Modify: `take-out-system/pom.xml`

**Interfaces:**
- Consumes: 无
- Produces: 编译期可用 Spring Security API；运行期 admin 引入 framework 后自动装配 Security

**为什么只改这两个 pom（不改 admin / common / pojo）**

原则：**谁用到 Security 的 API / 配置，谁才声明依赖**；并遵守依赖方向 `admin → framework → system → pojo → common`。

| 模块 | 加什么 | 原因 |
|------|--------|------|
| `take-out-framework` | `spring-boot-starter-security`（完整） | 这里放 `SecurityConfig`、JWT Filter、EntryPoint，直接使用 `HttpSecurity`、`SecurityFilterChain` 等 Web Security API |
| `take-out-system` | `spring-security-core`（轻量） | 这里放 `EmployeeUserDetails`、`UserDetailsService`，以及 `login` 用的 `AuthenticationManager`；只需核心类型，不必再拉一整套 Web Security |
| `take-out-admin` | **不声明** | 已依赖 `framework`，Security 会**传递依赖**进来；启动模块重复声明没有必要 |
| `take-out-common` / `take-out-pojo` | **不声明** | 只有 JWT 工具、实体、常量，不碰 Security API；加上会污染底座、破坏分层 |

一句话：`framework` 管「安全怎么拦请求」，`system` 管「用户怎么加载/登录校验」；`admin` 坐享其成，底层模块保持干净。

- [x] **Step 1: 修改 `take-out-framework/pom.xml`**

在现有 `spring-boot-starter-webmvc` 依赖旁追加：

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-security</artifactId>
</dependency>
```

- [x] **Step 2: 修改 `take-out-system/pom.xml`**

在现有依赖旁追加（只需 core，避免 system 再拉一整套 Web Security）：

```xml
<dependency>
    <groupId>org.springframework.security</groupId>
    <artifactId>spring-security-core</artifactId>
</dependency>
```

版本由 Spring Boot 父 POM 管理，不要写 `<version>`。

- [x] **Step 3: 验证依赖可解析**

Run:

```powershell
.\mvnw.cmd -q dependency:resolve -pl take-out-framework,take-out-system -am
```

Expected: exit code `0`。

- [x] **Step 4: Commit（仅当用户要求）**

```bash
git add take-out-framework/pom.xml take-out-system/pom.xml
git commit -m "build: add Spring Security dependencies"
```

---

### Task 2: EmployeeUserDetails + UserDetailsService

**Files:**
- Create: `take-out-system/src/main/java/com/sky/takeout/system/security/EmployeeUserDetails.java`
- Create: `take-out-system/src/main/java/com/sky/takeout/system/security/EmployeeUserDetailsService.java`
- Create: `take-out-system/src/test/java/com/sky/takeout/system/security/EmployeeUserDetailsServiceTest.java`

**Interfaces:**
- Consumes: `EmployeeMapper`、`Employee` 实体（`status`：1 启用 / 0 禁用）
- Produces:
  - `EmployeeUserDetails`：`getId(): Long`、`getUsername()`、`getPassword()`、`isEnabled()`（`status != 0`）、`getAuthorities()` 返回空列表
  - `EmployeeUserDetailsService.loadUserByUsername(String): UserDetails`
  - 静态工厂：`EmployeeUserDetails.fromEmployee(Employee)`、`EmployeeUserDetails.forId(Long id)`（JWT Filter 用，密码可空字符串，enabled=true）

- [x] **Step 1: 写失败测试 `EmployeeUserDetailsServiceTest`**

```java
package com.sky.takeout.system.security;

import com.sky.takeout.pojo.entity.Employee;
import com.sky.takeout.system.mapper.EmployeeMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmployeeUserDetailsServiceTest {

    @Mock
    private EmployeeMapper employeeMapper;

    @InjectMocks
    private EmployeeUserDetailsService service;

    @Test
    void loadUserByUsername_returnsDetails() {
        Employee employee = new Employee();
        employee.setId(1L);
        employee.setUsername("admin");
        employee.setPassword("hash");
        employee.setStatus(1);
        when(employeeMapper.selectOne(ArgumentMatchers.<LambdaQueryWrapper<Employee>>any()))
                .thenReturn(employee);

        var details = (EmployeeUserDetails) service.loadUserByUsername("admin");
        assertEquals(1L, details.getId());
        assertEquals("admin", details.getUsername());
        assertEquals("hash", details.getPassword());
        assertTrue(details.isEnabled());
    }

    @Test
    void loadUserByUsername_whenMissing_throws() {
        when(employeeMapper.selectOne(ArgumentMatchers.<LambdaQueryWrapper<Employee>>any()))
                .thenReturn(null);
        assertThrows(UsernameNotFoundException.class,
                () -> service.loadUserByUsername("nope"));
    }

    @Test
    void loadUserByUsername_whenDisabled_isNotEnabled() {
        Employee employee = new Employee();
        employee.setId(2L);
        employee.setUsername("bad");
        employee.setPassword("hash");
        employee.setStatus(0);
        when(employeeMapper.selectOne(ArgumentMatchers.<LambdaQueryWrapper<Employee>>any()))
                .thenReturn(employee);

        var details = (EmployeeUserDetails) service.loadUserByUsername("bad");
        assertFalse(details.isEnabled());
    }
}
```

若 `take-out-system` 尚无测试依赖，在 `take-out-system/pom.xml` 增加：

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-test</artifactId>
    <scope>test</scope>
</dependency>
```

- [x] **Step 2: 跑测试，确认失败**

```powershell
.\mvnw.cmd -pl take-out-system -am test -Dtest=EmployeeUserDetailsServiceTest "-Dsurefire.failIfNoSpecifiedTests=false"
```

说明：加 `-am` 时会先进入依赖模块（如 `take-out-common`）；那些模块里没有该测试名，Surefire 3.x 默认会失败。加上 `failIfNoSpecifiedTests=false` 即可跳过。

Expected: 编译失败或测试失败（类不存在）。

- [x] **Step 3: 实现 `EmployeeUserDetails`**

```java
package com.sky.takeout.system.security;

import com.sky.takeout.pojo.entity.Employee;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.Collections;

public class EmployeeUserDetails implements UserDetails {

    private final Long id;
    private final String username;
    private final String password;
    private final boolean enabled;

    public EmployeeUserDetails(Long id, String username, String password, boolean enabled) {
        this.id = id;
        this.username = username;
        this.password = password;
        this.enabled = enabled;
    }

    public static EmployeeUserDetails fromEmployee(Employee employee) {
        boolean enabled = employee.getStatus() == null || employee.getStatus() != 0;
        return new EmployeeUserDetails(
                employee.getId(),
                employee.getUsername(),
                employee.getPassword(),
                enabled
        );
    }

    /** JWT 认证后重建 principal，不承载密码 */
    public static EmployeeUserDetails forId(Long id) {
        return new EmployeeUserDetails(id, String.valueOf(id), "", true);
    }

    public Long getId() {
        return id;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return Collections.emptyList();
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }
}
```

- [x] **Step 4: 实现 `EmployeeUserDetailsService`**

```java
package com.sky.takeout.system.security;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sky.takeout.pojo.entity.Employee;
import com.sky.takeout.system.mapper.EmployeeMapper;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class EmployeeUserDetailsService implements UserDetailsService {

    private final EmployeeMapper employeeMapper;

    public EmployeeUserDetailsService(EmployeeMapper employeeMapper) {
        this.employeeMapper = employeeMapper;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        Employee employee = employeeMapper.selectOne(
                new LambdaQueryWrapper<Employee>().eq(Employee::getUsername, username));
        if (employee == null) {
            throw new UsernameNotFoundException("用户不存在");
        }
        return EmployeeUserDetails.fromEmployee(employee);
    }
}
```

- [x] **Step 5: 再跑测试**

```powershell
.\mvnw.cmd -pl take-out-system -am test -Dtest=EmployeeUserDetailsServiceTest "-Dsurefire.failIfNoSpecifiedTests=false"
```

Expected: exit code `0`，测试通过。

- [x] **Step 6: Commit（仅当用户要求）**

```bash
git add take-out-system/pom.xml take-out-system/src/main/java/com/sky/takeout/system/security take-out-system/src/test/java/com/sky/takeout/system/security
git commit -m "feat: add EmployeeUserDetails and UserDetailsService"
```

---

### Task 3: PasswordEncoder + 种子密码改为 BCrypt

**Files:**
- Create: `take-out-framework/src/main/java/com/sky/takeout/framework/config/SecurityConfig.java`（本 Task 先只放 `PasswordEncoder` Bean；后续 Task 补全 FilterChain）
- Modify: `sky.sql`（INSERT 中的 password）
- 运行库：执行一次 `UPDATE`

**Interfaces:**

- Consumes: 无
- Produces: `@Bean PasswordEncoder` → `BCryptPasswordEncoder`

- [ ] **Step 1: 创建 `SecurityConfig`，先只提供编码器**

```java
package com.sky.takeout.framework.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
```

- [ ] **Step 2: 生成 BCrypt（一次性）**

在任意已能编译的模块写临时测试或 `main`（生成后可删）：

```java
@Test
void printBcrypt() {
    System.out.println(new BCryptPasswordEncoder().encode("123456"));
}
```

Run:

```powershell
.\mvnw.cmd -pl take-out-framework -am test -Dtest=你的临时类名#printBcrypt
```

复制控制台输出的哈希（约 60 字符，形如 `$2a$10$...`）。

> `employee.password` 列是 `varchar(64)`，BCrypt 60 字符放得下。

- [ ] **Step 3: 更新 `sky.sql` 中 admin 密码**

把：

```sql
INSERT INTO `employee` VALUES (1,'管理员','admin','123456',...
```

中的 `'123456'` 换成上一步生成的 BCrypt 字符串（保留单引号）。

- [ ] **Step 4: 更新正在跑的数据库**

```sql
UPDATE employee SET password = '<你的BCrypt哈希>' WHERE username = 'admin';
```

可用：

```powershell
docker compose exec -T mysql mysql -utakeout_rw -pTakeoutRw@123 take_out -e "UPDATE employee SET password='<哈希>' WHERE username='admin';"
```

（容器名/服务名以你的 `docker-compose.yml` 为准；若库是初始化脚本灌的，也可 `docker compose down -v` 后重建，但会清库。）

- [ ] **Step 5: Commit（仅当用户要求）**

```bash
git add take-out-framework/src/main/java/com/sky/takeout/framework/config/SecurityConfig.java sky.sql
git commit -m "feat: add BCrypt PasswordEncoder and hash seed password"
```

---

### Task 4: 登录改为 AuthenticationManager

**Files:**
- Modify: `take-out-framework/.../config/SecurityConfig.java`（暴露 `AuthenticationManager` Bean）
- Modify: `take-out-system/.../service/impl/EmployeeServiceImpl.java`
- Modify: `take-out-admin/.../controller/EmployeeControllerTest.java`（若有 login 测再改；当前可不动 Controller）

**Interfaces:**
- Consumes: `EmployeeUserDetailsService`、`PasswordEncoder`、`AuthenticationManager`
- Produces: `EmployeeService.login(EmployeeLoginDTO)` 成功返回带 id/username/name 的 `Employee`；失败抛 `BusinessException`

- [ ] **Step 1: 在 `SecurityConfig` 增加 `AuthenticationManager` Bean**

```java
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;

@Bean
public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
    return configuration.getAuthenticationManager();
}
```

（保留已有 `passwordEncoder()`。）

- [ ] **Step 2: 改写 `EmployeeServiceImpl.login`**

```java
package com.sky.takeout.system.service.impl;

import com.sky.takeout.common.exception.BusinessException;
import com.sky.takeout.common.result.ErrorCode;
import com.sky.takeout.pojo.dto.EmployeeLoginDTO;
import com.sky.takeout.pojo.entity.Employee;
import com.sky.takeout.system.mapper.EmployeeMapper;
import com.sky.takeout.system.security.EmployeeUserDetails;
import com.sky.takeout.system.service.EmployeeService;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

@Service
public class EmployeeServiceImpl implements EmployeeService {

    private final EmployeeMapper employeeMapper;
    private final AuthenticationManager authenticationManager;

    public EmployeeServiceImpl(EmployeeMapper employeeMapper,
                               AuthenticationManager authenticationManager) {
        this.employeeMapper = employeeMapper;
        this.authenticationManager = authenticationManager;
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
                    new UsernamePasswordAuthenticationToken(
                            loginDTO.getUsername(),
                            loginDTO.getPassword()
                    )
            );
            EmployeeUserDetails principal = (EmployeeUserDetails) authentication.getPrincipal();
            Employee employee = employeeMapper.selectById(principal.getId());
            if (employee == null) {
                throw new BusinessException(ErrorCode.ERROR, "用户名或密码错误");
            }
            return employee;
        } catch (BadCredentialsException ex) {
            throw new BusinessException(ErrorCode.ERROR, "用户名或密码错误");
        } catch (DisabledException ex) {
            throw new BusinessException(ErrorCode.ERROR, "账号已禁用");
        }
    }
}
```

说明：Spring Security 默认会把「用户不存在」也收成 `BadCredentialsException`，与「密码错误」同一文案，符合 spec。

- [ ] **Step 3: 编译 system + framework**

```powershell
.\mvnw.cmd -pl take-out-admin -am compile
```

Expected: exit code `0`。  
若报 `AuthenticationManager` 找不到 Bean：确认 `SecurityConfig` 已被 component-scan（`com.sky.takeout` 下）扫到。

- [ ] **Step 4: Commit（仅当用户要求）**

```bash
git add take-out-framework/src/main/java/com/sky/takeout/framework/config/SecurityConfig.java take-out-system/src/main/java/com/sky/takeout/system/service/impl/EmployeeServiceImpl.java
git commit -m "feat: authenticate employee login via AuthenticationManager"
```

---

### Task 5: EntryPoint 与 AccessDeniedHandler

**Files:**
- Create: `take-out-framework/src/main/java/com/sky/takeout/framework/security/JwtAuthenticationEntryPoint.java`
- Create: `take-out-framework/src/main/java/com/sky/takeout/framework/security/JwtAccessDeniedHandler.java`

**Interfaces:**
- Consumes: `Result`、`ErrorCode`、`ObjectMapper`
- Produces: 写入 HTTP 401/403 + JSON body `{"code":401|403,"msg":"...","data":null}`

- [ ] **Step 1: 实现 `JwtAuthenticationEntryPoint`**

> **Boot 4 / Jackson 3 注意：** 不要用 `com.fasterxml.jackson.databind.ObjectMapper`（已不在编译 classpath）。注入 Spring 自动配置的 `tools.jackson.databind.json.JsonMapper`。

```java
package com.sky.takeout.framework.security;

import com.sky.takeout.common.result.ErrorCode;
import com.sky.takeout.common.result.Result;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Component
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final JsonMapper jsonMapper;

    public JwtAuthenticationEntryPoint(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        jsonMapper.writeValue(response.getWriter(),
                Result.error(ErrorCode.UNAUTHORIZED, "未登录或登录已失效"));
    }
}
```

- [ ] **Step 2: 实现 `JwtAccessDeniedHandler`**

```java
package com.sky.takeout.framework.security;

import com.sky.takeout.common.result.ErrorCode;
import com.sky.takeout.common.result.Result;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Component
public class JwtAccessDeniedHandler implements AccessDeniedHandler {

    private final JsonMapper jsonMapper;

    public JwtAccessDeniedHandler(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    @Override
    public void handle(HttpServletRequest request,
                       HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        jsonMapper.writeValue(response.getWriter(),
                Result.error(ErrorCode.FORBIDDEN, "没有操作权限"));
    }
}
```

- [ ] **Step 3: 编译**

```powershell
.\mvnw.cmd -pl take-out-framework -am compile
```

Expected: exit code `0`。

- [ ] **Step 4: Commit（仅当用户要求）**

```bash
git add take-out-framework/src/main/java/com/sky/takeout/framework/security
git commit -m "feat: add JWT auth entry point and access denied handler"
```

---

### Task 6: JwtAuthenticationFilter

**Files:**

- Create: `take-out-framework/src/main/java/com/sky/takeout/framework/security/JwtAuthenticationFilter.java`

**Interfaces:**
- Consumes: `JwtUtil.parseToken`、`JwtClaimsConstant.EMP_ID`、`BaseContext`、`EmployeeUserDetails.forId`
- Produces: 合法 token → `SecurityContext` + `BaseContext`；非法 token → 不放行并写 401（调用 EntryPoint）；无 token → 放行给后续 Security 决定（受保护接口会 401）

- [ ] **Step 1: 实现 Filter（不要加 `@Component`，避免被注册两次；由 SecurityConfig 以 `@Bean` 创建）**

说明：`BaseContext.removeCurrentId()` 放在 `filterChain.doFilter(...)` **之后**的 `finally` 中——此时 Controller 已执行完，同一请求线程上清理是安全的。构造参数的 `@Value` 在 Task 7 的 `@Bean` 方法上注入，本类只收普通参数。

```java
package com.sky.takeout.framework.security;

import com.sky.takeout.common.constant.JwtClaimsConstant;
import com.sky.takeout.common.context.BaseContext;
import com.sky.takeout.common.jwt.JwtUtil;
import com.sky.takeout.system.security.EmployeeUserDetails;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final AuthenticationEntryPoint authenticationEntryPoint;
    private final String secretKey;
    private final String tokenHeaderName;

    public JwtAuthenticationFilter(AuthenticationEntryPoint authenticationEntryPoint,
                                   String secretKey,
                                   String tokenHeaderName) {
        this.authenticationEntryPoint = authenticationEntryPoint;
        this.secretKey = secretKey;
        this.tokenHeaderName = tokenHeaderName;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String headerName = (tokenHeaderName == null || tokenHeaderName.isBlank())
                ? HttpHeaders.AUTHORIZATION
                : tokenHeaderName;
        String header = request.getHeader(headerName);

        if (header == null || header.isBlank()) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = header.startsWith("Bearer ") ? header.substring(7).trim() : header.trim();
        try {
            var claims = JwtUtil.parseToken(secretKey, token);
            Long empId = ((Number) claims.get(JwtClaimsConstant.EMP_ID)).longValue();
            EmployeeUserDetails principal = EmployeeUserDetails.forId(empId);
            var authentication = new UsernamePasswordAuthenticationToken(
                    principal, null, principal.getAuthorities());
            SecurityContextHolder.getContext().setAuthentication(authentication);
            BaseContext.setCurrentId(empId);
            filterChain.doFilter(request, response);
        } catch (JwtException | IllegalArgumentException | ClassCastException | NullPointerException ex) {
            SecurityContextHolder.clearContext();
            authenticationEntryPoint.commence(request, response,
                    new BadCredentialsException("无效的token", ex));
        } finally {
            BaseContext.removeCurrentId();
            // SecurityContext 由 Spring Security 的策略在请求结束清理；此处清 BaseContext 即可。
            // 若 filterChain.doFilter 已在 try 中调用，finally 仍在同一请求线程、在 Controller 返回之后执行，安全。
        }
    }
}
```

- [ ] **Step 2: 编译**

```powershell
.\mvnw.cmd -pl take-out-framework -am compile
```

Expected: exit code `0`。

- [ ] **Step 3: Commit（仅当用户要求）**

```bash
git add take-out-framework/src/main/java/com/sky/takeout/framework/security/JwtAuthenticationFilter.java
git commit -m "feat: add JwtAuthenticationFilter"
```

---

### Task 7: 补全 SecurityFilterChain

**Files:**
- Modify: `take-out-framework/src/main/java/com/sky/takeout/framework/config/SecurityConfig.java`

**Interfaces:**
- Consumes: `JwtAuthenticationFilter`、`JwtAuthenticationEntryPoint`、`JwtAccessDeniedHandler`
- Produces: 完整无 Session 安全链；放行 `POST /admin/employee/login`

- [ ] **Step 1: 将 `SecurityConfig` 写成完整版**

```java
package com.sky.takeout.framework.config;

import com.sky.takeout.framework.security.JwtAccessDeniedHandler;
import com.sky.takeout.framework.security.JwtAuthenticationEntryPoint;
import com.sky.takeout.framework.security.JwtAuthenticationFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }

    @Bean
    public JwtAuthenticationFilter jwtAuthenticationFilter(
            JwtAuthenticationEntryPoint entryPoint,
            @Value("${jwt.admin-secret-key}") String secretKey,
            @Value("${jwt.admin-token-name}") String tokenHeaderName) {
        return new JwtAuthenticationFilter(entryPoint, secretKey, tokenHeaderName);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   JwtAuthenticationFilter jwtAuthenticationFilter,
                                                   JwtAuthenticationEntryPoint entryPoint,
                                                   JwtAccessDeniedHandler accessDeniedHandler) throws Exception {
        http.csrf(csrf -> csrf.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/admin/employee/login").permitAll()
                        .anyRequest().authenticated()
                )
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(accessDeniedHandler)
                )
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
```

- [ ] **Step 2: 修正现有 `EmployeeControllerTest`（Security 上 classpath 后默认会拦 MockMvc）**

在测试类上增加：

```java
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;

@WebMvcTest(controllers = EmployeeController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class EmployeeControllerTest {
    // 其余不变
}
```

若 Boot 4 的注解包名不同，以 IDE 自动导入为准；目的是 **单测不走 Filter**。

- [ ] **Step 3: 跑已有 Controller 单测**

```powershell
.\mvnw.cmd -pl take-out-admin -am test -Dtest=EmployeeControllerTest
```

Expected: 通过。

- [ ] **Step 4: Commit（仅当用户要求）**

```bash
git add take-out-framework/src/main/java/com/sky/takeout/framework/config/SecurityConfig.java take-out-admin/src/test/java/com/sky/takeout/admin/controller/EmployeeControllerTest.java
git commit -m "feat: configure SecurityFilterChain for JWT auth"
```

---

### Task 8: 安全集成验收测试

**Files:**
- Create: `take-out-admin/src/test/java/com/sky/takeout/admin/security/AdminSecurityIT.java`

**Interfaces:**
- Consumes: 完整 Spring Boot 上下文、真实 Security、真实 JWT 配置；Service 可用 `@MockitoBean` 降低对 DB 的依赖（登录路径仍要测 AuthenticationManager 时更适合连库）
- Produces: 覆盖 spec 验收 1/2/3/5（无 token、坏 token、登录+带 token、登录无需 token）

推荐两种测法二选一（手敲选 **A** 即可）：

**A. `@SpringBootTest` + MockMvc + 真实 DB（需 docker MySQL 已启动、密码已是 BCrypt）**

```java
package com.sky.takeout.admin.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sky.takeout.common.result.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AdminSecurityIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void protectedApi_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/admin/employee/1"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(ErrorCode.UNAUTHORIZED));
    }

    @Test
    void protectedApi_withBadToken_returns401() throws Exception {
        mockMvc.perform(get("/admin/employee/1").header("Authorization", "not-a-jwt"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(ErrorCode.UNAUTHORIZED));
    }

    @Test
    void login_thenAccessProtectedApi() throws Exception {
        MvcResult loginResult = mockMvc.perform(post("/admin/employee/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"admin","password":"123456"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCode.SUCCESS))
                .andExpect(jsonPath("$.data.token").isNotEmpty())
                .andReturn();

        JsonNode body = objectMapper.readTree(loginResult.getResponse().getContentAsString());
        String token = body.path("data").path("token").asText();

        mockMvc.perform(get("/admin/employee/1").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCode.SUCCESS))
                .andExpect(jsonPath("$.data.username").value("admin"));
    }
}
```

- [ ] **Step 1: 确保 MySQL 已启动且 admin 密码为 BCrypt**

```powershell
docker compose ps
```

- [ ] **Step 2: 添加并运行 IT**

```powershell
.\mvnw.cmd -pl take-out-admin -am test -Dtest=AdminSecurityIT
```

Expected: 3 个测试通过。若登录失败，优先检查 DB 中 password 是否已是 BCrypt、明文是否仍为 `123456`。

- [ ] **Step 3: 手动再验一遍（可选）**

```powershell
# 无 token → 401
curl -i http://localhost:8080/admin/employee/1

# 登录
curl -i -X POST http://localhost:8080/admin/employee/login -H "Content-Type: application/json" -d "{\"username\":\"admin\",\"password\":\"123456\"}"

# 带 token（把 TOKEN 换成返回值）
curl -i http://localhost:8080/admin/employee/1 -H "Authorization: TOKEN"
```

- [ ] **Step 4: Commit（仅当用户要求）**

```bash
git add take-out-admin/src/test/java/com/sky/takeout/admin/security/AdminSecurityIT.java
git commit -m "test: add admin JWT security integration tests"
```

---

## 验收对照（Spec）

| Spec 项 | Task |
|---------|------|
| Security 依赖落在 framework / system | Task 1 |
| UserDetails + UserDetailsService | Task 2 |
| BCrypt + 种子迁移 | Task 3 |
| AuthenticationManager 登录 | Task 4 |
| EntryPoint / AccessDeniedHandler | Task 5 |
| JWT Filter + BaseContext | Task 6 |
| SecurityFilterChain、放行 login | Task 7 |
| 401/登录/带 token 验收 | Task 8 |
| Controller 仍签发 JWT | 已有 `EmployeeController`，无需改签名（Task 4 后应自动可用） |
| 不做 RBAC | 全局约束 |

---

## 手敲时常见坑

1. **Filter 加了 `@Component` 又 `addFilterBefore`** → 同一 Filter 执行两次；只通过 `SecurityConfig` `@Bean` 创建。
2. **在 `finally` 里过早 `BaseContext.remove` 且写在 `doFilter` 之前** → 业务拿不到当前用户；按 Task 6 最终版，在 `doFilter` 返回后清理。
3. **库里仍是明文 `123456`** → `matches` 永远失败。
4. **`WebMvcTest` 未关 Filter** → 原 `EmployeeControllerTest` 集体 401。
5. **`PasswordEncoder` / `UserDetailsService` 缺 Bean** → 登录时 `AuthenticationManager` 无 DaoAuthenticationProvider。
