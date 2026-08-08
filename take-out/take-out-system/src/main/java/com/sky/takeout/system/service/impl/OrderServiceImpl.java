package com.sky.takeout.system.service.impl;

import java.math.BigDecimal;
import java.time.DateTimeException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.sky.takeout.common.exception.BusinessException;
import com.sky.takeout.common.result.ErrorCode;
import com.sky.takeout.pojo.dto.order.OrderCancelDTO;
import com.sky.takeout.pojo.dto.order.OrderConfirmDTO;
import com.sky.takeout.pojo.dto.order.OrderMockDTO;
import com.sky.takeout.pojo.dto.order.OrderMockItemDTO;
import com.sky.takeout.pojo.dto.order.OrderRejectionDTO;
import com.sky.takeout.pojo.dto.order.OrderQueryDTO;
import com.sky.takeout.system.mapper.DishMapper;
import com.sky.takeout.system.mapper.OrderDetailMapper;
import com.sky.takeout.system.mapper.OrderMapper;
import com.sky.takeout.system.mapper.SetmealMapper;
import com.sky.takeout.system.service.OrderService;

import lombok.Data;

import com.sky.takeout.pojo.entity.Dish;
import com.sky.takeout.pojo.entity.Order;
import com.sky.takeout.pojo.entity.OrderDetail;
import com.sky.takeout.pojo.entity.Setmeal;
import com.sky.takeout.pojo.enums.OrderStatus;
import com.sky.takeout.pojo.enums.PayStatus;
import com.sky.takeout.pojo.enums.SaleStatus;
import com.sky.takeout.pojo.vo.order.OrderDetailVO;
import com.sky.takeout.pojo.vo.order.OrderMockVO;
import com.sky.takeout.pojo.vo.order.OrderStatisticsVO;
import com.sky.takeout.pojo.vo.order.OrderVO;

@Data
class MockUser {
    private Long id = 1001L;
    private Long addressBookId = 1L;
    private String name = "张三";
    private String phone = "13800138000";
    private String address = "北京市海淀区";
    private String consignee = "张三";
    private String consigneePhone = "13800138000";
    private String consigneeAddress = "北京市海淀区";
    private String consigneeEmail = "zhangsan@example.com";
    private String consigneePassword = "123456";
}

@Service
public class OrderServiceImpl implements OrderService {

    private final OrderMapper orderMapper;
    private final OrderDetailMapper orderDetailMapper;
    private final DishMapper dishMapper;
    private final SetmealMapper setmealMapper;

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final Logger log = LoggerFactory.getLogger(OrderServiceImpl.class);

    private static final MockUser MOCK_USER = new MockUser();

    public OrderServiceImpl(OrderMapper orderMapper, OrderDetailMapper orderDetailMapper, DishMapper dishMapper, SetmealMapper setmealMapper) {
        this.orderMapper = orderMapper;
        this.orderDetailMapper = orderDetailMapper;
        this.dishMapper = dishMapper;
        this.setmealMapper = setmealMapper;
    }

    @Override
    public IPage<OrderVO> page(OrderQueryDTO queryDTO) {
        // page/pageSize 由 QueryDTO 默认值 + @Min(1) 保证；这里只防 null
        Integer pageNum = queryDTO.getPage() == null ? 1 : queryDTO.getPage();
        Integer pageSize = queryDTO.getPageSize() == null ? 10 : queryDTO.getPageSize();
        Page<Order> page = new Page<>(pageNum, pageSize);

        LambdaQueryWrapper<Order> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.like(StringUtils.hasText(queryDTO.getNumber()), Order::getNumber, queryDTO.getNumber());
        queryWrapper.like(StringUtils.hasText(queryDTO.getPhone()), Order::getPhone, queryDTO.getPhone());

        LocalDateTime beginTime = parseDateTime(queryDTO.getBeginTime());
        LocalDateTime endTime = parseDateTime(queryDTO.getEndTime());
        queryWrapper.ge(beginTime != null, Order::getOrderTime, beginTime); // 大于等于开始时间
        queryWrapper.le(endTime != null, Order::getOrderTime, endTime); // 小于等于结束时间

        // statusCode 空或0，不查询订单状态
        Integer statusCode = queryDTO.getStatus();
        if (statusCode != null && statusCode != 0) {
            queryWrapper.eq(Order::getStatus, OrderStatus.fromCode(statusCode));
        }

        queryWrapper.orderByDesc(Order::getOrderTime); // 按订单时间降序

        // 查看订单头
        IPage<Order> orderPage = orderMapper.selectPage(page, queryWrapper);
        List<Order> records = orderPage.getRecords();
        if (records.isEmpty()) {
            return orderPage.convert(order -> toVO(order, null, false));
        }

        // 批量查询明细
        List<Long> orderIds = records.stream().map(Order::getId).collect(Collectors.toList());
        Map<Long, List<OrderDetail>> detailMap = getDetailMap(orderIds);

        // 转换为 VO
        return orderPage.convert(order -> toVO(order, detailMap.get(order.getId()), false));

    }


    @Override
    public OrderVO getById(Long id) {
        Order order = orderMapper.selectById(id);
        if (order == null) {
            throw new BusinessException(ErrorCode.CONFLICT, "订单不存在");
        }

        // 查询明细
        Map<Long, List<OrderDetail>> detailMap = getDetailMap(List.of(order.getId()));

        return toVO(order, detailMap.get(order.getId()), true);
    }

    /**
     * 订单状态数量统计
     */
    @Override
    public OrderStatisticsVO statistics() {
        // status = 2 待接单
        Long toBeConfirmed = orderMapper.selectCount(new LambdaQueryWrapper<Order>().eq(Order::getStatus, OrderStatus.TO_BE_CONFIRMED));
        // status = 3 已接单
        Long confirmed = orderMapper.selectCount(new LambdaQueryWrapper<Order>().eq(Order::getStatus, OrderStatus.CONFIRMED));
        // status = 4 派送中
        Long deliveryInProgress = orderMapper.selectCount(new LambdaQueryWrapper<Order>().eq(Order::getStatus, OrderStatus.DELIVERY_IN_PROGRESS));

        OrderStatisticsVO vo = new OrderStatisticsVO();
        vo.setToBeConfirmed(toBeConfirmed == null ? 0 : toBeConfirmed.intValue());
        vo.setConfirmed(confirmed == null ? 0 : confirmed.intValue());
        vo.setDeliveryInProgress(deliveryInProgress == null ? 0 : deliveryInProgress.intValue());
        return vo;
    }


    /**
     * 接单：只有待接单状态的订单才能接单
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void confirm(OrderConfirmDTO confirmDTO) {
        Order order = getOrder(confirmDTO.getId());

        // 只允许待接单状态的订单被接单
        assertOrderStatus(order, OrderStatus.TO_BE_CONFIRMED, "只有待接单订单才能接单");

        // 更新订单状态为已接单
        order.setStatus(OrderStatus.CONFIRMED);
        orderMapper.updateById(order);
        log.info("订单{}状态改为已接单", order.getId());
    }

    /**
     * 拒单：只有待接单状态的订单才能拒单；原因非空由 DTO @NotBlank 保证
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void rejection(OrderRejectionDTO rejectionDTO) {
        Order order = getOrder(rejectionDTO.getId());

        // 只允许待接单状态的订单被拒单；原因非空由 DTO @NotBlank 保证
        assertOrderStatus(order, OrderStatus.TO_BE_CONFIRMED, "只有待接单订单才能拒单");

        order.setStatus(OrderStatus.CANCELLED);
        order.setRejectionReason(rejectionDTO.getRejectionReason().trim());
        order.setCancelTime(LocalDateTime.now());
        orderMapper.updateById(order);
        log.info("订单{}已拒单", order.getId());
    }

    /**
     * 派送：只有已接单状态的订单才能派送
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delivery(Long id) {
        Order order = getOrder(id);

        // 只允许已接单状态的订单被派送
        assertOrderStatus(order, OrderStatus.CONFIRMED, "只有待派送订单才能派送");

        // 更新订单状态为派送中
        order.setStatus(OrderStatus.DELIVERY_IN_PROGRESS);
        orderMapper.updateById(order);
        log.info("订单{}状态改为派送中", order.getId());
    }

    /**
     * 完成：只有派送中状态的订单才能完成
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void complete(Long id) {
        Order order = getOrder(id);

        // 只允许派送中状态的订单被完成
        assertOrderStatus(order, OrderStatus.DELIVERY_IN_PROGRESS, "只有派送中订单才能完成");

        // 更新订单状态为已完成
        order.setStatus(OrderStatus.COMPLETED);
        order.setDeliveryTime(LocalDateTime.now());
        orderMapper.updateById(order);
        log.info("订单{}状态改为已完成", order.getId());

    }

    /**
     * 取消：只有待接单、已接单、派送中状态的订单才能取消；原因非空由 DTO @NotBlank 保证
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancel(OrderCancelDTO cancelDTO) {
        Order order = getOrder(cancelDTO.getId());
        OrderStatus currentStatus = order.getStatus();
        List<OrderStatus> cancelableStatuses = List.of(OrderStatus.TO_BE_CONFIRMED, OrderStatus.CONFIRMED, OrderStatus.DELIVERY_IN_PROGRESS);

        if (!cancelableStatuses.contains(currentStatus)) {
            throw new BusinessException(ErrorCode.CONFLICT, "当前订单状态不允许取消：" + (currentStatus == null ? "null" : currentStatus.getMessage()));
        }

        order.setStatus(OrderStatus.CANCELLED);
        order.setCancelReason(cancelDTO.getCancelReason().trim());
        order.setCancelTime(LocalDateTime.now());
        orderMapper.updateById(order);
        log.info("订单{}状态改为已取消", order.getId());

    }

    /**
     * 模拟下单
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public OrderMockVO mock(OrderMockDTO mockDTO) {
        List<OrderDetail> orderDetails = new ArrayList<>();
        BigDecimal totalAmount = BigDecimal.ZERO;

        
        for (OrderMockItemDTO item : mockDTO.getItems()) {
            boolean isDish = item.getDishId() != null;
            boolean isSetmeal = item.getSetmealId() != null;

            if (isDish == isSetmeal) {
                throw new BusinessException(ErrorCode.CONFLICT, "菜品和套餐只能选择一个");
            }

            OrderDetail orderDetail = new OrderDetail();
            orderDetail.setNumber(item.getNumber());

            if (isDish) {
                Dish dish = dishMapper.selectById(item.getDishId());
                if (dish == null) {
                    throw new BusinessException(ErrorCode.CONFLICT, "菜品不存在");
                }

                if (dish.getStatus() != SaleStatus.ENABLE) {
                    throw new BusinessException(ErrorCode.CONFLICT, "菜品已下架");
                }

                // 快照存
                orderDetail.setDishId(dish.getId());
                orderDetail.setName(dish.getName());
                orderDetail.setImage(dish.getImage());
                orderDetail.setAmount(dish.getPrice());
                orderDetail.setDishFlavor(StringUtils.hasText(item.getDishFlavor()) ? item.getDishFlavor().trim() : null);

            } else if (isSetmeal) {
                Setmeal setmeal = setmealMapper.selectById(item.getSetmealId());
                if (setmeal == null) {
                    throw new BusinessException(ErrorCode.CONFLICT, "套餐不存在");
                }

                if (setmeal.getStatus() != SaleStatus.ENABLE) {
                    throw new BusinessException(ErrorCode.CONFLICT, "套餐已下架");
                }

                // 快照存
                orderDetail.setSetmealId(setmeal.getId());
                orderDetail.setName(setmeal.getName());
                orderDetail.setImage(setmeal.getImage());
                orderDetail.setAmount(setmeal.getPrice());
            }

            // 计算单品金额,multiply方法是BigDecimal的静态方法,用于计算两个BigDecimal的乘积
            BigDecimal mount = orderDetail.getAmount().multiply(BigDecimal.valueOf(orderDetail.getNumber()));
            totalAmount = totalAmount.add(mount);
            orderDetails.add(orderDetail);
        }

        // 打包费
        int packAmount = 0;
        BigDecimal orderAmount = totalAmount.add(BigDecimal.valueOf(packAmount));
        LocalDateTime now = LocalDateTime.now();

        Order order = new Order();
        order.setNumber(generateOrderNumber());
        order.setStatus(OrderStatus.PENDING_PAYMENT);
        order.setPayStatus(PayStatus.UNPAID);
        order.setCheckoutTime(null); // 未支付时 checkoutTime 为 null

        order.setUserId(MOCK_USER.getId());
        order.setAddressBookId(MOCK_USER.getAddressBookId());
        order.setPhone(MOCK_USER.getPhone());
        order.setAddress(MOCK_USER.getAddress());
        order.setUserName(MOCK_USER.getName());
        order.setConsignee(MOCK_USER.getConsignee());

        order.setOrderTime(now);
        order.setPayMethod(1);
        order.setAmount(orderAmount);
        order.setRemark(mockDTO.getRemark());
        order.setEstimatedDeliveryTime(now.plusMinutes(45));
        order.setDeliveryStatus(1);
        order.setPackAmount(packAmount);
        order.setTablewareNumber(1);
        order.setTablewareStatus(1);

        // 下单
        orderMapper.insert(order);

        // 批量插入明细
        for(OrderDetail orderDetail : orderDetails) {
            orderDetail.setOrderId(order.getId());
            orderDetailMapper.insert(orderDetail);
        }

        log.info("订单{}下单成功", order.getId());
        return toMockVO(order);

    }


    /**
     * 模拟支付
     * 真项目里这里会：验签、核对金额、防重复通知(幂等)。
     * 我们用状态校验代替：只有「待付款 + 未支付」才能付
     */
    @Override
    public OrderMockVO mockPay(Long id) {

    }

    /**
     * 生成订单号
     * 格式：ORDyyyyMMddHHmmssXXXX
     * @return
     */
    private String generateOrderNumber() {
        // 时间戳
        String time = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        // 随机数
        int rnd = ThreadLocalRandom.current().nextInt(1000, 10000);

        return "ORD" + time + rnd;
    }

    private OrderMockVO toMockVO(Order order) {
        OrderMockVO vo = new OrderMockVO();

        BeanUtils.copyProperties(order, vo);
        return vo;
    }

    /** id 非空由 DTO @NotNull 或路径参数保证；这里只查库做业务校验 */
    private Order getOrder(Long id) {
        Order order = orderMapper.selectById(id);
        if (order == null) {
            throw new BusinessException(ErrorCode.CONFLICT, "订单不存在");
        }
        return order;
    }

    private void assertOrderStatus(Order order, OrderStatus expectedStatus, String message) {
        if (order.getStatus() != expectedStatus) {
            throw new BusinessException(ErrorCode.CONFLICT, message);
        }
    }


    /**
     * 批量查询明细，并按 orderId 分组
     * @param orderIds
     * @return
     */
    private Map<Long, List<OrderDetail>> getDetailMap(List<Long> orderIds) {
        if (orderIds == null || orderIds.isEmpty()) {
            return null;
        }

        List<OrderDetail> allDetails = orderDetailMapper.selectList(
            new LambdaQueryWrapper<OrderDetail>().in(OrderDetail::getOrderId, orderIds)
        );

        return allDetails.stream().collect(Collectors.groupingBy(OrderDetail::getOrderId));
    }

    /**
     * Entity → VO；details 用于拼 orderDishes（可为 null）。
     */
    private OrderVO toVO(Order order, List<OrderDetail> details, Boolean withDetails) {
        OrderVO vo = new OrderVO();
        BeanUtils.copyProperties(order, vo); // 复制属性
        vo.setOrderDishes(buildOrderDishes(details)); // 设置订单菜品摘要
        if (withDetails) {
            vo.setOrderDetailList(details.stream().map(d -> {
                OrderDetailVO detailVO = new OrderDetailVO();
                BeanUtils.copyProperties(d, detailVO);
                return detailVO;
            }).collect(Collectors.toList()));
        }
        return vo;
    }

    /**
     * 拼订单菜品摘要
     * 例如："宫保鸡丁*2, 鱼香肉丝*1"
     * 
     * @param details
     * @return
     */
    private String buildOrderDishes(List<OrderDetail> details) {
        if (details == null || details.isEmpty()) {
            return "";
        }
        return details.stream().map(d -> d.getName() + "*" + d.getNumber()).collect(Collectors.joining(";"));
    }

    /**
     * 前端传来 yyyy-MM-dd HH:mm:ss；空串返回 null。
     */
    private LocalDateTime parseDateTime(String text) {
        if (!StringUtils.hasText(text)) {
            return null;
        }
        try {
            return LocalDateTime.parse(text.trim(), DATE_TIME_FORMATTER);
        } catch (DateTimeException e) {
            throw new BusinessException(ErrorCode.ERROR, "时间格式错误，应为 yyyy-MM-dd HH:mm:ss");
        }
    }
}
