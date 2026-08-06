package com.sky.takeout.pojo.vo.dish;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import com.sky.takeout.pojo.enums.SaleStatus;

import lombok.Data;
import lombok.Builder;

@Data
@Builder
public final class DishVO {

    private Long id;
    private String name;
    private Long categoryId;
    private String categoryName;
    private BigDecimal price;
    private String image;
    private String description;
    private SaleStatus status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    /** 详情回显时使用；分页列表可为空 */
    private List<DishFlavorVO> flavors;
}
