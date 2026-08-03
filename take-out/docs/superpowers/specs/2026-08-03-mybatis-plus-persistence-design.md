# take-out MyBatis-Plus 持久化底座设计

日期：2026-08-03  
状态：已批准（待实现）

## 背景

混合多模块骨架已就绪（`common` / `pojo` / `system` / `framework` / `admin`），Docker MySQL（端口 `3307`、库 `take_out`、账号见 `MYSQLACCOUNT.md`）与 `sky.sql` 种子数据已可用。原设计明确「本次不引入 MyBatis」；现提前引入持久化，以便后续业务域直接落库。

## 目标

- 引入 **MyBatis-Plus**（Spring Boot 4 专用 starter）与 **Lombok**。
- 配置数据源并完成 Mapper 扫描 / 驼峰映射。
- 打通一条只读链路：`Employee` 实体 → Mapper → Service → `GET /api/employees/{id}` → 不含密码的 VO。
- 保持现有依赖方向：`admin → framework → system → pojo → common`。

## 非目标

- 不引入分页插件、代码生成器。
- 不做员工 CRUD、登录鉴权、密码加密改造。
- 不写自定义 XML SQL。
- 不拆多环境 profile（`dev`/`prod`）；不做 H2 / Testcontainers。
- 不创建 `take-out-api` 或业务域模块（`module-dish` 等）。

## 技术选型

| 项 | 选择 |
|----|------|
| ORM | MyBatis-Plus `mybatis-plus-spring-boot4-starter` **3.5.17** |
| 驱动 | MySQL Connector/J（Boot 管理版本即可） |
| 实体写法 | Lombok（`@Data` 等） |
| 首表 | `employee`（种子数据含 id=1 的 `admin`） |

## 模块职责与依赖

| 模块 | 本次职责 |
|------|----------|
| 父 POM | `dependencyManagement` 锁定 MyBatis-Plus 版本；按需声明 Lombok |
| `take-out-pojo` | `Employee` 实体、`EmployeeVO`（无 password）；引入 Lombok |
| `take-out-system` | 依赖 MyBatis-Plus starter + MySQL 驱动；`EmployeeMapper`、`EmployeeService` |
| `take-out-framework` | `@MapperScan("com.sky.takeout.**.mapper")`；必要时显式 MyBatis-Plus 驼峰等配置类 |
| `take-out-admin` | `application.yml` 数据源；`EmployeeController` |

依赖方向不变，**禁止** `common` / `pojo` 依赖上层。数据源账号密码仅写在 **admin 启动配置**，不放入 framework。

## 实体 / Mapper / 接口约定

### Employee（`com.sky.takeout.pojo.entity`）

对齐表字段：`id, name, username, password, phone, sex, idNumber, status, createTime, updateTime, createUser, updateUser`。

- `@TableName("employee")`
- 驼峰 ↔ 下划线：MyBatis-Plus `map-underscore-to-camel-case: true`
- Lombok `@Data`

### EmployeeVO（`com.sky.takeout.pojo.vo`）

与对外展示字段一致，**不含 `password`**。Controller 只返回 VO。

### EmployeeMapper（`com.sky.takeout.system.mapper`）

```text
public interface EmployeeMapper extends BaseMapper<Employee> {}
```

按 id 查询使用 `selectById`；本次无 XML。

### EmployeeService（`com.sky.takeout.system.service`）

- `Employee getById(Long id)`（或直接返回 VO，由实现选定一种并保持 Controller 简洁）
- 查无记录时抛 `BusinessException(ErrorCode.ERROR, "员工不存在")`（或等价明确文案）

### EmployeeController（`com.sky.takeout.admin.controller`）

- `GET /api/employees/{id}` → `Result<EmployeeVO>`
- 成功时 `data` 中不得出现 `password` 字段

## 配置

`take-out-admin/src/main/resources/application.yml`（在现有配置上追加）：

```yaml
spring:
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

连接信息与 Docker 映射（主机 `3307`）及 `MYSQLACCOUNT.md` 一致。

## 验证标准

手工：

1. `docker compose up -d`（MySQL healthy）
2. 启动 `take-out-admin`
3. `GET /api/employees/1` → 统一 `Result`，`username` 为 `admin`，无 `password`
4. `GET /api/employees/99999` → 业务错误（员工不存在）

构建：

```bash
.\mvnw.cmd clean test -pl take-out-admin -am
```

（需本机 Docker MySQL 已启动，因现有全量 `@SpringBootTest` 会拉起数据源。）

## 测试策略

| 测试 | 策略 |
|------|------|
| `EmployeeControllerTest` | MockMvc + mock `EmployeeService`，不依赖真实库 |
| 现有 `TakeOutAdminApplicationTests` / `DemoControllerTest` | 保持全量 `@SpringBootTest`；**约定跑测前容器已启动** |
| 本次不做 | H2、Testcontainers、多 profile |

## 决策记录

| 项 | 选择 | 原因 |
|----|------|------|
| MyBatis vs Plus | MyBatis-Plus | 后续 CRUD / 业务开发更省，Boot4 有官方 starter |
| 范围 | 底座 + Employee 只读打通 | 可验证，又不过早做满员工管理 |
| 实体 | Lombok | 减少样板代码 |
| 依赖落点 | starter 在 system，yml 在 admin | 符合「业务落 system、环境在启动模块」 |
| 密码出库 | VO 剥离 | 避免只读演示接口泄露密码字段 |

## 后续扩展（不在本次范围）

- 员工分页列表、增删改、状态启停
- 登录与密码哈希
- 分页插件、多数据源、独立 `application-dev.yml`
- 业务域模块内各自 Mapper（仍由 `@MapperScan` 覆盖）
