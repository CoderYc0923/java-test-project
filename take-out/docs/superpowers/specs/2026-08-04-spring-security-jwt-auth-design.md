# Spring Security + JWT 认证设计

日期：2026-08-04  
状态：已批准（待实现）

## 背景

管理端已具备员工登录并发放 JWT（`JwtUtil`、`EmployeeController.login`、`jwt.*` 配置、`BaseContext`），但尚未接入请求侧认证：`WebMvcConfig` 为空，受保护接口（如 `GET /admin/employee/{id}`）可被匿名调用。

目标是按正规 Spring Security 流程完成**认证（Authentication）**，为后续授权（RBAC）留扩展点。用户将自行手敲实现；本文档为设计规格。

## 目标

- 引入 Spring Security，采用无 Session（JWT）认证。
- 登录走 `AuthenticationManager` + `UserDetailsService` + `PasswordEncoder`（BCrypt）。
- 后续请求由 JWT Filter 校验，写入 `SecurityContext` 与 `BaseContext`。
- 未认证访问返回与现有 `Result` 一致的 401 响应。
- 保持模块依赖方向：`admin → framework → system → pojo → common`。

## 非目标（本期不做）

- RBAC、角色/菜单/权限表、`@PreAuthorize`。
- 刷新 token、登出黑名单、多端踢下线。
- Spring Security 默认表单登录页 / HTTP Basic 作为主登录方式。
- OAuth2 / Resource Server / Authorization Server。
- 改用 MVC `HandlerInterceptor` 做鉴权（本期用 Security Filter 链）。

## 方案选型

采用：**Security 管登录 + JWT Filter 管后续请求**（方案 1）。

- 保留现有 `JwtUtil`（jjwt）签发/解析。
- 不采用「仅加 Filter、登录不进 Security」的半吊子做法。
- 不引入完整 OAuth2 Resource Server（对本期管理端单体过重）。

## 架构与模块边界

| 模块 | 职责 |
|------|------|
| `take-out-framework` | `spring-boot-starter-security`；`SecurityConfig`、`JwtAuthenticationFilter`、`JwtAuthenticationEntryPoint`、`AccessDeniedHandler`、`PasswordEncoder` Bean；暴露 `AuthenticationManager` Bean |
| `take-out-system` | 增加 `spring-security-core`（或等价）编译依赖，以使用 `UserDetails` / `AuthenticationManager` API；实现 `EmployeeUserDetails`、`EmployeeUserDetailsService`；`EmployeeService.login` 改为走 `AuthenticationManager` |
| `take-out-common` | 继续使用 `JwtUtil`、`BaseContext`、`JwtClaimsConstant`、`ErrorCode.UNAUTHORIZED` / `FORBIDDEN` |
| `take-out-admin` | Controller 收参、调用服务、签发 JWT 并返回 `EmployeeLoginVO`；沿用 `application.yml` 中 `jwt.*` |

```text
登录：
Client → POST /admin/employee/login
      → AuthenticationManager.authenticate
      → UserDetailsService 加载员工 + BCrypt 校验
      → Controller 用 JwtUtil 发 token → EmployeeLoginVO

业务请求：
Client 带 Authorization
      → JwtAuthenticationFilter 验签
      → SecurityContext + BaseContext(empId)
      → Controller
      → 请求结束清理 BaseContext
```

会话策略：`SessionCreationPolicy.STATELESS`。

## 类职责

### framework

| 类 | 职责 |
|----|------|
| `SecurityConfig` | 配置 `SecurityFilterChain`：关闭 CSRF、无 Session；放行登录；其余需认证；注册 `PasswordEncoder`（BCrypt）；将 JWT Filter 加在 `UsernamePasswordAuthenticationFilter` 之前 |
| `JwtAuthenticationFilter` | 读取请求头 token；验签成功后设置 `SecurityContext` 与 `BaseContext`；失败交由 EntryPoint；请求结束清理 ThreadLocal |
| `JwtAuthenticationEntryPoint` | 未认证 → HTTP 401 + `Result`（`ErrorCode.UNAUTHORIZED`） |
| `AccessDeniedHandler`（简单实现） | 已认证无权限 → HTTP 403 + `Result`（`ErrorCode.FORBIDDEN`）；本期几乎不会触发，为完整性保留 |

### system

| 类 | 职责 |
|----|------|
| `EmployeeUserDetails` | 实现 `UserDetails`：员工 id、用户名、密码哈希、`status` → `isEnabled` |
| `EmployeeUserDetailsService` | 按用户名查员工；不存在抛 `UsernameNotFoundException` |
| `EmployeeService.login` | 构造 `UsernamePasswordAuthenticationToken`，调用 `AuthenticationManager.authenticate`；成功返回 `Employee`（或等价信息供 Controller 发 JWT） |

### admin

| 类 | 职责 |
|----|------|
| `EmployeeController.login` | 调用 `employeeService.login`；用 `JwtUtil.createToken` 写入 `empId` claim；返回 `EmployeeLoginVO`（JWT 签发留在 Controller，少改现有结构） |

## 路径与 Header

- 匿名放行：`POST /admin/employee/login`
- 需认证：其余请求（`anyRequest().authenticated()`），覆盖 `/admin/**`
- Token 请求头名：`jwt.admin-token-name`（当前为 `Authorization`）
- Token 格式：优先剥离 `Bearer ` 前缀；若无前缀则整段作为 JWT（兼容现有客户端习惯）

## 密码

- 统一使用 `BCryptPasswordEncoder`；登录使用 `matches(raw, encoded)`。
- 数据库 `employee.password` 存 BCrypt 哈希。
- 开发环境需将现有明文种子数据迁移为 BCrypt（实现计划中给出具体步骤）；本期不新增改密/注册接口。

## 异常与响应约定

| 场景 | 处理方 | 表现 |
|------|--------|------|
| 无/无效/过期 JWT 访问受保护资源 | `JwtAuthenticationEntryPoint` | HTTP 401 + `Result`（`UNAUTHORIZED`） |
| 已认证但拒绝访问 | `AccessDeniedHandler` | HTTP 403 + `Result`（`FORBIDDEN`） |
| 登录用户名/密码错误 | Service 将 `BadCredentialsException` / `UsernameNotFoundException` 转为 `BusinessException` | `GlobalExceptionHandler`；文案统一为「用户名或密码错误」 |
| 账号禁用 | `DisabledException` → `BusinessException` | 「账号已禁用」 |
| 其它业务/系统异常 | 现有 `GlobalExceptionHandler` | 不变 |

说明：Filter 链上的认证失败**不会**进入 `@RestControllerAdvice`，必须单独实现 EntryPoint / AccessDeniedHandler，且 body 与 `Result` 对齐。

## 数据与上下文

- JWT claim：`JwtClaimsConstant.EMP_ID` → 员工 id。
- `SecurityContext`：存放已认证的 `Authentication`（principal 为 `EmployeeUserDetails` 或等价）。
- `BaseContext`：同步写入当前员工 id，供后续 MyBatis 自动填充等使用；务必在请求结束时 `removeCurrentId()`，避免线程池泄漏。

## 测试验收

1. 无 token 访问 `GET /admin/employee/{id}` → 401  
2. 错误或过期 token → 401  
3. 登录成功拿到 token，带 token 访问受保护接口 → 200  
4. 错误密码 / 禁用账号 → 对应业务错误文案  
5. `POST /admin/employee/login` 无需 token  

可选：扩展现有 `EmployeeControllerTest` / `MockMvc`，覆盖上述关键路径。

## 与既有设计的关系

- 延续 `2026-08-01-take-out-hybrid-modules-design` 的模块边界；原「不引入 Spring Security」仅针对骨架阶段，本期正式引入并落在 `framework`。
- 不改为 Interceptor 方案；与「业界 Security Filter 链」对齐，同时保留项目已有 JWT 工具。
