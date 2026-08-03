# take-out：「Web → 业务 → 数据库」完整梳理

本文合并 Spring 运行时与 MyBatis-Plus 持久化说明，用 **`GET /api/employees/{id}`** 一条链路，讲清本项目从 HTTP 到 MySQL 的全貌。

```text
Web（Controller） → 业务（Service） → 数据库（Mapper / MyBatis-Plus / MySQL）
```

| 角色 | 类 | 模块 |
|------|-----|------|
| 启动类 | `TakeOutAdminApplication` | admin |
| Web | `EmployeeController` | admin |
| 业务接口 | `EmployeeService` | system |
| 业务实现 | `EmployeeServiceImpl` | system |
| 持久化 | `EmployeeMapper` | system |
| 实体 / VO | `Employee` / `EmployeeVO` | pojo |
| Mapper 扫描 | `MybatisPlusConfig` | framework |
| 异常处理 | `GlobalExceptionHandler` | framework |

---

## 0. 总览：两段生命周期

```text
┌─────────────────────────────────────────────────────────────┐
│  启动（只做一次）                                              │
│  main → 扫包 → 注册 Bean → 构造器注入 → 注册 URL → 起 Tomcat   │
│  同时：读 yml 建 DataSource；@MapperScan 注册 Mapper 代理      │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│  每次请求（可重复）                                            │
│  HTTP → DispatcherServlet → Controller                      │
│       → Service（接口引用 → Impl 实例）                       │
│       → Mapper 代理 → SQL → MySQL → Entity                  │
│       → VO（去 password）→ Result → JSON                    │
└─────────────────────────────────────────────────────────────┘
```

一句话：启动时把对象造好、依赖接好；请求时只在已装配好的链路上走数据。

---

## 1. 工程骨架：模块与依赖

### 1.1 依赖方向（禁止反向）

```text
take-out-admin → take-out-framework → take-out-system → take-out-pojo → take-out-common
```

| 模块 | 在这条链里干什么 |
|------|------------------|
| 父 `pom.xml` | `mybatis-plus-bom:3.5.17` 锁版本 |
| `take-out-pojo` | Entity / VO；只引 `mybatis-plus-annotation` + Lombok |
| `take-out-system` | starter + MySQL 驱动；Mapper / Service |
| `take-out-framework` | `@MapperScan`、全局异常等基础设施 |
| `take-out-admin` | 启动类、Controller、`application.yml` 数据源 |

设计要点：

- **starter 在 system**：持久化跟业务模块走  
- **yml 在 admin**：连接信息只在启动模块  
- **pojo 只引 annotation**：模型层不拉完整 ORM 运行时  

### 1.2 关键依赖

**父 POM**

```xml
<dependency>
    <groupId>com.baomidou</groupId>
    <artifactId>mybatis-plus-bom</artifactId>
    <version>3.5.17</version>
    <type>pom</type>
    <scope>import</scope>
</dependency>
```

**pojo**：`lombok`、`mybatis-plus-annotation`  
**system**：`mybatis-plus-spring-boot4-starter`、`mysql-connector-j`（runtime）

### 1.3 当前范围

| 已做 | 未做 |
|------|------|
| 数据源、Mapper 扫描、驼峰映射 | 分页插件、自定义 XML |
| `GET /api/employees/{id}` 只读打通 | 员工 CRUD、登录鉴权 |
| | 多 profile、H2 / Testcontainers |

---

## 2. 启动阶段：从 `main` 到「可接请求」

### 2.1 入口

```java
@SpringBootApplication(scanBasePackages = "com.sky.takeout")
public class TakeOutAdminApplication {
    public static void main(String[] args) {
        SpringApplication.run(TakeOutAdminApplication.class, args);
    }
}
```

`SpringApplication.run` 会：创建 `ApplicationContext` → 组件扫描 + 自动配置 → 装配 Bean → 启动内嵌 Tomcat（默认 8080）。

`scanBasePackages = "com.sky.takeout"`：admin / framework / system 下的 `@RestController`、`@Service`、`@Configuration` 都会被扫到（Maven 多模块已把它们放进 classpath）。

### 2.2 你写的代码 vs Spring 做什么

你写的是「类 + 注解」，例如 Controller 只依赖 **接口** `EmployeeService`，真正逻辑在 `EmployeeServiceImpl`。  
启动前没有「自动重写」；启动后是 **多态引用 + 容器注入**。

### 2.3 谁进 IoC 容器

| 注解 | 例子 | 含义 |
|------|------|------|
| `@RestController` | `EmployeeController` | Web Bean，返回值序列化为 JSON |
| `@Service` | `EmployeeServiceImpl` | 业务 Bean（进容器的是 Impl，不是接口） |
| `@Configuration` | `MybatisPlusConfig` | 配置 Bean |
| `@Mapper` + `@MapperScan` | `EmployeeMapper` | MyBatis 生成代理并注册为 Bean |

容器示意：

```text
ApplicationContext
├── employeeController     → EmployeeController
├── employeeServiceImpl    → EmployeeServiceImpl（也可按 EmployeeService 类型查找）
├── employeeMapper         → MyBatis 动态代理（实现 EmployeeMapper）
├── mybatisPlusConfig       → 配置类
├── dataSource             → 读 application.yml 自动配置
├── sqlSessionFactory      → MyBatis-Plus 相关
└── ...
```

### 2.4 构造器注入顺序（简化）

```text
1. DataSource / SqlSessionFactory（自动配置 + yml）
2. EmployeeMapper 代理 Bean
3. new EmployeeServiceImpl(employeeMapper)
4. new EmployeeController(employeeService)   // 传入的是 Impl 实例
5. 注册 GET /api/employees/{id} → getById
6. Tomcat 就绪
```

Controller 要 `EmployeeService` 类型 → 容器里唯一可赋值的是 `EmployeeServiceImpl` → 注入。  
**不是重写接口，是把实现对象赋给接口引用。** 若有两个实现，需 `@Primary` / `@Qualifier`，否则启动失败。

本项目用构造器注入：`final` 依赖、缺 Bean 启动即失败、单测易 mock。

### 2.5 持久化相关配置（启动时生效）

**`application.yml`（admin）**

```yaml
spring:
  datasource:
    url: jdbc:mysql://127.0.0.1:3307/take_out?...
    username: takeout_rw
    password: TakeoutRw@123
    driver-class-name: com.mysql.cj.jdbc.Driver

mybatis-plus:
  configuration:
    map-underscore-to-camel-case: true   # id_number ↔ idNumber
  global-config:
    db-config:
      id-type: auto
```

**`MybatisPlusConfig`（framework）**

```java
@Configuration
@MapperScan("com.sky.takeout.system.mapper")
public class MybatisPlusConfig {
}
```

后续新业务模块的 Mapper 包，往 `@MapperScan` 的 `value` 数组追加即可。

---

## 3. 请求阶段：Web → 业务 → 数据库

以 `GET http://localhost:8080/api/employees/1` 为例。

### 3.1 分层调用链

```text
HTTP GET /api/employees/{id}
        │
        ▼  【Web】
EmployeeController                (admin)
  · employeeService.getById(id)   // 编译期：接口；运行期：Impl
  · Employee → EmployeeVO（去 password）
  · Result.success(vo) → JSON
        │
        ▼  【业务】
EmployeeServiceImpl               (system)
  · employeeMapper.selectById(id)
  · null → BusinessException("员工不存在")
        │
        ▼  【数据库】
EmployeeMapper 代理               (system)
  · BaseMapper.selectById
  · 生成 SELECT ... FROM employee WHERE id = ?
        │
        ▼
MySQL employee 表                 (Docker :3307 / take_out)
```

### 3.2 Web：DispatcherServlet → Controller

```text
Tomcat
  → DispatcherServlet
  → 匹配 EmployeeController#getById，解析 id=1
  → 调用 getById(1L)
```

```java
Employee employee = employeeService.getById(id);
return Result.success(toVO(employee));
```

| 点 | 说明 |
|----|------|
| 编译类型 | `EmployeeService` |
| 运行时对象 | 启动时注入的 `EmployeeServiceImpl` |
| 方法分派 | JVM 多态 → 执行 Impl 的 `getById` |

等价手写：

```java
EmployeeMapper mapper = /* MyBatis 代理 */;
EmployeeService service = new EmployeeServiceImpl(mapper);
EmployeeController controller = new EmployeeController(service);
controller.getById(1L);  // 内部调到 Impl
```

Spring 只是启动时自动完成 new 与装配。

### 3.3 业务：ServiceImpl

```java
Employee employee = employeeMapper.selectById(id);
if (employee == null) {
    throw new BusinessException(ErrorCode.ERROR, "员工不存在");
}
return employee;
```

- 返回实体 `Employee`；VO 转换放在 Controller  
- 查无抛业务异常，不返回 `null`  

### 3.4 数据库：Mapper → SQL → 行映射

`EmployeeMapper`：

```java
@Mapper
public interface EmployeeMapper extends BaseMapper<Employee> {
}
```

空接口即可用 Plus 内置 CRUD；当前只用 `selectById`，无 XML。

代理内部大致：

1. 拦截 `selectById(1)`  
2. 读实体 `@TableName("employee")`、`@TableId` 等生成 SQL  
3. 经 `DataSource` 访问 MySQL  
4. 结果行 → `Employee`（yml 已开下划线转驼峰）  

常用 `BaseMapper` 方法（后续扩展）：

| 方法 | 用途 |
|------|------|
| `selectById` | 按主键查 |
| `selectList` | 条件列表 |
| `insert` | 插入 |
| `updateById` | 按主键更新 |
| `deleteById` | 按主键删除 |

### 3.5 实体与 VO

**Entity（对表，可含敏感字段）**

```java
@Data
@TableName("employee")
public class Employee {
    @TableId(type = IdType.AUTO)
    private Long id;
    // name, username, password, phone, sex,
    // idNumber, status, createTime, updateTime, createUser, updateUser
}
```

表结构见 `sky.sql` 的 `employee`。

**VO（对外，无 password）**  
Controller 手工 `toVO`，避免直接序列化 Entity。

### 3.6 正常返回 vs 异常路径

**成功**

```text
Employee → toVO（去 password）→ Result.success → Jackson JSON → HTTP 200
```

**员工不存在**

```text
BusinessException
  → GlobalExceptionHandler
  → Result 错误码 + msg「员工不存在」
  → 本项目业务错误多为 HTTP 200 + body.code 表示失败
```

---

## 4. 「接口为何能调到 Impl」对照表

| 问题 | 答案 |
|------|------|
| 谁实现了接口？ | `EmployeeServiceImpl implements EmployeeService` |
| 谁进了容器？ | 带 `@Service` 的 **Impl** |
| Controller 声明要什么？ | 构造器参数 `EmployeeService` |
| Spring 注入了什么？ | 那个 Impl 实例 |
| 调用执行谁？ | 真实类型上的方法 → Impl#getById |
| 有没有自动重写接口？ | **没有**，是多态 + DI |

Debug：在 `Controller.getById` 打断点 Step Into → 应进入 `EmployeeServiceImpl`；看 `employeeService.getClass()` 一般为 Impl（若有 AOP 可能是代理，最终仍到 Impl 逻辑）。

---

## 5. 端到端怎么跑

### 5.1 起库

```powershell
cd take-out
docker compose up -d
docker compose ps   # take-out-mysql healthy
```

账号见 `MYSQLACCOUNT.md`；种子数据见 `sky.sql`（含 id=1 的 `admin`）。

### 5.2 起应用

```powershell
.\mvnw.cmd spring-boot:run -pl take-out-admin
```

### 5.3 手工验证

```powershell
curl http://localhost:8080/api/employees/1
curl http://localhost:8080/api/employees/99999
```

| 请求 | 期望 |
|------|------|
| id=1 | 成功；`username=admin`；无 `password` |
| id=99999 | 业务错误；`msg=员工不存在` |

### 5.4 测试

```powershell
# Mock Service，不连库
.\mvnw.cmd -pl take-out-admin -am test -Dtest=EmployeeControllerTest

# 全量（含 @SpringBootTest）须先起 Docker MySQL
.\mvnw.cmd clean test -pl take-out-admin -am
```

---

## 6. 新增一张表时怎么接进这条链

1. **库**：MySQL / `sky.sql`（或迁移）建表  
2. **pojo**：`entity`（`@TableName` / `@TableId`）+ 需要的 `vo` / `dto`  
3. **system**：`XxxMapper extends BaseMapper<Xxx>`；`XxxService` + `Impl`  
4. **framework**：Mapper 包若不在 `system.mapper`，更新 `@MapperScan`  
5. **admin**：Controller；VO 转换与敏感字段剥离  
6. **测试**：优先 `WebMvcTest` + mock Service；连库测先起 Docker  

仍遵循同一模式：

```text
Controller（接口依赖）→ ServiceImpl（@Service）→ Mapper 代理 → 表
```

---

## 7. 相关文件索引

| 文件 | 说明 |
|------|------|
| `take-out-admin/.../TakeOutAdminApplication.java` | 启动与扫包 |
| `take-out-admin/.../controller/EmployeeController.java` | Web 入口 |
| `take-out-admin/.../application.yml` | 数据源 + Plus |
| `take-out-system/.../service/EmployeeService.java` | 业务接口 |
| `take-out-system/.../service/impl/EmployeeServiceImpl.java` | 业务实现 |
| `take-out-system/.../mapper/EmployeeMapper.java` | Mapper |
| `take-out-pojo/.../entity/Employee.java` | 实体 |
| `take-out-pojo/.../vo/EmployeeVO.java` | 出参 VO |
| `take-out-framework/.../config/MybatisPlusConfig.java` | `@MapperScan` |
| `take-out-framework/.../web/GlobalExceptionHandler.java` | 异常 → Result |
| `pom.xml` | MyBatis-Plus BOM |
| `docs/superpowers/specs/2026-08-03-mybatis-plus-persistence-design.md` | 设计说明 |
| `docs/superpowers/plans/2026-08-03-mybatis-plus-persistence.md` | 实现计划 |

---

## 8. 建议阅读顺序

1. 本文 **§0～§3**：建立「启动装配 + 请求下钻」心智模型  
2. 对照代码：Application → Controller → ServiceImpl → Mapper → Entity / yml  
3. 断点 Step Into 走一遍 `GET /api/employees/1`  
4. 需要拆分查阅时：`spring-flow.md`（偏 Spring DI）、`mybatisplus.md`（偏持久化配置）均为本文摘要入口  
