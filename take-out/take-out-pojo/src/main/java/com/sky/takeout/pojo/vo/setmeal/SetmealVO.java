package com.sky.takeout.pojo.vo.setmeal;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import com.sky.takeout.pojo.enums.SaleStatus;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public final class SetmealVO {

    private Long id;
    private String name;
    private Long categoryId;
    private String categoryName;
    private BigDecimal price;
    private String imageOssPath;
    private String imageUrl;
    private String description;
    private SaleStatus status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    /** 详情回显；分页列表可为空 */
    private List<SetmealDishVO> setmealDishes;
}
