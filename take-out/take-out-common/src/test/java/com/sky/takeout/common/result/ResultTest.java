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
