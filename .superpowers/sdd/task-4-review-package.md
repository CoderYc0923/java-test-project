# Review package Task 4

## take-out-framework/src/main/java/com/sky/takeout/framework/web/GlobalExceptionHandler.java
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

## take-out-framework/src/main/java/com/sky/takeout/framework/config/WebMvcConfig.java
```java
package com.sky.takeout.framework.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {
}
```

