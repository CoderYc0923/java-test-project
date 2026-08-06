package com.sky.takeout.pojo.dto.employee;

import lombok.Data;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;
import com.sky.takeout.common.constant.RegexConstant;
import com.sky.takeout.pojo.enums.Sex;

/**
 * 更新员工DTO
 * @param id id
 * @param name 姓名
 * @param phone 手机号
 * @param sex 性别
 * @param idNumber 身份证号
 * @author Cyrus
 * @since 2026-08-06
 */
@Data
public class EmployeeUpdateDTO {

    @NotNull(message = "id不能为空")
    @Schema(description = "id", example = "1")
    private Long id;

    @NotBlank(message = "姓名不能为空")
    @Size(min = 2, max = 32, message = "姓名长度必须在1到32之间")
    @Schema(description = "姓名", example = "张三")
    private String name;

    @NotBlank(message = "手机号不能为空")
    @Pattern(regexp = RegexConstant.PHONE, message = "手机号格式不正确")
    @Schema(description = "手机号", example = "13800138000")
    private String phone;

    @NotNull(message = "性别不能为空")
    @Schema(description = "性别", example = "1")
    private Sex sex;

    @Pattern(regexp = RegexConstant.ID_NUMBER, message = "身份证号格式不正确")
    @Schema(description = "身份证号", example = "13800138000")
    private String idNumber;
}
