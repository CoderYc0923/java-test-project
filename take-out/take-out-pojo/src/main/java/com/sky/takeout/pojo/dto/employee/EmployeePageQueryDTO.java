package com.sky.takeout.pojo.dto.employee;

import lombok.Data;
import io.swagger.v3.oas.annotations.media.Schema;

@Data
@Schema(description = "员工分页查询")
public class EmployeePageQueryDTO {

    @Schema(description = "页码", example = "1")
    private Integer page = 1;

    @Schema(description = "每页条数", example = "10")
    private Integer pageSize = 10;

    @Schema(description = "姓名（模糊查询）", example = "张三")
    private String name;
}
