package com.sky.takeout.pojo.entity;

import java.math.BigDecimal;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

@Data
@TableName("order_detail")
public class OrderDetail {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long orderId;
    private Long dishId;
    private Long setmealId;
    private String name;
    private String image;
    private String dishFlavor;
    private Integer number;
    private BigDecimal amount;
}
