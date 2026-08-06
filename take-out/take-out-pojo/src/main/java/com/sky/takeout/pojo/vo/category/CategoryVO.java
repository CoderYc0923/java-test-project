package com.sky.takeout.pojo.vo.category;

import lombok.Data;
import java.time.LocalDateTime;

import com.sky.takeout.pojo.enums.CategoryType;

@Data
public final class CategoryVO {

    private Long id;
    private String name;
    private CategoryType type;
    private Integer sort;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
