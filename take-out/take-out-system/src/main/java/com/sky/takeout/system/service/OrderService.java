package com.sky.takeout.system.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.sky.takeout.pojo.dto.order.OrderConfirmDTO;
import com.sky.takeout.pojo.dto.order.OrderQueryDTO;
import com.sky.takeout.pojo.dto.order.OrderRejectionDTO;
import com.sky.takeout.pojo.dto.order.OrderCancelDTO;
import com.sky.takeout.pojo.dto.order.OrderMockDTO;
import com.sky.takeout.pojo.vo.order.OrderMockVO;
import com.sky.takeout.pojo.vo.order.OrderStatisticsVO;
import com.sky.takeout.pojo.vo.order.OrderVO;

public interface OrderService {
    IPage<OrderVO> page(OrderQueryDTO queryDTO);

    OrderStatisticsVO statistics();

    OrderVO getById(Long id);

    void confirm(OrderConfirmDTO confirmDTO);

    void rejection(OrderRejectionDTO rejectionDTO);

    void delivery(Long id);

    void complete(Long id);

    void cancel(OrderCancelDTO cancelDTO);

    OrderMockVO mock(OrderMockDTO mockDTO);

    OrderMockVO mockPay(Long id);
}
