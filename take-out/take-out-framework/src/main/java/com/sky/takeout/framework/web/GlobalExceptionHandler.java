package com.sky.takeout.framework.web;

import com.sky.takeout.common.exception.BusinessException;
import com.sky.takeout.common.result.ErrorCode;
import com.sky.takeout.common.result.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> handleMethodArgumentNotValidException(MethodArgumentNotValidException ex) {
        // BindingResult 里装着所有字段错误；一般取第一条给前端即可
        FieldError fieldError = ex.getBindingResult().getFieldError();
        // fieldError.getDefaultMessage() 就是 DTO 注解里写的 message = "手机号格式不正确" 那种文案
        String message = fieldError != null ? fieldError.getDefaultMessage() : "参数校验失败";
        return Result.error(ErrorCode.BAD_REQUEST, message);
    }

    @ExceptionHandler(BusinessException.class)
    public Result<Void> handleBusinessException(BusinessException ex) {
        return Result.error(ex.getCode(), ex.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(Exception ex) {
        log.error("未处理异常", ex);
        return Result.error("系统异常，请稍后重试");
    }
}
