# take-out 混合多模块骨架 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将单模块 `take-out` 原地改造为混合多模块骨架（common / pojo / system / framework / admin），并提供统一 `Result` 与全局异常处理，使 `GET /api/hello` 返回统一 JSON。

**Architecture:** 根工程改为 `packaging=pom` 的父 POM；依赖单向 `admin → framework → system → pojo → common`；仅 `take-out-admin` 可启动并打可执行 jar；包名统一为 `com.sky.takeout.*`。

**Tech Stack:** Java 17、Maven、Spring Boot 4.1.0（`spring-boot-starter-webmvc`）、JUnit 5、Lombok（可选，本计划 common 手写 getter/setter 工厂方法，不强制 Lombok）

## Global Constraints

- Spring Boot parent：`4.1.0`；Java：`17`
- 模块名：`take-out-common`、`take-out-pojo`、`take-out-system`、`take-out-framework`、`take-out-admin`
- 包名：`com.sky.takeout.*`（不再使用 `com.sky.take_out`）
- 依赖方向：`admin → framework → system → pojo → common`，禁止反向依赖
- 仅 `take-out-admin` 含 `@SpringBootApplication` 与 `spring-boot-maven-plugin`
- 本次不引入 MyBatis、Redis、Security；不创建 api / 业务域模块
- Commit：仅在用户明确要求时执行；计划中的 commit 步骤默认跳过，除非用户授权

---

## File Structure

| 路径 | 职责 |
|------|------|
| `pom.xml` | 父 POM：`packaging=pom`、modules、dependencyManagement |
| `take-out-common/pom.xml` | common 模块 POM（无 Web 依赖） |
| `take-out-common/.../result/ErrorCode.java` | 成功/失败码常量 |
| `take-out-common/.../result/Result.java` | 统一返回体 |
| `take-out-common/.../exception/BusinessException.java` | 业务异常 |
| `take-out-common/.../ResultTest.java` | Result / 异常单元测试 |
| `take-out-pojo/pom.xml` | pojo 模块 POM |
| `take-out-pojo/.../entity/package-info.java` | entity 包占位 |
| `take-out-pojo/.../dto/package-info.java` | dto 包占位 |
| `take-out-pojo/.../vo/package-info.java` | vo 包占位 |
| `take-out-system/pom.xml` | system 模块 POM |
| `take-out-system/.../service/package-info.java` | service 包占位 |
| `take-out-system/.../mapper/package-info.java` | mapper 包占位 |
| `take-out-framework/pom.xml` | framework 模块 POM（含 webmvc） |
| `take-out-framework/.../web/GlobalExceptionHandler.java` | 全局异常 → Result |
| `take-out-framework/.../config/WebMvcConfig.java` | WebMvc 配置占位 |
| `take-out-admin/pom.xml` | 启动模块 POM + boot plugin |
| `take-out-admin/.../TakeOutAdminApplication.java` | 启动类，`scanBasePackages=com.sky.takeout` |
| `take-out-admin/.../controller/DemoController.java` | `/api/hello` → `Result` |
| `take-out-admin/src/main/resources/application.yml` | 应用配置 |
| `take-out-admin/.../TakeOutAdminApplicationTests.java` | 上下文加载测试 |
| `take-out-admin/.../controller/DemoControllerTest.java` | MockMvc 接口测试 |
| 删除：根目录 `src/` | 避免与子模块冲突 |

---

### Task 1: 父 POM 与五子模块脚手架

**Files:**
- Modify: `pom.xml`
- Create: `take-out-common/pom.xml`
- Create: `take-out-pojo/pom.xml`
- Create: `take-out-system/pom.xml`
- Create: `take-out-framework/pom.xml`
- Create: `take-out-admin/pom.xml`

**Interfaces:**
- Consumes: 无
- Produces: 可解析的多模块 Maven 反应堆；内部坐标 `com.sky:take-out-*:0.0.1-SNAPSHOT`

- [ ] **Step 1: 重写根 `pom.xml` 为父工程**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>4.1.0</version>
        <relativePath/>
    </parent>

    <groupId>com.sky</groupId>
    <artifactId>take-out</artifactId>
    <version>0.0.1-SNAPSHOT</version>
    <packaging>pom</packaging>
    <name>take-out</name>

    <modules>
        <module>take-out-common</module>
        <module>take-out-pojo</module>
        <module>take-out-system</module>
        <module>take-out-framework</module>
        <module>take-out-admin</module>
    </modules>

    <properties>
        <java.version>17</java.version>
        <take-out.version>0.0.1-SNAPSHOT</take-out.version>
    </properties>

    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>com.sky</groupId>
                <artifactId>take-out-common</artifactId>
                <version>${take-out.version}</version>
            </dependency>
            <dependency>
                <groupId>com.sky</groupId>
                <artifactId>take-out-pojo</artifactId>
                <version>${take-out.version}</version>
            </dependency>
            <dependency>
                <groupId>com.sky</groupId>
                <artifactId>take-out-system</artifactId>
                <version>${take-out.version}</version>
            </dependency>
            <dependency>
                <groupId>com.sky</groupId>
                <artifactId>take-out-framework</artifactId>
                <version>${take-out.version}</version>
            </dependency>
        </dependencies>
    </dependencyManagement>
</project>
```

- [ ] **Step 2: 创建 `take-out-common/pom.xml`**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>com.sky</groupId>
        <artifactId>take-out</artifactId>
        <version>0.0.1-SNAPSHOT</version>
    </parent>
    <artifactId>take-out-common</artifactId>
    <name>take-out-common</name>
    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>
</project>
```

- [ ] **Step 3: 创建 `take-out-pojo/pom.xml`**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>com.sky</groupId>
        <artifactId>take-out</artifactId>
        <version>0.0.1-SNAPSHOT</version>
    </parent>
    <artifactId>take-out-pojo</artifactId>
    <name>take-out-pojo</name>
    <dependencies>
        <dependency>
            <groupId>com.sky</groupId>
            <artifactId>take-out-common</artifactId>
        </dependency>
    </dependencies>
</project>
```

- [ ] **Step 4: 创建 `take-out-system/pom.xml`**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>com.sky</groupId>
        <artifactId>take-out</artifactId>
        <version>0.0.1-SNAPSHOT</version>
    </parent>
    <artifactId>take-out-system</artifactId>
    <name>take-out-system</name>
    <dependencies>
        <dependency>
            <groupId>com.sky</groupId>
            <artifactId>take-out-pojo</artifactId>
        </dependency>
    </dependencies>
</project>
```

- [ ] **Step 5: 创建 `take-out-framework/pom.xml`**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>com.sky</groupId>
        <artifactId>take-out</artifactId>
        <version>0.0.1-SNAPSHOT</version>
    </parent>
    <artifactId>take-out-framework</artifactId>
    <name>take-out-framework</name>
    <dependencies>
        <dependency>
            <groupId>com.sky</groupId>
            <artifactId>take-out-system</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-webmvc</artifactId>
        </dependency>
    </dependencies>
</project>
```

- [ ] **Step 6: 创建 `take-out-admin/pom.xml`**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>com.sky</groupId>
        <artifactId>take-out</artifactId>
        <version>0.0.1-SNAPSHOT</version>
    </parent>
    <artifactId>take-out-admin</artifactId>
    <name>take-out-admin</name>
    <dependencies>
        <dependency>
            <groupId>com.sky</groupId>
            <artifactId>take-out-framework</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-webmvc-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>
    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
```

- [ ] **Step 7: 校验反应堆可解析**

Run: `mvn -q -N validate` 然后 `mvn -q validate`  
Expected: BUILD SUCCESS（子模块尚无源码也可 validate）

- [ ] **Step 8: Commit（仅用户授权时）**

```bash
git add pom.xml take-out-common/pom.xml take-out-pojo/pom.xml take-out-system/pom.xml take-out-framework/pom.xml take-out-admin/pom.xml
git commit -m "build: scaffold hybrid multi-module parent and child POMs"
```

---

### Task 2: common — ErrorCode / Result / BusinessException

**Files:**
- Create: `take-out-common/src/main/java/com/sky/takeout/common/result/ErrorCode.java`
- Create: `take-out-common/src/main/java/com/sky/takeout/common/result/Result.java`
- Create: `take-out-common/src/main/java/com/sky/takeout/common/exception/BusinessException.java`
- Test: `take-out-common/src/test/java/com/sky/takeout/common/result/ResultTest.java`

**Interfaces:**
- Consumes: 无
- Produces:
  - `ErrorCode.SUCCESS = 1`，`ErrorCode.ERROR = 0`（int）
  - `Result<T>`：`getCode()` / `getMsg()` / `getData()`；`success()` / `success(T)` / `error(String)` / `error(Integer, String)`
  - `BusinessException(Integer code, String message)`；`getCode()`

- [ ] **Step 1: 写失败测试 `ResultTest`**

```java
package com.sky.takeout.common.result;

import com.sky.takeout.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ResultTest {

    @Test
    void success_withData_usesSuccessCode() {
        Result<String> result = Result.success("ok");
        assertEquals(ErrorCode.SUCCESS, result.getCode());
        assertEquals("ok", result.getData());
    }

    @Test
    void error_usesErrorCode() {
        Result<Void> result = Result.error("fail");
        assertEquals(ErrorCode.ERROR, result.getCode());
        assertEquals("fail", result.getMsg());
    }

    @Test
    void businessException_carriesCodeAndMessage() {
        BusinessException ex = new BusinessException(ErrorCode.ERROR, "biz");
        assertEquals(ErrorCode.ERROR, ex.getCode());
        assertEquals("biz", ex.getMessage());
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `mvn -pl take-out-common test -Dtest=ResultTest`  
Expected: FAIL（类不存在或编译失败）

- [ ] **Step 3: 实现 `ErrorCode`**

```java
package com.sky.takeout.common.result;

public final class ErrorCode {
    public static final Integer SUCCESS = 1;
    public static final Integer ERROR = 0;

    private ErrorCode() {
    }
}
```

- [ ] **Step 4: 实现 `Result`**

```java
package com.sky.takeout.common.result;

import java.io.Serializable;

public class Result<T> implements Serializable {
    private Integer code;
    private String msg;
    private T data;

    public static <T> Result<T> success() {
        return success(null);
    }

    public static <T> Result<T> success(T data) {
        Result<T> result = new Result<>();
        result.code = ErrorCode.SUCCESS;
        result.msg = "success";
        result.data = data;
        return result;
    }

    public static <T> Result<T> error(String msg) {
        return error(ErrorCode.ERROR, msg);
    }

    public static <T> Result<T> error(Integer code, String msg) {
        Result<T> result = new Result<>();
        result.code = code;
        result.msg = msg;
        return result;
    }

    public Integer getCode() {
        return code;
    }

    public String getMsg() {
        return msg;
    }

    public T getData() {
        return data;
    }
}
```

- [ ] **Step 5: 实现 `BusinessException`**

```java
package com.sky.takeout.common.exception;

public class BusinessException extends RuntimeException {
    private final Integer code;

    public BusinessException(Integer code, String message) {
        super(message);
        this.code = code;
    }

    public Integer getCode() {
        return code;
    }
}
```

- [ ] **Step 6: 运行测试确认通过**

Run: `mvn -pl take-out-common test -Dtest=ResultTest`  
Expected: BUILD SUCCESS，tests passed

- [ ] **Step 7: Commit（仅用户授权时）**

```bash
git add take-out-common
git commit -m "feat(common): add Result, ErrorCode, and BusinessException"
```

---

### Task 3: pojo / system 包占位

**Files:**
- Create: `take-out-pojo/src/main/java/com/sky/takeout/pojo/entity/package-info.java`
- Create: `take-out-pojo/src/main/java/com/sky/takeout/pojo/dto/package-info.java`
- Create: `take-out-pojo/src/main/java/com/sky/takeout/pojo/vo/package-info.java`
- Create: `take-out-system/src/main/java/com/sky/takeout/system/service/package-info.java`
- Create: `take-out-system/src/main/java/com/sky/takeout/system/mapper/package-info.java`

**Interfaces:**
- Consumes: Task 1 模块坐标
- Produces: 可编译的空包占位（Git 可跟踪）

- [ ] **Step 1: 写入 package-info 文件**

每个文件内容分别为：

```java
package com.sky.takeout.pojo.entity;
```

```java
package com.sky.takeout.pojo.dto;
```

```java
package com.sky.takeout.pojo.vo;
```

```java
package com.sky.takeout.system.service;
```

```java
package com.sky.takeout.system.mapper;
```

- [ ] **Step 2: 编译 pojo 与 system**

Run: `mvn -pl take-out-pojo,take-out-system -am compile`  
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit（仅用户授权时）**

```bash
git add take-out-pojo take-out-system
git commit -m "chore: add pojo and system package placeholders"
```

---

### Task 4: framework — 全局异常与 WebMvc 占位

**Files:**
- Create: `take-out-framework/src/main/java/com/sky/takeout/framework/web/GlobalExceptionHandler.java`
- Create: `take-out-framework/src/main/java/com/sky/takeout/framework/config/WebMvcConfig.java`

**Interfaces:**
- Consumes: `Result`、`ErrorCode`、`BusinessException`
- Produces:
  - `@RestControllerAdvice` 处理 `BusinessException` → `Result.error(code, msg)`
  - 处理 `Exception` → `Result.error("系统异常，请稍后重试")`
  - `WebMvcConfig` 实现 `WebMvcConfigurer`（空方法体即可）

- [ ] **Step 1: 实现 `GlobalExceptionHandler`**

```java
package com.sky.takeout.framework.web;

import com.sky.takeout.common.exception.BusinessException;
import com.sky.takeout.common.result.Result;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public Result<Void> handleBusinessException(BusinessException ex) {
        return Result.error(ex.getCode(), ex.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(Exception ex) {
        return Result.error("系统异常，请稍后重试");
    }
}
```

- [ ] **Step 2: 实现 `WebMvcConfig`**

```java
package com.sky.takeout.framework.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {
}
```

- [ ] **Step 3: 编译 framework**

Run: `mvn -pl take-out-framework -am compile`  
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit（仅用户授权时）**

```bash
git add take-out-framework
git commit -m "feat(framework): add global exception handler and WebMvcConfig"
```

---

### Task 5: admin 启动模块迁移与 `/api/hello`

**Files:**
- Create: `take-out-admin/src/main/java/com/sky/takeout/admin/TakeOutAdminApplication.java`
- Create: `take-out-admin/src/main/java/com/sky/takeout/admin/controller/DemoController.java`
- Create: `take-out-admin/src/main/resources/application.yml`
- Create: `take-out-admin/src/test/java/com/sky/takeout/admin/TakeOutAdminApplicationTests.java`
- Create: `take-out-admin/src/test/java/com/sky/takeout/admin/controller/DemoControllerTest.java`
- Delete: `src/main/java/com/sky/take_out/**`
- Delete: `src/main/resources/application.properties`
- Delete: `src/test/java/com/sky/take_out/**`
- Delete: 根目录空余 `src/` 树

**Interfaces:**
- Consumes: `Result.success(T)`；framework 组件需被扫描
- Produces: `GET /api/hello` → JSON `{"code":1,"msg":"success","data":"Hello, World!"}`

- [ ] **Step 1: 写 `DemoControllerTest`（先失败）**

```java
package com.sky.takeout.admin.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class DemoControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void hello_returnsUnifiedResult() throws Exception {
        mockMvc.perform(get("/api/hello"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1))
                .andExpect(jsonPath("$.data").value("Hello, World!"));
    }
}
```

- [ ] **Step 2: 实现启动类**

```java
package com.sky.takeout.admin;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.sky.takeout")
public class TakeOutAdminApplication {

    public static void main(String[] args) {
        SpringApplication.run(TakeOutAdminApplication.class, args);
    }
}
```

- [ ] **Step 3: 实现 `DemoController`**

```java
package com.sky.takeout.admin.controller;

import com.sky.takeout.common.result.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class DemoController {

    @GetMapping("/hello")
    public Result<String> hello() {
        return Result.success("Hello, World!");
    }
}
```

- [ ] **Step 4: 写入 `application.yml`**

```yaml
spring:
  application:
    name: take-out-admin
```

- [ ] **Step 5: 迁移上下文测试**

```java
package com.sky.takeout.admin;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class TakeOutAdminApplicationTests {

    @Test
    void contextLoads() {
    }
}
```

- [ ] **Step 6: 删除根目录旧 `src/`**

删除：

- `src/main/java/com/sky/take_out/TakeOutApplication.java`
- `src/main/java/com/sky/take_out/controller/DemoController.java`
- `src/main/resources/application.properties`
- `src/test/java/com/sky/take_out/TakeOutApplicationTests.java`

并移除已空的 `src` 目录树。

- [ ] **Step 7: 运行 admin 测试与打包**

Run:

```bash
mvn clean package -pl take-out-admin -am
```

Expected: BUILD SUCCESS；`DemoControllerTest` 与 `TakeOutAdminApplicationTests` 通过；产物 `take-out-admin/target/take-out-admin-0.0.1-SNAPSHOT.jar`

- [ ] **Step 8: Commit（仅用户授权时）**

```bash
git add take-out-admin
git add -u src
git commit -m "feat(admin): migrate boot entry and return Result from /api/hello"
```

---

### Task 6: 规格状态回写与最终核对

**Files:**
- Modify: `docs/superpowers/specs/2026-08-01-take-out-hybrid-modules-design.md`（状态改为已实现/已批准落地）

- [ ] **Step 1: 将设计文档状态从「待用户审阅」改为「已批准并实现」**

- [ ] **Step 2: 最终命令复核**

Run: `mvn clean test -pl take-out-admin -am`  
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit docs（仅用户授权时）**

```bash
git add docs/superpowers/specs/2026-08-01-take-out-hybrid-modules-design.md
git commit -m "docs: mark hybrid modules design as implemented"
```

---

## Spec Coverage Checklist

| 规格要求 | 对应任务 |
|----------|----------|
| 父工程 + 五子模块 | Task 1 |
| Result / ErrorCode / BusinessException | Task 2 |
| pojo entity/dto/vo 包 | Task 3 |
| system service/mapper 占位 | Task 3 |
| GlobalExceptionHandler + WebMvcConfig | Task 4 |
| admin 启动 + DemoController + yml | Task 5 |
| 删除根 `src/` | Task 5 |
| `mvn clean package -pl take-out-admin -am` | Task 5 / 6 |
| 不引入 MyBatis/Redis/Security/api/业务域 | 全任务遵守 |

## Self-Review Notes

- 无 TBD/占位步骤；类签名在 Task 2/4/5 一致（`ErrorCode.SUCCESS=1`、`Result.success/error`、`BusinessException.getCode()`）。
- `@SpringBootApplication(scanBasePackages = "com.sky.takeout")` 确保 framework 中 `@RestControllerAdvice` / `@Configuration` 生效。
- common 使用 `spring-boot-starter-test`（非 webmvc-test），避免无意义 Web 测试依赖。
