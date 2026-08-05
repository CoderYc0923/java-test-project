package com.sky.takeout.system.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.sky.takeout.pojo.entity.Category;
import com.sky.takeout.pojo.dto.category.CategoryQueryDTO;
import com.sky.takeout.pojo.dto.category.CategorySaveDTO;
import com.sky.takeout.pojo.dto.category.CategoryUpdateDTO;
import com.sky.takeout.pojo.dto.category.CategoryEnableOrDisableDTO;

public interface CategoryService {

    IPage<Category> page(CategoryQueryDTO categoryQueryDTO);

    void save(CategorySaveDTO saveDTO);

    void update(CategoryUpdateDTO updateDTO);

    void delete(Long id);

    void enableOrDisable(Long id, CategoryEnableOrDisableDTO enableOrDisableDTO);

}
