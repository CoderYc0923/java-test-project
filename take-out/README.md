# take-out 外卖项目操作手册

基于 Spring Boot 4 / Java 17 的 **Maven 多模块**工程，采用「若依式底座 + 业务域可扩展」的混合结构。

当前可启动入口：**仅 `take-out-admin`（管理端）**。

---

## 1. 环境要求

| 项 | 版本 / 说明 |
|----|-------------|
| JDK | 17+ |
| Maven | 3.9+（也可用项目自带 `mvnw.cmd`） |
| IDE | IntelliJ IDEA / Cursor / VS Code（需能识别多模块 Maven） |

首次打开工程时，请用 **根目录 `pom.xml`** 导入 Maven 父工程，等待依赖下载完成。

---

## 2. 如何启动

### 2.1 命令行（推荐）

在项目根目录 `take-out/` 下执行。多模块首次启动（或改过 common/framework 等依赖模块后）需要先把兄弟模块装进本地仓库：

```powershell
# ① 安装全部模块到本地 .m2（首次 / 改过依赖模块后执行）
.\mvnw.cmd clean install -DskipTests

# ② 只启动 admin
.\mvnw.cmd spring-boot:run -pl take-out-admin
```

或打包后用 jar 运行（一条命令即可，`-am` 会顺带编译依赖模块）：

```powershell
.\mvnw.cmd clean package -pl take-out-admin -am
java -jar take-out-admin\target\take-out-admin-0.0.1-SNAPSHOT.jar
```

说明：

- `-pl take-out-admin`：目标模块是 admin
- `-am`：同时构建它所依赖的 common / pojo / system / framework（适合 `package` / `test`）
- **不要**对 `spring-boot:run` 使用 `-am`：会先打到父工程，报 `Unable to find a suitable main class`
- 仅 `spring-boot:run -pl take-out-admin` 且未 `install` 时，会报找不到 `take-out-framework` 等依赖
### 2.2 IDE 启动

1. 打开根工程，确认右侧 Maven 中能看到 5 个子模块
2. 找到并运行：  
   `take-out-admin` → `com.sky.takeout.admin.TakeOutAdminApplication`

**不要**再找旧的 `TakeOutApplication` / `com.sky.take_out`，根目录单模块 `src` 已移除。

### 2.3 启动验证

默认端口：**8080**

浏览器或 curl 访问：

```text
http://localhost:8080/api/hello
```

期望响应类似：

```json
{"code":1,"msg":"success","data":"Hello, World!"}
```

### 2.4 常用命令

```powershell
# 校验整个反应堆
.\mvnw.cmd validate

# 只跑 admin 及其依赖的测试
.\mvnw.cmd clean test -pl take-out-admin -am

# 只跑 common 单元测试
.\mvnw.cmd test -pl take-out-common
```

---

## 3. 工程目录总览

```text
take-out/                          # 父工程（packaging=pom），统一版本，不写业务
├── pom.xml
├── README.md                      # 本手册
├── docs/                          # 设计/计划文档（可选查阅）
├── take-out-common/               # 通用能力
├── take-out-pojo/                 # 数据模型
├── take-out-system/               # 系统业务（用户/角色等，当前占位）
├── take-out-framework/            # 框架配置（异常、Web 等）
└── take-out-admin/                # 管理端启动入口（可打可执行 jar）
```

依赖只允许 **向下**，禁止反向依赖：

```text
admin → framework → system → pojo → common
```

---

## 4. 各模块含义与该往哪写代码

### 4.1 根工程 `take-out`

| 项 | 说明 |
|----|------|
| 作用 | 声明子模块、统一 Spring Boot / 内部模块版本 |
| 放什么 | 只放父 `pom.xml`、文档、脚本 |
| 不放什么 | 不写 Java 业务、不放启动类 |

### 4.2 `take-out-common` — 通用底座

| 项 | 说明 |
|----|------|
| 作用 | 全项目共用的工具与约定 |
| 典型内容 | `Result`、`ErrorCode`、`BusinessException`、常量、工具类 |
| 包路径 | `com.sky.takeout.common.*` |
| 约束 | **不要**依赖 Spring Web；尽量保持轻量 |

后期开发：新增统一错误码、工具方法、通用注解 → 放这里。

### 4.3 `take-out-pojo` — 模型层

| 项 | 说明 |
|----|------|
| 作用 | 实体与传输对象 |
| 包路径 | `com.sky.takeout.pojo.entity` / `dto` / `vo` |
| 约定 | `entity` 对表；`dto` 入参；`vo` 出参；避免直接把 entity 甩给前端 |

后期开发：新建菜品、订单等模型 → 先写在这里（或未来拆到业务域模块自己的 domain）。

### 4.4 `take-out-system` — 系统业务

| 项 | 说明 |
|----|------|
| 作用 | 用户、角色、菜单、部门等「系统管理」业务 |
| 包路径 | `com.sky.takeout.system.service` / `mapper`（当前为空占位） |
| 约束 | 本阶段未接 MyBatis；接持久化后在此放 Service / Mapper |

后期开发：登录用户、权限相关 → 优先放 system，而不是塞进 admin。

### 4.5 `take-out-framework` — 框架能力

| 项 | 说明 |
|----|------|
| 作用 | Spring 相关基础设施 |
| 已有 | `GlobalExceptionHandler`（异常 → `Result`）、`WebMvcConfig`（Web 占位） |
| 包路径 | `com.sky.takeout.framework.web` / `config` |

后期开发：跨域、拦截器、Jackson 配置、AOP 日志 → 放这里。

### 4.6 `take-out-admin` — 管理端入口

| 项 | 说明 |
|----|------|
| 作用 | **唯一（当前）可启动、可单独打包部署** 的应用 |
| 已有 | `TakeOutAdminApplication`、`DemoController`、`application.yml` |
| 包路径 | `com.sky.takeout.admin.controller` 等 |
| 注意 | 这里写 **管理端接口与启动配置**；业务逻辑尽量下沉到 system / 未来业务模块 |

配置文件位置：

```text
take-out-admin/src/main/resources/application.yml
```

---

## 5. 后期开发怎么放文件（速查）

| 你要做的事 | 放到哪个模块 | 示例路径 |
|------------|--------------|----------|
| 新增管理端 HTTP 接口 | `take-out-admin` | `.../admin/controller/XxxController.java` |
| 改端口、数据源、日志级别 | `take-out-admin` | `application.yml` |
| 写业务 Service / Mapper | `take-out-system`（系统类）或未来业务模块 | `.../system/service/...` |
| 新增表实体 / 入参 DTO / 出参 VO | `take-out-pojo` | `.../pojo/entity|dto|vo/` |
| 统一返回、业务异常、错误码 | `take-out-common` | `.../common/result|exception/` |
| 全局异常、跨域、拦截器 | `take-out-framework` | `.../framework/web|config/` |
| 新增用户端独立服务 | 新建 `take-out-user`（启动模块） | 与 admin 平级，自带 Application |
| 新增菜品/订单等大业务域 | 新建 `module-dish` 等 | 被 admin/user 依赖，自己不启动 |

接口返回建议统一使用：

```java
return Result.success(data);
// 或
throw new BusinessException(ErrorCode.ERROR, "业务提示");
```

---

## 6. 单独打包与多入口扩展

### 6.1 当前：只打管理端

```powershell
.\mvnw.cmd clean package -pl take-out-admin -am
```

产物：

```text
take-out-admin/target/take-out-admin-0.0.1-SNAPSHOT.jar
```

### 6.2 以后加用户端 `take-out-user`

思路：与 `take-out-admin` **平级**再加一个启动模块（自带 `@SpringBootApplication` + `spring-boot-maven-plugin`），依赖 `framework` / `system` / 业务模块。

单独打包示例：

```powershell
.\mvnw.cmd clean package -pl take-out-user -am
```

这样 admin、user 可以：

- 不同端口
- 不同鉴权
- **分开部署**（两个 jar，不是微服务，只是两个可运行入口）

### 6.3 以后加业务域模块（如菜品）

```text
take-out-module-dish/   # 只含业务代码，一般不单独启动
```

由 `take-out-admin`（或 `take-out-user`）在 `pom.xml` 里依赖它即可。

---

## 7. 包名与分层约定

- 统一包前缀：`com.sky.takeout.*`
- Controller → Service → Mapper（接入持久化后）
- Controller 只做参数接收与调用；事务与业务放 Service
- 禁止：`common` / `pojo` 依赖 `admin`、`framework`

---

## 8. 当前已具备 / 尚未接入

**已具备**

- 多模块骨架与单向依赖
- 统一返回 `Result`、业务异常、全局异常处理
- 管理端示例接口 `/api/hello`

**尚未接入（后续按需加）**

- MyBatis / 数据库
- Redis
- Spring Security / JWT
- `take-out-user`、业务域模块（菜品、订单等）

更细的设计说明见：`docs/superpowers/specs/2026-08-01-take-out-hybrid-modules-design.md`。

---

## 9. 常见问题

**Q：在根目录直接 Run 找不到启动类？**  
A：启动类在 `take-out-admin` 模块：`TakeOutAdminApplication`。

**Q：`Port 8080 was already in use`？**  
A：8080 已被占用（常见是上次启动的 Java 没关）。PowerShell 结束占用进程：  
`Get-NetTCPConnection -LocalPort 8080 -State Listen | ForEach-Object { Stop-Process -Id $_.OwningProcess -Force }`  
然后再启动；或在 `application.yml` 改端口：`server.port: 8081`。

**Q：`Could not resolve dependencies ... take-out-framework`？**  
A：本地仓库还没有兄弟模块。先在根目录执行：  
`.\mvnw.cmd clean install -DskipTests`  
再执行：`.\mvnw.cmd spring-boot:run -pl take-out-admin`（不要加 `-am`）。

**Q：`spring-boot:run -pl take-out-admin -am` 报找不到 main class？**  
A：`-am` 会把父 POM 也算进反应堆，`run` 目标先打到父工程上。对 `run` 不要加 `-am`，改用上面的 `install` + `run`。

**Q：改了 common，admin 没生效？**  
A：再执行一次 `.\mvnw.cmd clean install -DskipTests`，或 `.\mvnw.cmd install -pl take-out-common,take-out-pojo,take-out-system,take-out-framework -am`；IDE 里对父工程执行 Maven Reload。

**Q：可以只启动 framework 吗？**  
A：不可以。framework / common / pojo / system 都是库模块，只有带启动类的模块（目前是 admin）才能运行。

**Q：这是微服务吗？**  
A：不是。这是多模块单体；多个启动模块（admin / user）也只是多个可部署 jar，进程间默认不走注册中心。
