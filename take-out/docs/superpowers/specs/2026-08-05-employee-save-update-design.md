# 员工管理：新增与编辑设计

日期：2026-08-05  
状态：待审阅

## 背景

管理端已具备：

- 员工登录（JWT + BCrypt + Spring Security）
- 按 ID 查询员工（`GET /admin/employee/{id}`）

尚未具备新增、编辑员工。前端表单字段为：账号、姓名、手机号、性别、身份证号；新增时密码默认为 `123456`。

## 目标

- 实现新增员工、编辑员工两个接口，对齐业界分层与校验习惯。
- 与现有模块边界一致：`admin → framework → system → pojo → common`。
- 复用已有 `Result`、`BusinessException`、`PasswordEncoder`、`BaseContext`。
- 为后续分页查询、启用禁用复用同一套 DTO/校验/审计模式。

## 非目标（本期不做）

- 员工分页查询、启用/禁用账号、删除员工。
- 修改密码 / 重置密码独立接口。
- RBAC、操作日志、导入导出。
- 前端页面实现（仅约定后端契约）。

## 已确认决策

| 项 | 决策 |
|----|------|
| 学习方式 | 先完整设计文档审阅，再分步实现并讲解关键决策 |
| 编辑时 username | **不可改**（UpdateDTO 不含 username；Service 忽略任何误传） |
| 身份证号 | **可选**；库表 `id_number` 改为允许 NULL；有值时校验 18 位格式 |
| 入参 DTO | 拆分 `EmployeeSaveDTO` 与 `EmployeeUpdateDTO` |
| 参数校验 | 接入 Bean Validation（`@Valid` + 注解） |
| 审计字段 | MyBatis-Plus `MetaObjectHandler` + `BaseContext.getCurrentId()` |
| 接口风格 | 管理端习惯：`POST/PUT /admin/employee`（编辑 id 在 body） |

## 方案选型

采用：**标准分层增量（方案 2）**。

- API 路径贴近苍穹外卖管理端习惯，降低前后端对接成本。
- 内部实现采用拆分 DTO、Bean Validation、自动填充，避免后续模块重复造轮子。
- 不采用更 REST 的 `PUT /admin/employee/{id}`（本期优先契约一致性）。

## 架构与数据流

```text
新增：
Client → POST /admin/employee + JWT
      → @Valid EmployeeSaveDTO
      → EmployeeService.save
         ├─ username 唯一校验
         ├─ 默认密码 BCrypt("123456")
         ├─ status = 1
         └─ insert（MetaObjectHandler 填 create/update 审计字段）
      → Result.success()

编辑：
Client → PUT /admin/employee + JWT
      → @Valid EmployeeUpdateDTO
      → EmployeeService.update
         ├─ 按 id 查存在性
         ├─ 只更新 name/phone/sex/idNumber
         └─ updateById（自动填 updateTime/updateUser）
      → Result.success()
```

鉴权：两个接口均需登录（已在 Security 默认 `authenticated` 覆盖内，无需改白名单）。

## API 契约

| 能力 | 方法 | 路径 | 入参 | 出参 |
|------|------|------|------|------|
| 新增员工 | POST | `/admin/employee` | `EmployeeSaveDTO` | `Result<Void>`（`code=1`） |
| 编辑员工 | PUT | `/admin/employee` | `EmployeeUpdateDTO` | `Result<Void>` |
| 回显（已有） | GET | `/admin/employee/{id}` | path `id` | `Result<EmployeeVO>` |

说明：新增成功不强制返回新 id；若联调需要可后续改为 `Result<Long>`，本期保持简单。

## DTO 与校验规则

### EmployeeSaveDTO

| 字段 | 类型 | 校验 |
|------|------|------|
| username | String | `@NotBlank`；长度建议 3–32 |
| name | String | `@NotBlank`；长度建议 ≤32 |
| phone | String | `@NotBlank`；`@Pattern(regexp = "^1\\d{10}$")` |
| sex | String | `@NotBlank`；`@Pattern(regexp = "^[01]$")`（`1`=男，`0`=女，与现有种子数据一致） |
| idNumber | String | 可选；非空时 `@Pattern` 校验 18 位身份证（含末位 X/x） |

不含 password；由服务端写入默认值。

### EmployeeUpdateDTO

| 字段 | 类型 | 校验 |
|------|------|------|
| id | Long | `@NotNull` |
| name | String | 同 Save |
| phone | String | 同 Save |
| sex | String | 同 Save |
| idNumber | String | 同 Save |

不含 username、password、status。

### 可选身份证校验细节

- 空串 / null：视为未填写，入库为 `null`（Save/Update 前将 blank 规范化为 null）。
- 有值：匹配 `^[1-9]\\d{5}(18|19|20)\\d{2}(0[1-9]|1[0-2])(0[1-9]|[12]\\d|3[01])\\d{3}[\\dXx]$`（实用级格式校验，不做校验位算法）。

## Service 规则

### save(EmployeeSaveDTO)

1. `username` 查重；存在则 `BusinessException(ErrorCode.CONFLICT, "账号已存在")`。
2. 映射 Entity：业务字段来自 DTO；`password = passwordEncoder.encode(PasswordConstant.DEFAULT_PASSWORD)`；`status = 1`。
3. `idNumber` blank → null。
4. `employeeMapper.insert(entity)`。
5. 不返回 Entity 给 Controller 也可；Controller 直接 `Result.success()`。

### update(EmployeeUpdateDTO)

1. `getById(id)`，不存在则 `BusinessException(ErrorCode.ERROR, "员工不存在")`。
2. 仅 `setName` / `setPhone` / `setSex` / `setIdNumber`（blank → null）。
3. **禁止**修改 username、password、status。
4. `employeeMapper.updateById(entity)`。

注意：`updateById` 默认对 null 字段策略需确认——若希望「清空身份证」生效，应允许 `idNumber=null` 写入；若 MyBatis-Plus 默认忽略 null，则需对该字段使用策略注解或 `UpdateWrapper.set`。**本期要求：显式支持将身份证更新为空（null）。**

## 审计字段自动填充

### Entity 注解

在 `Employee` 上：

- `createTime` / `createUser`：`@TableField(fill = FieldFill.INSERT)`
- `updateTime` / `updateUser`：`@TableField(fill = FieldFill.INSERT_UPDATE)`

### MetaObjectHandler

放置于 `take-out-framework`（或 `system`，与现有 MyBatis 配置所在模块一致；当前 `MybatisPlusConfig` 在 framework，故 Handler 放 framework）：

- insert：`createTime`/`updateTime` = now；`createUser`/`updateUser` = `BaseContext.getCurrentId()`（可为 null 时跳过或写 null）
- update：`updateTime` = now；`updateUser` = `BaseContext.getCurrentId()`

## 默认密码常量

`take-out-common` 新增：

```text
com.sky.takeout.common.constant.PasswordConstant
  DEFAULT_PASSWORD = "123456"
```

仅服务端使用；不通过 API 暴露。

## 数据库变更

`employee.id_number`：`NOT NULL` → `DEFAULT NULL`（可空）。

提供：

1. 更新 `sky.sql` 中建表定义（新环境初始化一致）。
2. 已有库执行一次：

```sql
ALTER TABLE employee MODIFY COLUMN id_number varchar(18) NULL COMMENT '身份证号';
```

性别 `sex` 保持 `NOT NULL`，取值 `"0"` / `"1"`。

## 模块与文件变更清单

| 模块 | 变更 |
|------|------|
| common | `PasswordConstant` |
| pojo | `EmployeeSaveDTO`、`EmployeeUpdateDTO`；`Employee` 增加 fill 注解；validation 依赖（若注解写在 pojo） |
| system | `EmployeeService`/`Impl` 增加 `save`/`update` |
| framework | `MetaObjectHandler`；`GlobalExceptionHandler` 处理 `MethodArgumentNotValidException`（及必要时 `ConstraintViolationException`） |
| admin | Controller 增加 POST/PUT；引入 validation；补充 `EmployeeControllerTest` |
| sql | `sky.sql` + 迁移说明 |

依赖：在放置 `@NotBlank` 等注解的模块引入 `spring-boot-starter-validation`（通常 `pojo` 用 `jakarta.validation-api`，`admin`/`framework` 用完整 starter 以启用校验）。

## 错误处理约定

| 场景 | HTTP | body |
|------|------|------|
| Bean Validation 失败 | 200 + `code=400` | `Result.error(ErrorCode.BAD_REQUEST, 第一条字段错误消息)` |
| 账号已存在 | 200 + `code=409` | `Result.error(ErrorCode.CONFLICT, "账号已存在")` |
| 员工不存在 | 200 + `code=500`（沿用现有 `getById` 的 `ErrorCode.ERROR`） | `Result.error(ErrorCode.ERROR, "员工不存在")` |
| 未登录 | 401 | 已有 EntryPoint |

说明：项目当前业务异常多为 HTTP 200 + `code != 1`。校验失败跟同一约定，避免前端两套解析逻辑。

## 测试计划

Controller 层（`@WebMvcTest` + Mock Service，延续现有 `addFilters = false` 模式）：

1. 新增成功 → 200 / `code=1`
2. 新增缺必填字段 → `code=400` + 错误信息
3. 新增账号重复 → Service 抛业务异常 → 对应错误消息
4. 编辑成功
5. 编辑不存在 id → 员工不存在
6. （可选）Service 单测：默认密码已 BCrypt；update 不改 username

## 风险与注意点

- `updateById` 对 null 字段的忽略策略会影响「清空身份证」；实现时必须验证。
- 默认密码明文仅存常量，入库必须 BCrypt。
- 性别编码必须与前端约定为 `"0"`/`"1"`，不要混用「男/女」字符串。
- 唯一索引 `idx_username` 仍是最后一道防线；业务层先查重以返回友好错误（避免只抛 SQL 异常）。

## 实现顺序建议（供后续 plan）

1. 依赖与校验基础设施（validation + GlobalExceptionHandler）
2. 常量、DTO、Entity fill 注解、MetaObjectHandler
3. DB 变更
4. Service save/update
5. Controller 接口
6. 测试与手工联调
