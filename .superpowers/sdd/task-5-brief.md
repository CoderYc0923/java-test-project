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
