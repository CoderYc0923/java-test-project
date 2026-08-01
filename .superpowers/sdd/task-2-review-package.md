# Review package Task 2
## Stat
A take-out-common/src/main/java/com/sky/takeout/common/result/ErrorCode.java
A take-out-common/src/main/java/com/sky/takeout/common/result/Result.java
A take-out-common/src/main/java/com/sky/takeout/common/exception/BusinessException.java
A take-out-common/src/test/java/com/sky/takeout/common/result/ResultTest.java

## File: take-out-common/src/main/java/com/sky/takeout/common/result/ErrorCode.java
```java
package com.sky.takeout.common.result;

public final class ErrorCode {
    public static final Integer SUCCESS = 1;
    public static final Integer ERROR = 0;

    private ErrorCode() {
    }
}
```

## File: take-out-common/src/main/java/com/sky/takeout/common/result/Result.java
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

## File: take-out-common/src/main/java/com/sky/takeout/common/exception/BusinessException.java
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

## File: take-out-common/src/test/java/com/sky/takeout/common/result/ResultTest.java
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

