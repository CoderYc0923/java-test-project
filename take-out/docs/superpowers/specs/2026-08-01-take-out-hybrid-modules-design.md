# take-out 混合多模块骨架设计

日期：2026-08-01  
状态：已批准并实现  

## 背景

当前 `take-out` 为单模块 Spring Boot 工程（启动类 + `DemoController`）。目标是采用「若依式底座 + 业务域可扩展」的混合多模块结构，先搭可运行的最小底座，业务域模块（菜品、订单等）与 API 入口后续再加。

## 目标

- 将工程改为 Maven 多模块父工程。
- 底座模块：`common`、`pojo`、`system`、`framework`、`admin`。
- 轻量通用能力：统一返回、业务异常、全局异常处理、Web 配置占位。
- 仅一个可启动入口：`take-out-admin`。
- 保持后续可平滑增加 `take-out-api`、`module-dish` 等，而不改核心依赖方向。

## 非目标（本次不做）

- 不创建 `take-out-api`。
- 不创建 `module-dish` / `module-order` 等业务域模块。
- 不引入 MyBatis、Redis、Spring Security。
- 不拆分为微服务或引入注册中心/网关。

## 模块结构

```text
take-out/                         # 父工程（packaging=pom），原地改造
├── pom.xml
├── take-out-common/
├── take-out-pojo/
├── take-out-system/
├── take-out-framework/
└── take-out-admin/               # 唯一 Spring Boot 启动模块
```

### 职责

| 模块 | 职责 |
|------|------|
| `take-out-parent`（根） | 统一依赖版本、子模块列表；不写业务代码 |
| `take-out-common` | `Result`、结果码、`BusinessException`、通用常量；不依赖 Spring Web |
| `take-out-pojo` | `entity` / `dto` / `vo` 包；模型定义 |
| `take-out-system` | 系统业务占位（用户/角色/菜单等未来落点）；`service` / `mapper` 空包 |
| `take-out-framework` | `GlobalExceptionHandler`、`WebMvcConfig` 占位 |
| `take-out-admin` | 启动类、controller、`application.yml`；打可执行 jar |

### 依赖方向（只允许向下）

```text
admin → framework → system → pojo → common
```

规则：

1. 仅 `take-out-admin` 含 `@SpringBootApplication`，仅它配置 `spring-boot-maven-plugin` 打可执行包。
2. 未来业务域模块（如 `module-dish`）依赖 `pojo` / `common`，再由 `admin`（或日后的 `api`）引入。
3. 禁止 `common`、`pojo` 依赖上层模块。
4. 未来 `take-out-api` 与 `admin` 平级，同样依赖 `framework`（或按需依赖），不得被其他模块依赖。

## 包名

统一使用：`com.sky.takeout.*`（示例：`com.sky.takeout.common.result`、`com.sky.takeout.admin.controller`）。

由现有 `com.sky.take_out` 迁移时一并调整。

## 轻量底座类清单

### take-out-common

- `Result<T>`：字段 `code`、`msg`、`data`；提供 `success` / `error` 工厂方法。
- `ErrorCode` 常量类：至少包含成功码与通用失败码（保持简单，不用枚举）。
- `BusinessException`：携带业务码与消息。

### take-out-pojo

- 包：`entity`、`dto`、`vo`。
- 本次可不放具体实体类。

### take-out-system

- 包：`service`、`mapper`（空占位）。
- 不引入持久化依赖。

### take-out-framework

- `GlobalExceptionHandler`：将 `BusinessException` 与未捕获异常转为 `Result`。
- `WebMvcConfig`：空实现或预留跨域扩展点。

### take-out-admin

- `TakeOutAdminApplication`。
- 迁移并改造 `DemoController`：`GET /api/hello` 返回 `Result.success(...)`。
- 配置文件：`application.yml`（可从现有 `application.properties` 迁移等价配置）。

## 工程改造步骤（实现时遵循）

1. 根 `pom.xml` 改为 `packaging=pom`，声明 `<modules>`。
2. 新建五个子模块及各自 `pom.xml`。
3. 将现有启动类、controller、资源文件迁入 `take-out-admin`；通用类写入对应模块。
4. 删除根目录原单模块 `src/`（避免与子模块重复）。
5. 父 POM 继续继承现有 `spring-boot-starter-parent`（当前为 4.1.0），Java 17。

## 验证标准

```bash
mvn clean package -pl take-out-admin -am
```

启动 `take-out-admin` 后访问 `/api/hello`，响应为统一 `Result` JSON（含成功码与数据），而非纯字符串。

## 后续扩展（不在本次范围，仅约定）

- 增加 API 端：新建 `take-out-api` 启动模块，复用 `framework` / `system` / 业务模块。
- 增加业务域：新建 `module-dish` 等，由 `admin` 按需依赖。
- 持久化/安全：在 `system` 或 `framework` 中按需引入，仍遵守单向依赖。

## 决策记录

| 项 | 选择 |
|----|------|
| 落地范围 | 最小可用底座，业务域后加 |
| 启动入口 | 仅 `take-out-admin` |
| 底座代码量 | 轻量（Result + 异常 + 配置占位） |
| 结构风格 | 混合：若依式底座 + 预留业务域扩展 |
| 改造方式 | 当前目录原地改为父工程 |
