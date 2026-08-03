# Task 1 Report: 父 POM 与子模块依赖

**Status:** ✅ Complete  
**Date:** 2026-08-03  
**Commits:** None (Step 5 skipped per constraints)

---

## What Was Implemented

### Step 1: 父 `pom.xml` — MyBatis-Plus BOM

在 `<dependencyManagement><dependencies>` 内追加（位于 take-out 内部模块声明之前）：

```xml
<dependency>
    <groupId>com.baomidou</groupId>
    <artifactId>mybatis-plus-bom</artifactId>
    <version>3.5.17</version>
    <type>pom</type>
    <scope>import</scope>
</dependency>
```

### Step 2: `take-out-pojo/pom.xml` — Lombok + annotation

在现有 `take-out-common` 依赖旁追加：

- `org.projectlombok:lombok`（`<optional>true</optional>`）
- `com.baomidou:mybatis-plus-annotation`（版本由 BOM 管理）

### Step 3: `take-out-system/pom.xml` — Starter + MySQL 驱动

在现有 `take-out-pojo` 依赖旁追加：

- `com.baomidou:mybatis-plus-spring-boot4-starter`（版本由 BOM 管理）
- `com.mysql:mysql-connector-j`（`<scope>runtime</scope>`，版本由 Spring Boot parent 管理）

### Step 4: 验证依赖可解析

命令：`.\mvnw.cmd -q dependency:resolve -pl take-out-system -am`

**首次运行（exit 1）：** 因 `take-out-common` 尚未安装到本地仓库（`Could not find artifact com.sky:take-out-common:jar:0.0.1-SNAPSHOT`）。这是多模块项目首次解析时的预期现象，与本次 POM 变更无关。

**补救：** 执行 `.\mvnw.cmd -q install -pl take-out-system -am -DskipTests` 将 reactor 链（common → pojo → system）安装到本地仓库。

**再次运行 brief 命令（exit 0）：** 无 unresolved dependency 错误。

**补充验证（dependency:tree）：**

| 模块 | 依赖 | 解析版本 |
|------|------|----------|
| take-out-pojo | lombok | 1.18.46 (optional) |
| take-out-pojo | mybatis-plus-annotation | 3.5.17 |
| take-out-system | mybatis-plus-spring-boot4-starter | 3.5.17 |
| take-out-system | mysql-connector-j | 9.7.0 (runtime) |

### Step 5: Commit

**Skipped** — 用户约束与 plan 均要求不提交。

---

## Files Changed

| Action | Path |
|--------|------|
| Modified | `take-out/pom.xml` |
| Modified | `take-out/take-out-pojo/pom.xml` |
| Modified | `take-out/take-out-system/pom.xml` |

未触碰：`docker-*`、`docker/` 及其他 dirty 文件。

---

## Self-Review

| 检查项 | 结果 |
|--------|------|
| 仅修改 brief 指定的 3 个 POM | ✅ |
| MyBatis-Plus BOM 3.5.17 import | ✅ |
| pojo: lombok optional + mybatis-plus-annotation | ✅ |
| system: mybatis-plus-spring-boot4-starter + mysql-connector-j runtime | ✅ |
| 子模块未写 Plus 版本号（由 BOM 统一管理） | ✅ |
| 依赖方向 admin → framework → system → pojo → common 未变 | ✅ |
| 未创建 Java 源文件 | ✅ |
| dependency:resolve 最终 exit 0 | ✅ |

**备注：** 全新 clone 环境下，首次 `dependency:resolve -am` 可能需先 `install -am` 安装内部模块；后续 resolve 可独立成功。

---

## Concerns

无阻塞性问题。唯一环境注意事项：多模块 reactor 首次解析需本地已 install 内部 SNAPSHOT 构件。
