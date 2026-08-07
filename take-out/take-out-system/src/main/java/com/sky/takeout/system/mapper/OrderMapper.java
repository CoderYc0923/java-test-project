package com.sky.takeout.system.mapper;

import org.apache.ibatis.annotations.Mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sky.takeout.pojo.entity.Order;

@Mapper
public interface OrderMapper extends BaseMapper<Order> {

}
