package com.sky.takeout.pojo.entity;

import java.math.BigDecimal;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

/**
 * 套餐菜品关系
 */
@Data
@TableName("setmeal_dish")
public class SetmealDish {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long setmealId;
    private Long dishId;
    /** 菜品名称（冗余） */
    private String name;
    /** 菜品单价（冗余） */
    private BigDecimal price;
    /** 份数 */
    private Integer copies;
}
