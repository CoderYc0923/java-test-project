package com.sky.takeout.pojo.dto.employee;

import lombok.Data;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 员工登录DTO
 * @param username 用户名
 * @param password 密码
 * @author Cyrus
 * @since 2026-08-06
 */
@Data
public class EmployeeLoginDTO {
    @Schema(description = "用户名", example = "admin")
    private String username;
    @Schema(description = "密码", example = "123456")
    private String password;
}
