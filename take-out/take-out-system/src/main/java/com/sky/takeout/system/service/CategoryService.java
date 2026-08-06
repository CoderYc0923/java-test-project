package com.sky.takeout.system.service;

import java.util.List;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.sky.takeout.pojo.entity.Category;
import com.sky.takeout.pojo.dto.category.CategoryQueryDTO;
import com.sky.takeout.pojo.dto.category.CategorySaveDTO;
import com.sky.takeout.pojo.dto.category.CategoryUpdateDTO;
import com.sky.takeout.pojo.dto.category.CategoryEnableOrDisableDTO;
import com.sky.takeout.pojo.enums.CategoryType;

public interface CategoryService {

    Category getById(Long id);

    IPage<Category> page(CategoryQueryDTO categoryQueryDTO);

    List<Category> list(CategoryType categoryType);

    void save(CategorySaveDTO saveDTO);

    void update(CategoryUpdateDTO updateDTO);

    void delete(Long id);

    void enableOrDisable(Long id, CategoryEnableOrDisableDTO enableOrDisableDTO);

}
