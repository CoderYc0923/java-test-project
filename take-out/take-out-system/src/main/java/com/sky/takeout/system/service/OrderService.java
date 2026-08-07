package com.sky.takeout.system.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.sky.takeout.pojo.dto.order.OrderQueryDTO;
import com.sky.takeout.pojo.vo.order.OrderStatisticsVO;
import com.sky.takeout.pojo.vo.order.OrderVO;

public interface OrderService {
    IPage<OrderVO> page(OrderQueryDTO queryDTO);

    OrderStatisticsVO statistics();

    OrderVO getById(Long id);
}
