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
