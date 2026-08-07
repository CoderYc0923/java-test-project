package com.sky.takeout.pojo.vo.order;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import com.sky.takeout.pojo.enums.OrderStatus;
import com.sky.takeout.pojo.enums.PayStatus;

import lombok.Data;

@Data
public final class OrderVO {

    private Long id;
    private String number;
    private OrderStatus status;
    private Long userId;
    private Long addressBookId;
    private LocalDateTime orderTime;
    private LocalDateTime checkoutTime;
    private Integer payMethod;
    private PayStatus payStatus;
    private BigDecimal amount;
    private String remark;
    private String phone;
    private String address;
    private String userName;
    private String consignee;
    private String cancelReason;
    private String rejectionReason;
    private LocalDateTime cancelTime;
    private LocalDateTime estimatedDeliveryTime;
    private Integer deliveryStatus;
    private LocalDateTime deliveryTime;
    private Integer packAmount;
    private Integer tablewareNumber;
    private Integer tablewareStatus;

    /** 列表菜品摘要，如：老坛酸菜鱼*1;米饭*1 */
    private String orderDishes;

    /** 详情明细；分页列表可为 null */
    private List<OrderDetailVO> orderDetailList;
}
