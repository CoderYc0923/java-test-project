package com.sky.takeout.pojo.vo.order;

import java.math.BigDecimal;

import lombok.Data;

/**
 * 订单明细行（详情弹窗 orderDetailList）
 */
@Data
public final class OrderDetailVO {

    private Long id;
    private String name;
    private String image;
    private Long dishId;
    private Long setmealId;
    private String dishFlavor;
    private Integer number;
    private BigDecimal amount;
}
