package com.sky.takeout.system.service.impl;

import java.math.BigDecimal;
import java.time.DateTimeException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.sky.takeout.common.exception.BusinessException;
import com.sky.takeout.common.result.ErrorCode;
import com.sky.takeout.pay.config.PayProperties;
import com.sky.takeout.pay.gateway.MockPaymentGateway;
import com.sky.takeout.pay.port.PayAttemptPort;
import com.sky.takeout.pay.redis.RedisIdempotentHelper;
import com.sky.takeout.pojo.dto.mq.OrderStatusMessage;
import com.sky.takeout.pojo.dto.order.MockPayNotifyDTO;
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
import com.sky.takeout.system.mq.producers.OrderStatusProducer;
import com.sky.takeout.system.service.OrderService;

import lombok.Data;

import com.sky.takeout.pojo.entity.Dish;
import com.sky.takeout.pojo.entity.Order;
import com.sky.takeout.pojo.entity.OrderDetail;
import com.sky.takeout.pojo.entity.PayAttempt;
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
    private final MockPaymentGateway mockPaymentGateway;
    private final RedisIdempotentHelper redisIdempotentHelper;
    private final PayProperties payProperties;
    private final PayAttemptPort payAttemptPort;
    private final OrderStatusProducer orderStatusProducer;

    /** 防连点下单：order:idempotent:{requestId} */
    private static final String IDEMPOTENT_KEY_PREFIX = "order:idempotent:";
    /** 占坑中：同 requestId 正在建单 */
    private static final String PROCESSING = "PROCESSING";

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final Logger log = LoggerFactory.getLogger(OrderServiceImpl.class);

    private static final MockUser MOCK_USER = new MockUser();

    public OrderServiceImpl(OrderMapper orderMapper, OrderDetailMapper orderDetailMapper, DishMapper dishMapper,
            SetmealMapper setmealMapper, MockPaymentGateway mockPaymentGateway,
            RedisIdempotentHelper redisIdempotentHelper, PayProperties payProperties, PayAttemptPort payAttemptPort,
            OrderStatusProducer orderStatusProducer) {
        this.orderMapper = orderMapper;
        this.orderDetailMapper = orderDetailMapper;
        this.dishMapper = dishMapper;
        this.setmealMapper = setmealMapper;
        this.mockPaymentGateway = mockPaymentGateway;
        this.redisIdempotentHelper = redisIdempotentHelper;
        this.payProperties = payProperties;
        this.payAttemptPort = payAttemptPort;
        this.orderStatusProducer = orderStatusProducer;
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
        Long toBeConfirmed = orderMapper
                .selectCount(new LambdaQueryWrapper<Order>().eq(Order::getStatus, OrderStatus.TO_BE_CONFIRMED));
        // status = 3 已接单
        Long confirmed = orderMapper
                .selectCount(new LambdaQueryWrapper<Order>().eq(Order::getStatus, OrderStatus.CONFIRMED));
        // status = 4 派送中
        Long deliveryInProgress = orderMapper
                .selectCount(new LambdaQueryWrapper<Order>().eq(Order::getStatus, OrderStatus.DELIVERY_IN_PROGRESS));

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

        transitionAndPublish(confirmDTO.getId(), OrderStatus.TO_BE_CONFIRMED, OrderStatus.CONFIRMED, null,
                "只有待接单订单才能接单");

    }

    /**
     * 拒单：只有待接单状态的订单才能拒单；原因非空由 DTO @NotBlank 保证
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void rejection(OrderRejectionDTO rejectionDTO) {

        transitionAndPublish(rejectionDTO.getId(), OrderStatus.TO_BE_CONFIRMED, OrderStatus.CANCELLED, o -> {
            o.setRejectionReason(rejectionDTO.getRejectionReason().trim());
            o.setCancelTime(LocalDateTime.now());
        },
                "只有待接单订单才能拒单");
    }

    /**
     * 派送：只有已接单状态的订单才能派送
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delivery(Long id) {

        transitionAndPublish(id, OrderStatus.CONFIRMED, OrderStatus.DELIVERY_IN_PROGRESS, null,
                "只有待派送订单才能派送");
    }

    /**
     * 完成：只有派送中状态的订单才能完成
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void complete(Long id) {

        transitionAndPublish(id, OrderStatus.DELIVERY_IN_PROGRESS, OrderStatus.COMPLETED, o -> {
            o.setDeliveryTime(LocalDateTime.now());
        },
                "只有派送中订单才能完成");

    }

    /**
     * 取消：只有待接单、已接单、派送中状态的订单才能取消；原因非空由 DTO @NotBlank 保证
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancel(OrderCancelDTO cancelDTO) {
        List<OrderStatus> cancelableStatuses = List.of(OrderStatus.TO_BE_CONFIRMED, OrderStatus.CONFIRMED,
                OrderStatus.DELIVERY_IN_PROGRESS);


        transitionAndPublish(cancelDTO.getId(), cancelableStatuses, OrderStatus.COMPLETED, o -> {
            o.setCancelReason(cancelDTO.getCancelReason().trim());
            o.setCancelTime(LocalDateTime.now());
        },
                "当前订单状态不允许取消");
    }

    /**
     * 模拟下单（强制requestId+ redis幂等）
     * trySetNx(PROCESSING) -> doMockCreate -> set(orderId)
     * 失败delete，允许同requestId重试
     * 重复请求：PROCESSING=处理中；已是orderId=返回原单
     * 
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public OrderMockVO mock(OrderMockDTO mockDTO) {
        // 强制幂等键：没有就不建单
        if (!StringUtils.hasText(mockDTO.getRequestId())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "requestId不能为空");
        }

        // 幂等键
        String requestId = mockDTO.getRequestId();
        // 拼接幂等键
        String key = IDEMPOTENT_KEY_PREFIX + requestId;

        // 幂等键过期时间
        Long ttl = payProperties.getOrderIdempotentTtlSeconds();
        if (ttl == null || ttl <= 0) {
            ttl = 300L;
        }

        // 判断是否是该订单第一次请求
        boolean first = redisIdempotentHelper.trySetNx(key, PROCESSING, ttl);
        // 若不是第一次请求
        if (!first) {
            // 拿到缓存中的订单id
            String cached = redisIdempotentHelper.get(key);
            // 若缓存中的订单id为PROCESSING，则说明该订单正在处理中，抛出异常
            if (PROCESSING.equals(cached)) {
                throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS, "下单处理中，请勿重复下单");
            }
            // 若缓存中的订单id不为空，则说明该订单已存在，返回原单
            if (StringUtils.hasText(cached)) {
                Order order = orderMapper.selectById(Long.valueOf(cached));

                if (order != null) {
                    log.info("订单{}已存在，返回原单", order.getId());

                    return toMockVO(order);
                }
            }
            // 若缓存中的订单id为空，则说明该订单不存在，抛出异常
            throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS, "下单处理中，请勿重复下单");
        }

        // 若第一次请求，则创建订单
        try {
            // 创建订单
            OrderMockVO vo = doMockCreate(mockDTO);
            // 将订单id缓存到redis中
            redisIdempotentHelper.set(key, String.valueOf(vo.getId()), ttl);
            return vo;
        } catch (RuntimeException e) {
            redisIdempotentHelper.delete(key);
            throw e;
        }
    }

    /**
     * 模拟支付
     * 调用支付网关进行支付；返回带 outTradeNo / checkoutUrl，供前端打开确认页
     * 
     * @param id 订单id
     */
    @Override
    public OrderMockVO mockPay(Long id) {
        Order order = mockPaymentGateway.requestPay(id);
        PayAttempt paying = payAttemptPort.findPayingByOrderId(id);
        return toMockVO(order, paying);
    }

    @Override
    public OrderMockVO mockPayNotify(MockPayNotifyDTO dto) {
        return toMockVO(mockPaymentGateway.handlePayNotify(dto), null);
    }

    private OrderMockVO doMockCreate(OrderMockDTO mockDTO) {
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
                orderDetail
                        .setDishFlavor(StringUtils.hasText(item.getDishFlavor()) ? item.getDishFlavor().trim() : null);

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
        for (OrderDetail orderDetail : orderDetails) {
            orderDetail.setOrderId(order.getId());
            orderDetailMapper.insert(orderDetail);
        }

        log.info("订单{}下单成功", order.getId());
        return toMockVO(order);
    }

    /**
     * 生成订单号
     * 格式：ORDyyyyMMddHHmmssXXXX
     * 
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
        return toMockVO(order, null);
    }

    /**
     * 组装模拟下单/支付 VO。
     * {@code paying} 非空时写入渠道单号与假微信确认页 URL（requestPay 后）。
     */
    private OrderMockVO toMockVO(Order order, PayAttempt paying) {
        OrderMockVO vo = new OrderMockVO();
        BeanUtils.copyProperties(order, vo);

        if (paying != null && StringUtils.hasText(paying.getOutTradeNo())) {
            String outTradeNo = paying.getOutTradeNo();
            vo.setOutTradeNo(outTradeNo);
            String base = trimTrailingSlash(payProperties.getMockWechatBaseUrl());
            vo.setCheckoutUrl(base + "/mock/pay/checkout?out_trade_no=" + urlEncode(outTradeNo));
        }
        return vo;
    }

    private static String trimTrailingSlash(String url) {
        if (!StringUtils.hasText(url)) {
            return "http://127.0.0.1:9090";
        }
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    private static String urlEncode(String value) {
        return java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8);
    }

    /** id 非空由 DTO @NotNull 或路径参数保证；这里只查库做业务校验 */
    private Order getOrder(Long id) {
        Order order = orderMapper.selectById(id);
        if (order == null) {
            throw new BusinessException(ErrorCode.CONFLICT, "订单不存在");
        }
        return order;
    }

    /**
     * 状态变迁并发布
     * 
     * @param orderId     订单id
     * @param fromStatus  当前状态
     * @param toStatus    目标状态
     * @param consumer    消费者
     * @param conflictMsg 冲突消息
     */
    private void transitionAndPublish(Long orderId, OrderStatus fromStatus, OrderStatus toStatus,
            Consumer<Order> consumer, String conflictMsg) {
        transitionAndPublish(orderId, List.of(fromStatus), toStatus, consumer, conflictMsg);
    }

    public void transitionAndPublish(Long orderId, List<OrderStatus> fromStatuses, OrderStatus toStatus,
            Consumer<Order> consumer, String conflictMsg) {
        // 获取订单快照
        Order snapshot = getOrder(orderId);

        // 创建补丁实体
        Order patchEntity = new Order();
        patchEntity.setStatus(toStatus);
        if (consumer != null) {
            // 执行消费者，填充补丁实体
            consumer.accept(patchEntity);
        }

        int rows = orderMapper.update(patchEntity, new LambdaUpdateWrapper<Order>()
                .eq(Order::getId, orderId)
                .in(Order::getStatus, fromStatuses));

        if (rows != 1) {
            throw new BusinessException(ErrorCode.CONFLICT, conflictMsg);
        }

        String eventId = UUID.randomUUID().toString();
        Long oid = orderId;
        String number = snapshot.getNumber();
        // 消息里的 from：用库里真实旧状态（取消等多 from 时更准）
        OrderStatus actualFrom = snapshot.getStatus();

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                OrderStatusMessage msg = OrderStatusMessage.builder()
                        .eventId(eventId)
                        .orderId(oid)
                        .orderNumber(number)
                        .fromStatus(actualFrom)
                        .toStatus(toStatus)
                        .occurredAt(LocalDateTime.now().toString())
                        .build();

                orderStatusProducer.send(msg);
            }
        });
    }

    /**
     * 批量查询明细，并按 orderId 分组
     * 
     * @param orderIds
     * @return
     */
    private Map<Long, List<OrderDetail>> getDetailMap(List<Long> orderIds) {
        if (orderIds == null || orderIds.isEmpty()) {
            return null;
        }

        List<OrderDetail> allDetails = orderDetailMapper.selectList(
                new LambdaQueryWrapper<OrderDetail>().in(OrderDetail::getOrderId, orderIds));

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
