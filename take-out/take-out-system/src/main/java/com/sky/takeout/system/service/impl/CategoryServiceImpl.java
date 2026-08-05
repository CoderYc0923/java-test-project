package com.sky.takeout.system.service.impl;

import com.sky.takeout.system.service.CategoryService;

import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.sky.takeout.pojo.entity.Category;
import com.sky.takeout.pojo.dto.category.CategoryQueryDTO;
import com.sky.takeout.pojo.dto.category.CategorySaveDTO;
import com.sky.takeout.pojo.dto.category.CategoryUpdateDTO;
import com.sky.takeout.pojo.dto.category.CategoryEnableOrDisableDTO;

@Service
public class CategoryServiceImpl implements CategoryService {

    @Override
    public IPage<Category> page(CategoryQueryDTO categoryQueryDTO) {
        return null;
    }

    @Override
    public void save(CategorySaveDTO saveDTO) {
        
    }

    @Override
    public void update(CategoryUpdateDTO updateDTO) {}

    @Override
    public void delete(Long id) {}

    @Override
    public void enableOrDisable(Long id, CategoryEnableOrDisableDTO enableOrDisableDTO) {}

}
