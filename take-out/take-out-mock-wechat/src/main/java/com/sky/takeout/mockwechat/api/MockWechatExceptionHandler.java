package com.sky.takeout.mockwechat.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.sky.takeout.mockwechat.api.dto.ErrorBody;

@RestControllerAdvice
public class MockWechatExceptionHandler {

    @ExceptionHandler(MockWechatException.class)
    public ResponseEntity<ErrorBody> handle(MockWechatException ex) {
        return ResponseEntity.status(ex.getStatus())
                .body(new ErrorBody(ex.getCode(), ex.getMessage()));
    }
}
