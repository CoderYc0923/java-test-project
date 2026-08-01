# Final branch review package (working tree, no commits)
## File list
take-out-common/src/main/java/com/sky/takeout/common/exception/BusinessException.java
take-out-common/src/main/java/com/sky/takeout/common/result/ErrorCode.java
take-out-common/src/main/java/com/sky/takeout/common/result/Result.java
take-out-common/src/test/java/com/sky/takeout/common/result/ResultTest.java
take-out-common/pom.xml
take-out-pojo/src/main/java/com/sky/takeout/pojo/dto/package-info.java
take-out-pojo/src/main/java/com/sky/takeout/pojo/entity/package-info.java
take-out-pojo/src/main/java/com/sky/takeout/pojo/vo/package-info.java
take-out-pojo/pom.xml
take-out-system/src/main/java/com/sky/takeout/system/mapper/package-info.java
take-out-system/src/main/java/com/sky/takeout/system/service/package-info.java
take-out-system/pom.xml
take-out-framework/src/main/java/com/sky/takeout/framework/config/WebMvcConfig.java
take-out-framework/src/main/java/com/sky/takeout/framework/web/GlobalExceptionHandler.java
take-out-framework/pom.xml
take-out-admin/src/main/java/com/sky/takeout/admin/controller/DemoController.java
take-out-admin/src/main/java/com/sky/takeout/admin/TakeOutAdminApplication.java
take-out-admin/src/main/resources/application.yml
take-out-admin/src/test/java/com/sky/takeout/admin/controller/DemoControllerTest.java
take-out-admin/src/test/java/com/sky/takeout/admin/TakeOutAdminApplicationTests.java
take-out-admin/target/classes/application.yml
take-out-admin/pom.xml

## Root pom.xml
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

## take-out-common/src/main/java/com/sky/takeout/common/exception/BusinessException.java
```
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

## take-out-common/src/main/java/com/sky/takeout/common/result/ErrorCode.java
```
package com.sky.takeout.common.result;

public final class ErrorCode {
    public static final Integer SUCCESS = 1;
    public static final Integer ERROR = 0;

    private ErrorCode() {
    }
}
```

## take-out-common/src/main/java/com/sky/takeout/common/result/Result.java
```
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

## take-out-common/src/test/java/com/sky/takeout/common/result/ResultTest.java
```
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

## take-out-pojo/src/main/java/com/sky/takeout/pojo/dto/package-info.java
```
package com.sky.takeout.pojo.dto;
```

## take-out-pojo/src/main/java/com/sky/takeout/pojo/entity/package-info.java
```
package com.sky.takeout.pojo.entity;
```

## take-out-pojo/src/main/java/com/sky/takeout/pojo/vo/package-info.java
```
package com.sky.takeout.pojo.vo;
```

## take-out-system/src/main/java/com/sky/takeout/system/mapper/package-info.java
```
package com.sky.takeout.system.mapper;
```

## take-out-system/src/main/java/com/sky/takeout/system/service/package-info.java
```
package com.sky.takeout.system.service;
```

## take-out-framework/src/main/java/com/sky/takeout/framework/config/WebMvcConfig.java
```
package com.sky.takeout.framework.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {
}
```

## take-out-framework/src/main/java/com/sky/takeout/framework/web/GlobalExceptionHandler.java
```
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

## take-out-admin/src/main/resources/application.yml
```
spring:
  application:
    name: take-out-admin
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

## take-out-admin/target/classes/application.yml
```
spring:
  application:
    name: take-out-admin
```

