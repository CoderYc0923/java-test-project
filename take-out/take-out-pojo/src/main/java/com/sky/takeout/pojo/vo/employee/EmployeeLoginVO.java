package com.sky.takeout.pojo.vo.employee;

import lombok.Data;
import lombok.Builder;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "员工登录返回结果")
@Data
@Builder
public class EmployeeLoginVO {
    @Schema(description = "员工id", example = "1")
    private Long id;
    @Schema(description = "用户名", example = "admin")
    private String username;
    @Schema(description = "姓名", example = "张三")
    private String name;
    @Schema(description = "token", example = "123456")
    private String token;
}
