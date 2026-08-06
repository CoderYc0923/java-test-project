package com.sky.takeout.pojo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

@Data
@TableName("dish_flavor")
public class DishFlavor {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long dishId;
    private String name;
    private String value;
}
