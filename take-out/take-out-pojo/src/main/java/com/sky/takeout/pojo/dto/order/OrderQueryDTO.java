package com.sky.takeout.pojo.dto.order;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import lombok.Data;

@Data
public final class OrderQueryDTO {

    @Min(value = 1, message = "页码至少为1")
    @Schema(description = "页码", example = "1")
    private Integer page = 1;

    @Min(value = 1, message = "每页条数至少为1")
    @Schema(description = "每页条数", example = "10")
    private Integer pageSize = 10;

    @Schema(description = "订单号（模糊查询）", example = "1234567890")
    private String number;

    @Schema(description = "手机号（模糊查询）", example = "13800138000")
    private String phone;

    @Schema(description = "订单状态：1 待付款，2 已支付，3 已退款", example = "1")
    private Integer status;

    @Schema(description = "开始时间", example = "2021-01-01 00:00:00")
    private String beginTime;

    @Schema(description = "结束时间", example = "2021-01-01 23:59:59")
    private String endTime;
}
