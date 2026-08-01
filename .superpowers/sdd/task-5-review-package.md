# Review package Task 5
Deleted: root src/ (old com.sky.take_out)
Root src exists: False

## take-out-admin/src/main/java/com/sky/takeout/admin/TakeOutAdminApplication.java
```
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

## take-out-admin/src/main/java/com/sky/takeout/admin/controller/DemoController.java
```
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

## take-out-admin/src/main/resources/application.yml
```
spring:
  application:
    name: take-out-admin
```

## take-out-admin/src/test/java/com/sky/takeout/admin/TakeOutAdminApplicationTests.java
```
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

## take-out-admin/src/test/java/com/sky/takeout/admin/controller/DemoControllerTest.java
```
package com.sky.takeout.admin.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
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

