package com.sky.takeout.system.service.impl;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sky.takeout.common.exception.BusinessException;
import com.sky.takeout.common.result.ErrorCode;
import com.sky.takeout.pojo.dto.dish.DishEnableOrDisableDTO;
import com.sky.takeout.pojo.dto.dish.DishQueryDTO;
import com.sky.takeout.pojo.dto.dish.DishSaveDTO;
import com.sky.takeout.pojo.dto.dish.DishUpdateDTO;
import com.sky.takeout.pojo.entity.Category;
import com.sky.takeout.pojo.entity.Dish;
import com.sky.takeout.pojo.enums.SaleStatus;
import com.sky.takeout.pojo.vo.dish.DishVO;
import com.sky.takeout.system.mapper.DishFlavorMapper;
import com.sky.takeout.system.mapper.DishMapper;
import com.sky.takeout.system.mapper.CategoryMapper;
import com.sky.takeout.system.service.DishService;

/**
 * 菜品业务占位，具体逻辑后续实现。
 */
@Service
public class DishServiceImpl implements DishService {

    private final DishMapper dishMapper;
    private final DishFlavorMapper dishFlavorMapper;
    private final CategoryMapper categoryMapper;

    public DishServiceImpl(DishMapper dishMapper, DishFlavorMapper dishFlavorMapper, CategoryMapper categoryMapper) {
        this.dishMapper = dishMapper;
        this.dishFlavorMapper = dishFlavorMapper;
        this.categoryMapper = categoryMapper;
    }

    @Override
    public IPage<DishVO> page(DishQueryDTO queryDTO) {
        // 1.分页
        int pageNum = queryDTO.getPage() == null || queryDTO.getPage() <= 0 ? 1 : queryDTO.getPage();
        int pageSize = queryDTO.getPageSize() == null || queryDTO.getPageSize() <= 0 ? 10 : queryDTO.getPageSize();

        Page<Dish> dishPage = new Page<>(pageNum, pageSize);

        // 2.查询条件
        LambdaQueryWrapper<Dish> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.like(StringUtils.hasText(queryDTO.getName()), Dish::getName, queryDTO.getName());
        queryWrapper.eq(queryDTO.getStatus() != null, Dish::getStatus, SaleStatus.fromCode(queryDTO.getStatus()));
        queryWrapper.eq(queryDTO.getCategoryId() != null, Dish::getCategoryId, queryDTO.getCategoryId());
        queryWrapper.orderByDesc(Dish::getCreateTime);

        // 3.查菜品
        IPage<Dish> page = dishMapper.selectPage(dishPage, queryWrapper);
        List<Dish> records = page.getRecords();

        // 4.批量查分类名
        Set<Long> categoryIds = records.stream()
                .map(Dish::getCategoryId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, String> categoryNameMap = getCategoryNameMap(categoryIds);

        // 5.将Dish转换为DishVO
        return page.convert(dish -> toVO(dish, categoryNameMap.get(dish.getCategoryId())));
    }

    @Override
    public List<Dish> list(Long categoryId) {
        LambdaQueryWrapper<Dish> queryWrapper = new LambdaQueryWrapper<>();

        queryWrapper.eq(categoryId != null, Dish::getCategoryId, categoryId);

        queryWrapper.orderByDesc(Dish::getCreateTime);

        return dishMapper.selectList(queryWrapper);
    }

    @Override
    public DishVO getById(Long id) {
        Dish dish = dishMapper.selectById(id);

        if (dish == null) {
            throw new BusinessException(ErrorCode.CONFLICT, "菜品不存在");
        }

        Map<Long, String> categoryNameMap = getCategoryNameMap(
                dish.getCategoryId() == null ? Collections.emptySet() : Set.of(dish.getCategoryId()));
        return toVO(dish, categoryNameMap.get(dish.getCategoryId()));
    }

    @Override
    public void save(DishSaveDTO saveDTO) {
        // TODO
    }

    @Override
    public void update(DishUpdateDTO updateDTO) {
        // TODO
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(String ids) {
        if (!StringUtils.hasText(ids)) {
            throw new BusinessException(ErrorCode.ERROR, "菜品id不能为空");
        }

        List<Long> idList = Arrays.stream(ids.split(","))
            .map(String::trim)
            .filter(StringUtils::hasText)
            .map(Long::parseLong)
            .distinct()
            .collect(Collectors.toList());
        
        if (idList.isEmpty()) {
            throw new BusinessException(ErrorCode.ERROR, "菜品id不能为空");
        }

        

        
    }

    @Override
    public void enableOrDisable(Long id, DishEnableOrDisableDTO enableOrDisableDTO) {
        // TODO
    }

    /**
     * 按分类 id 批量查询名称，返回 id → name。
     * 分页、详情、列表组装 VO 时复用。
     */
    private Map<Long, String> getCategoryNameMap(Collection<Long> categoryIds) {
        if (categoryIds == null || categoryIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return categoryMapper.selectByIds(categoryIds).stream()
                .collect(Collectors.toMap(Category::getId, Category::getName, (a, b) -> a));
    }

    private DishVO toVO(Dish dish, String categoryName) {
        return DishVO.builder()
                .id(dish.getId())
                .name(dish.getName())
                .categoryId(dish.getCategoryId())
                .categoryName(categoryName)
                .price(dish.getPrice())
                .image(dish.getImage())
                .description(dish.getDescription())
                .status(dish.getStatus())
                .createTime(dish.getCreateTime())
                .updateTime(dish.getUpdateTime())
                .build();
    }
}
