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
