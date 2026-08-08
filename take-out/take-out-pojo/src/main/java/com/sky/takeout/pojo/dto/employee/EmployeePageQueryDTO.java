package com.sky.takeout.pojo.dto.employee;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import lombok.Data;

/**
 * 员工分页查询DTO
 * @author Cyrus
 * @since 2026-08-06
 */
@Data
@Schema(description = "员工分页查询")
public class EmployeePageQueryDTO {

    @Min(value = 1, message = "页码至少为1")
    @Schema(description = "页码", example = "1")
    private Integer page = 1;

    @Min(value = 1, message = "每页条数至少为1")
    @Schema(description = "每页条数", example = "10")
    private Integer pageSize = 10;

    @Schema(description = "姓名（模糊查询）", example = "张三")
    private String name;
}
