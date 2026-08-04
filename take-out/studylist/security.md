# Spring Security + JWT 完整流程笔记（take-out）

> 目标：弄清「登录一次拿 token → 之后每次请求带 token」整条链路。  

---

## 0. 一句话总览

```text
登录：用户名+明文密码 → Security 验人（BCrypt）→ 发 JWT
业务：请求头带 JWT → Filter 验签 → 标记已登录 → Controller
```

不用 Session；服务端无状态，凭证就是 JWT。

---

## 1. 先建立两张「大图」

### 1.1 类落在哪

```text
take-out-admin          Controller：收登录参数、签发 JWT、返回 VO
        ↓
take-out-framework      SecurityConfig / JWT Filter / 401·403 Handler
        ↓
take-out-system         UserDetails / UserDetailsService / EmployeeService.login
        ↓
take-out-common         JwtUtil / BaseContext / ErrorCode / Result
```

| 模块 | 你写过的关键类 |
|------|----------------|
| common | `JwtUtil`、`BaseContext`、`JwtClaimsConstant`、`Result` |
| system | `EmployeeUserDetails`、`EmployeeUserDetailsService`、`EmployeeServiceImpl` |
| framework | `SecurityConfig`、`JwtAuthenticationFilter`、`JwtAuthenticationEntryPoint`、`JwtAccessDeniedHandler` |
| admin | `EmployeeController`（login + 发 token） |
| 前端 | `request.ts` 请求头 `Authorization`；登录存 `data.data.token` |

## 2. 流程 A：登录（拿 token）

### 2.1 时序

```text
前端 POST /api/employee/login
  （代理成）POST /admin/employee/login
  body: { "username":"admin", "password":"123456" }   ← 明文，不是哈希
        │
        ▼
┌─────────────────────── SecurityFilterChain ───────────────────────┐
│  JwtAuthenticationFilter：登录一般没 token → 直接放行               │
│  路径规则：/admin/employee/login → permitAll                       │
└───────────────────────────────┬───────────────────────────────────┘
                                ▼
                     EmployeeController.login
                                │
                employeeService.login(dto)
                                │
        ┌───────────────────────┴───────────────────────┐
        │           AuthenticationManager                 │
        │  ① UsernamePasswordAuthenticationToken(u, 明文) │
        │     → 此时「待验证」，还不算登录成功               │
        │  ② authenticate(...)                            │
        │       ├─ EmployeeUserDetailsService             │
        │       │    按用户名查 employee 表                │
        │       │    → EmployeeUserDetails（含哈希、status）│
        │       ├─ BCryptPasswordEncoder.matches          │
        │       │    明文 vs 库里 $2a$10$...               │
        │       └─ isEnabled()（status!=0）               │
        │  ③ 成功 → Authentication（principal=用户详情）   │
        │  失败 → BadCredentials / Disabled → 业务异常    │
        └───────────────────────┬───────────────────────┘
                                ▼
              selectById → 完整 Employee
                                ▼
         JwtUtil.createToken(密钥, ttl, { empId: id })
                                ▼
         Result { code:1, data: EmployeeLoginVO
                    { id, username, name, token } }
                                ▼
前端：data.code===1 → 存 cookie/token → 之后请求带头
```

### 2.2 `login` 里每一段在干什么

| 代码 | 作用 |
|------|------|
| `new UsernamePasswordAuthenticationToken(u, p)` | 包装「谁要登录、密码是什么」 |
| `authenticationManager.authenticate(...)` | 查库 + BCrypt + 是否启用 |
| `getPrincipal()` → `EmployeeUserDetails` | 取出认证后的用户，拿 `id` |
| `employeeMapper.selectById` | Security 只验身份；发 VO/JWT 还要完整员工 |
| `catch BadCredentialsException` | 用户不存在或密码错 →「用户名或密码错误」 |
| `catch DisabledException` | 账号禁用 |

### 2.3 Controller 发 JWT

```text
claims = { empId: employee.id }
token  = JwtUtil.createToken(secretKey, ttl, claims)
返回   = EmployeeLoginVO(id, username, name, token)
```

前端需要的登录字段后端都给了：`code=1` + `data.token`（以及 id/username/name）。

---

## 3. 流程 B：带 token 访问业务接口

例如：`GET /admin/employee/1`，请求头：

```http
Authorization: <登录返回的 JWT>
```

（前端 `request.ts` 已改成这个头名；不要再用旧的 `token` 头。）

### 3.1 时序

```text
前端带 Authorization
        │
        ▼
┌──────────────── JwtAuthenticationFilter ────────────────┐
│ 1. 读 Header（名 = jwt.admin-token-name）                 │
│ 2. 无 token？→ 放行（后面 authenticated 会拦成 401）      │
│ 3. 有 token？→ 可去 Bearer 前缀 → JwtUtil.parseToken     │
│ 4. 取出 empId                                            │
│ 5. EmployeeUserDetails.forId(empId)                      │
│ 6. UsernamePasswordAuthenticationToken(用户, null, 权限) │
│    → 密码为 null，表示「已用 JWT 证明过」                  │
│ 7. SecurityContextHolder.setAuthentication(...)  ← 必写  │
│ 8. BaseContext.setCurrentId(empId)                       │
│ 9. filterChain.doFilter → Controller                     │
│ 10.finally：BaseContext.removeCurrentId()               │
└──────────────────────────┬──────────────────────────────┘
                           ▼
              Security：anyRequest().authenticated()
                           │
              有 Authentication → 放行进 Controller
              没有 → EntryPoint → HTTP 401 + Result
                           ▼
                    EmployeeController / Service
```

### 3.2 三种常见结果

| 情况 | 谁处理 | 响应 |
|------|--------|------|
| 没带 token 访问受保护接口 | Security + EntryPoint | HTTP 401 + `Result(UNAUTHORIZED)` |
| token 无效/过期 | Filter 调 EntryPoint | HTTP 401 |
| 已认证但没权限（本期几乎没有） | AccessDeniedHandler | HTTP 403 |
| 登录密码错等业务失败 | `BusinessException` → `GlobalExceptionHandler` | 通常仍 HTTP 200 + `code≠1` |

**重要：** Filter 链上的 401/403 **不会**进 `@RestControllerAdvice`，所以才单独写 EntryPoint / AccessDeniedHandler。

---

## 4. SecurityConfig 在拼什么

`SecurityFilterChain` 相当于「大门规则」：

```text
csrf.disable()                          // JWT 无状态，不开 CSRF
STATELESS                               // 不建 HTTP Session
/admin/employee/login → permitAll       // 登录匿名可进
anyRequest → authenticated              // 其它必须已认证
exceptionHandling → EntryPoint / 403    // 失败时返回我们的 JSON
addFilterBefore(JwtFilter, ...)         // JWT 验签挂在用户名密码过滤器之前
```

另外两个 Bean：

- `PasswordEncoder`：BCrypt  
- `AuthenticationManager`：登录时注入给 `EmployeeServiceImpl`  
- `JwtAuthenticationFilter`：用 `@Value` 注入密钥和头名后 `new` 出来（**不要**给 Filter 加 `@Component`，避免注册两次）

---

## 5. 两个容易混的概念

### 5.1 认证 vs 授权

| | 认证 Authentication | 授权 Authorization |
|--|---------------------|-------------------|
| 问什么 | 你是谁？登录了吗？ | 你能不能做这件事？ |
| 本期 | ✅ JWT + 登录 | ❌ 不做 RBAC |

### 5.2 同一种 Token，两种用法

`UsernamePasswordAuthenticationToken`：

| 场景 | 内容 | 含义 |
|------|------|------|
| 登录 | 用户名 + **明文密码** | 「请验证我」 |
| JWT Filter | 用户对象 + **密码 null** | 「已经验证过，放进上下文」 |

### 5.3 Filter vs Interceptor

| | Filter（我们用的） | Interceptor |
|--|-------------------|-------------|
| 层 | Servlet / Security 链 | Spring MVC |
| 本期 | JWT 验签、401 | 不用 |

业界正规产品多走 **Spring Security Filter 链**；教学项目常写 MVC 拦截器。我们选的是前者。

### 5.4 SecurityContext vs BaseContext

| | SecurityContext | BaseContext |
|--|-----------------|-------------|
| 谁用 | Spring Security | 我们业务（如以后自动填 createUser） |
| 存什么 | Authentication | 当前员工 id（ThreadLocal） |
| 清理 | Security 请求结束策略 | Filter 的 `finally` 里 `remove` |

---

## 6. 前端如何对接（你已改过的部分）

```text
登录成功
  → data.code === 1
  → 保存 data.data.token（cookie 名仍可叫 token，只是本地键名）

之后每个请求
  → headers['Authorization'] = token   // 必须与 yml 一致

密码
  → 前端永远传明文；无需、也不应在前端做 BCrypt
```

---

## 7. 用一次完整「人生」串起来

1. 库里 `admin` 密码 = `$2a$10$...`（BCrypt）  
2. 前端提交 `admin` / `123456`  
3. Security 查库、`matches` 成功  
4. Controller 签发含 `empId` 的 JWT 返回  
5. 前端存 token，之后 `Authorization` 带上  
6. Filter 验签 → 写入 SecurityContext + BaseContext  
7. `GET /admin/employee/1` 进入 Controller，查到员工返回  

任一步对不上（头名错、库仍是明文、Filter 没 `setAuthentication`、没 `return`）都会表现为「登录了却一直 401」或 NPE。

---

## 8. 名词速查

| 名词 | 一句话 |
|------|--------|
| `UserDetails` | Spring 眼里的用户信息 |
| `UserDetailsService` | 按用户名从库加载用户 |
| `PasswordEncoder` | 密码哈希/校验；我们用 BCrypt |
| `AuthenticationManager` | 登录认证总入口 |
| `Authentication` | 一次认证请求或结果 |
| `SecurityContext` | 当前请求「已登录身份」 |
| `BaseContext` | 业务用的当前员工 id |
| `JwtUtil` | 签发/解析 JWT |
| `SecurityFilterChain` | 哪些路径要登录、挂哪些 Filter |
| EntryPoint | 未认证时写 401 JSON |

---

## 9. 自检清单

- [ ] 登录接口无需 token，其它接口需要  
- [ ] 库密码是 BCrypt，前端仍传明文  
- [ ] 请求头名 = `Authorization`（前后端一致）  
- [ ] Filter：无 token 要 `return`；有 token 要 `setAuthentication`  
- [ ] 401 走 EntryPoint；登录业务错误走 GlobalExceptionHandler  

---

## 10. 一句话收尾

> **登录靠 AuthenticationManager + BCrypt 认出你是谁并颁发 JWT；之后每次请求靠 JWT Filter 验签，把你标记为已认证。**  
> SecurityConfig 是大门规则，EntryPoint 是「没登录时的统一拒答」，整条链不依赖 Session。
