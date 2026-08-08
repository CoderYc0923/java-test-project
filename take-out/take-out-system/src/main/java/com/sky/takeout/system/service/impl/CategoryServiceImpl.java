package com.sky.takeout.system.service.impl;

import com.sky.takeout.system.mapper.CategoryMapper;
import com.sky.takeout.system.mapper.DishMapper;
import com.sky.takeout.system.mapper.SetmealMapper;
import com.sky.takeout.system.service.CategoryService;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import com.sky.takeout.pojo.entity.Category;
import com.sky.takeout.pojo.entity.Dish;
import com.sky.takeout.pojo.entity.Setmeal;
import com.sky.takeout.pojo.enums.CategoryType;
import com.sky.takeout.pojo.dto.category.CategoryQueryDTO;
import com.sky.takeout.pojo.dto.category.CategorySaveDTO;
import com.sky.takeout.pojo.dto.category.CategoryUpdateDTO;
import com.sky.takeout.pojo.dto.category.CategoryEnableOrDisableDTO;

import com.sky.takeout.common.exception.BusinessException;
import com.sky.takeout.common.result.ErrorCode;


@Service
public class CategoryServiceImpl implements CategoryService {

    private CategoryMapper categoryMapper;
    private DishMapper dishMapper;
    private SetmealMapper setmealMapper;
    
    private static final Logger log = LoggerFactory.getLogger(CategoryServiceImpl.class);
    
    public CategoryServiceImpl(CategoryMapper categoryMapper, DishMapper dishMapper, SetmealMapper setmealMapper) {
        this.categoryMapper = categoryMapper;
        this.dishMapper = dishMapper;
        this.setmealMapper = setmealMapper;
    }

    @Override
    public Category getById(Long id) {
        Category category = categoryMapper.selectById(id);
        if (category == null) {
            throw new BusinessException(ErrorCode.ERROR, "分类不存在");
        }
        return category;
    }


    @Override
    public IPage<Category> page(CategoryQueryDTO categoryQueryDTO) {
        
        int pageNum = categoryQueryDTO.getPage() == null ? 1 : categoryQueryDTO.getPage();
        int pageSize = categoryQueryDTO.getPageSize() == null ? 10 : categoryQueryDTO.getPageSize();

        // 分页构造器
        Page<Category> page = new Page<>(pageNum, pageSize);

        // 查询条件
        LambdaQueryWrapper<Category> wrapper = new LambdaQueryWrapper<>();

        // 分类名称模糊查询
        wrapper.like(StringUtils.hasText(categoryQueryDTO.getName()), Category::getName, categoryQueryDTO.getName());

        // 分类类型
        wrapper.eq(categoryQueryDTO.getType() != null, Category::getType, CategoryType.fromCode(categoryQueryDTO.getType()));

        // 排序
        wrapper.orderByDesc(Category::getSort);
        
        return categoryMapper.selectPage(page, wrapper);
    }
    
    @Override
    public List<Category> list(CategoryType categoryType) {
        LambdaQueryWrapper<Category> wrapper = new LambdaQueryWrapper<>();

        // type有值才添加type条件
        wrapper.eq(categoryType != null, Category::getType, categoryType);

        // 排序
        wrapper.orderByAsc(Category::getSort);

        return categoryMapper.selectList(wrapper);
    }

    @Override
    public void save(CategorySaveDTO saveDTO) {
        // 1. 检查分类名称是否唯一
        Long count = categoryMapper.selectCount(new LambdaQueryWrapper<Category>().eq(Category::getName, saveDTO.getName()));
        if (count != null && count > 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "分类名称已存在");
        }

        // 2. DTO 转换为 Entity
        Category category = new Category();

        category.setName(saveDTO.getName());
        category.setType(saveDTO.getType());
        category.setSort(saveDTO.getSort());

        // 3. 补充前端不传字段
        category.setStatus(1);

        categoryMapper.insert(category);
        log.info("新增分类成功, name={}", category.getName());
    }

    @Override
    public void update(CategoryUpdateDTO updateDTO) {
        // 1. 检查分类是否存在
        Category category = getById(updateDTO.getId());

        // 2. 检查分类名称是否唯一
        Long count = categoryMapper.selectCount(new LambdaQueryWrapper<Category>().eq(Category::getName, updateDTO.getName()));
        if (count != null && count > 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "分类名称已存在");
        }
        
        // 3. DTO 转换为 Entity
        category.setName(updateDTO.getName());
        category.setSort(updateDTO.getSort());

        // 4. 更新
        categoryMapper.updateById(category);
        log.info("更新分类成功, name={}", category.getName());

    }

    @Override
    public void delete(Long id) {
        // 1. 检查分类是否存在
        Category category = getById(id);

        // 2. 检查分类下是否存在菜品或套餐引用
        if (category.getType() == CategoryType.DISH) {
            Long dishCount = dishMapper.selectCount(
                new LambdaQueryWrapper<Dish>()
                .eq(Dish::getCategoryId, id)
            );
            if (dishCount != null && dishCount > 0) {
                throw new BusinessException(ErrorCode.ERROR, "分类下存在菜品，不能删除");
            }
        } else if (category.getType() == CategoryType.SETMEAL) {
            Long setmealCount = setmealMapper.selectCount(
                new LambdaQueryWrapper<Setmeal>()
                .eq(Setmeal::getCategoryId, id)
            );
            if (setmealCount != null && setmealCount > 0) {
                throw new BusinessException(ErrorCode.ERROR, "分类下存在套餐，不能删除");
            }
        }

        // 3. 删除分类
        categoryMapper.deleteById(id);
        log.info("删除分类成功, id={}, name={}", id, category.getName());
        
    }

    @Override
    public void enableOrDisable(Long id, CategoryEnableOrDisableDTO enableOrDisableDTO) {
        // 1. 检查分类是否存在
        Category category = getById(id);

        category.setStatus(enableOrDisableDTO.getStatus());

        categoryMapper.updateById(category);
        log.info("分类{}成功, id={}, name={}", enableOrDisableDTO.getStatus() == 1 ? "启用" : "禁用", id, category.getName());
    }

}
