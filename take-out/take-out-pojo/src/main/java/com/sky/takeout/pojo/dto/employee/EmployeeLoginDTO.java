package com.sky.takeout.pojo.dto.employee;

import lombok.Data;
import io.swagger.v3.oas.annotations.media.Schema;

@Data
public class EmployeeLoginDTO {
    @Schema(description = "用户名", example = "admin")
    private String username;
    @Schema(description = "密码", example = "123456")
    private String password;
}
