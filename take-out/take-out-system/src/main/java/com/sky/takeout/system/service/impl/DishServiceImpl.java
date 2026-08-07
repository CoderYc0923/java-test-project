package com.sky.takeout.system.service.impl;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sky.takeout.common.exception.BusinessException;
import com.sky.takeout.common.result.ErrorCode;
import com.sky.takeout.pojo.dto.dish.DishEnableOrDisableDTO;
import com.sky.takeout.pojo.dto.dish.DishFlavorDTO;
import com.sky.takeout.pojo.dto.dish.DishQueryDTO;
import com.sky.takeout.pojo.dto.dish.DishSaveDTO;
import com.sky.takeout.pojo.dto.dish.DishUpdateDTO;
import com.sky.takeout.pojo.entity.Category;
import com.sky.takeout.pojo.entity.Dish;
import com.sky.takeout.pojo.entity.DishFlavor;
import com.sky.takeout.pojo.entity.SetmealDish;
import com.sky.takeout.pojo.enums.CategoryType;
import com.sky.takeout.pojo.enums.SaleStatus;
import com.sky.takeout.pojo.vo.dish.DishFlavorVO;
import com.sky.takeout.pojo.vo.dish.DishVO;
import com.sky.takeout.system.mapper.CategoryMapper;
import com.sky.takeout.system.mapper.DishFlavorMapper;
import com.sky.takeout.system.mapper.DishMapper;
import com.sky.takeout.system.mapper.SetmealDishMapper;
import com.sky.takeout.system.oss.OssService;
import com.sky.takeout.system.service.DishService;

/**
 * 菜品业务实现：分页 / 列表 / 详情 / 增删改 / 启停。
 */
@Service
public class DishServiceImpl implements DishService {

    private final DishMapper dishMapper;
    private final DishFlavorMapper dishFlavorMapper;
    private final CategoryMapper categoryMapper;
    private final SetmealDishMapper setmealDishMapper;
    private final OssService ossService;

    private static final Logger log = LoggerFactory.getLogger(DishServiceImpl.class);

    public DishServiceImpl(
            DishMapper dishMapper,
            DishFlavorMapper dishFlavorMapper,
            CategoryMapper categoryMapper,
            SetmealDishMapper setmealDishMapper,
            OssService ossService) {
        this.dishMapper = dishMapper;
        this.dishFlavorMapper = dishFlavorMapper;
        this.categoryMapper = categoryMapper;
        this.setmealDishMapper = setmealDishMapper;
        this.ossService = ossService;
    }

    @Override
    public IPage<DishVO> page(DishQueryDTO queryDTO) {
        // 1. 分页参数兜底
        int pageNum = queryDTO.getPage() == null || queryDTO.getPage() <= 0 ? 1 : queryDTO.getPage();
        int pageSize = queryDTO.getPageSize() == null || queryDTO.getPageSize() <= 0 ? 10 : queryDTO.getPageSize();
        Page<Dish> dishPage = new Page<>(pageNum, pageSize);

        // 2. 查询条件
        LambdaQueryWrapper<Dish> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.like(StringUtils.hasText(queryDTO.getName()), Dish::getName, queryDTO.getName());
        queryWrapper.eq(queryDTO.getStatus() != null, Dish::getStatus, SaleStatus.fromCode(queryDTO.getStatus()));
        queryWrapper.eq(queryDTO.getCategoryId() != null, Dish::getCategoryId, queryDTO.getCategoryId());
        queryWrapper.orderByDesc(Dish::getCreateTime);

        // 3. 查菜品
        IPage<Dish> page = dishMapper.selectPage(dishPage, queryWrapper);
        List<Dish> records = page.getRecords();

        // 4. 批量查分类名，避免 N+1
        Map<Long, String> categoryNameMap = getCategoryNameMap(records.stream()
                .map(Dish::getCategoryId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet()));

        // 5. 转 VO（分页列表不带 flavors）
        return page.convert(dish -> toVO(dish, categoryNameMap.get(dish.getCategoryId())));
    }

    /**
     * 按分类查菜品列表（套餐选菜等场景）。
     * 只返回启售中的菜品，避免停售菜被选进套餐。
     */
    @Override
    public List<DishVO> list(Long categoryId) {
        LambdaQueryWrapper<Dish> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(categoryId != null, Dish::getCategoryId, categoryId);
        // 套餐选菜只需启售菜品
        queryWrapper.eq(Dish::getStatus, SaleStatus.ENABLE);
        queryWrapper.orderByDesc(Dish::getCreateTime);

        List<Dish> dishList = dishMapper.selectList(queryWrapper);

        // 批量查分类名，避免在 stream 里逐条查库
        Map<Long, String> categoryNameMap = getCategoryNameMap(dishList.stream()
                .map(Dish::getCategoryId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet()));

        return dishList.stream()
                .map(dish -> toVO(dish, categoryNameMap.get(dish.getCategoryId())))
                .collect(Collectors.toList());
    }

    @Override
    public DishVO getById(Long id) {
        Dish dish = dishMapper.selectById(id);
        if (dish == null) {
            throw new BusinessException(ErrorCode.CONFLICT, "菜品不存在");
        }

        Map<Long, String> categoryNameMap = getCategoryNameMap(
                dish.getCategoryId() == null ? Collections.emptySet() : Set.of(dish.getCategoryId()));
        DishVO dishVO = toVO(dish, categoryNameMap.get(dish.getCategoryId()));

        // 详情需要回显口味；分页/列表不查 flavors
        List<DishFlavor> dishFlavorList = dishFlavorMapper.selectList(
                new LambdaQueryWrapper<DishFlavor>().eq(DishFlavor::getDishId, id));
        List<DishFlavorVO> flavorVOList = dishFlavorList.stream()
                .map(this::toFlavorVO)
                .collect(Collectors.toList());
        dishVO.setFlavors(flavorVOList);

        return dishVO;
    }

    /**
     * 新增菜品（主表 + 口味）。
     * rollbackFor = Exception.class：出现 Exception 及其子类都回滚，避免只写下半段。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void save(DishSaveDTO saveDTO) {
        // 1. 名称唯一
        Long count = dishMapper.selectCount(
                new LambdaQueryWrapper<Dish>().eq(Dish::getName, saveDTO.getName()));
        if (count != null && count > 0) {
            throw new BusinessException(ErrorCode.ERROR, "菜品名称已存在");
        }

        // 2. 分类存在且为菜品分类
        Category category = categoryMapper.selectById(saveDTO.getCategoryId());
        if (category == null) {
            throw new BusinessException(ErrorCode.CONFLICT, "分类不存在");
        }
        if (category.getType() != CategoryType.DISH) {
            throw new BusinessException(ErrorCode.ERROR, "只能选择菜品分类");
        }

        // 3. DTO → Entity；status 为空时默认停售（与前端新增默认一致）
        Dish dish = new Dish();
        dish.setName(saveDTO.getName());
        dish.setCategoryId(saveDTO.getCategoryId());
        dish.setPrice(saveDTO.getPrice());
        dish.setImage(saveDTO.getImageOssPath());
        dish.setDescription(saveDTO.getDescription());
        dish.setStatus(saveDTO.getStatus() == null
                ? SaleStatus.DISABLE
                : SaleStatus.fromCode(saveDTO.getStatus()));

        // 4. 插入菜品（主键回填到 dish.id）
        dishMapper.insert(dish);

        // 5. 插入口味（可为空；口味数量通常很少，逐条 insert 即可）
        insertFlavors(dish.getId(), saveDTO.getFlavors());

        log.info("新增菜品成功, id={}, name={}", dish.getId(), dish.getName());
    }

    /**
     * 修改菜品：更新主表后，口味采用「先删后插」覆盖更新。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(DishUpdateDTO updateDTO) {
        Dish dish = dishMapper.selectById(updateDTO.getId());
        if (dish == null) {
            throw new BusinessException(ErrorCode.CONFLICT, "菜品不存在");
        }

        // 名称唯一：ne 排除当前菜品，避免「名字不变」时误报冲突
        Long count = dishMapper.selectCount(new LambdaQueryWrapper<Dish>()
                .eq(Dish::getName, updateDTO.getName())
                .ne(Dish::getId, updateDTO.getId()));
        if (count != null && count > 0) {
            throw new BusinessException(ErrorCode.ERROR, "菜品名称已存在");
        }

        Category category = categoryMapper.selectById(updateDTO.getCategoryId());
        if (category == null) {
            throw new BusinessException(ErrorCode.CONFLICT, "分类不存在");
        }
        if (category.getType() != CategoryType.DISH) {
            throw new BusinessException(ErrorCode.ERROR, "只能选择菜品分类");
        }

        dish.setName(updateDTO.getName());
        dish.setCategoryId(updateDTO.getCategoryId());
        dish.setPrice(updateDTO.getPrice());
        dish.setImage(updateDTO.getImageOssPath());
        dish.setDescription(updateDTO.getDescription());
        dish.setStatus(SaleStatus.fromCode(updateDTO.getStatus()));
        dishMapper.updateById(dish);

        // 口味覆盖：先按 dishId 清空，再插入新列表
        dishFlavorMapper.delete(
                new LambdaQueryWrapper<DishFlavor>().eq(DishFlavor::getDishId, dish.getId()));
        insertFlavors(dish.getId(), updateDTO.getFlavors());

        log.info("更新菜品成功, id={}, name={}", dish.getId(), dish.getName());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(String ids) {
        List<Long> idList = parseIdList(ids);

        List<Dish> dishList = dishMapper.selectByIds(idList);
        if (dishList.size() != idList.size()) {
            throw new BusinessException(ErrorCode.ERROR, "菜品不存在");
        }

        // 启售中不能删
        boolean onSale = dishList.stream().anyMatch(dish -> dish.getStatus() == SaleStatus.ENABLE);
        if (onSale) {
            throw new BusinessException(ErrorCode.ERROR, "启售中的菜品不能删除");
        }

        // 被套餐关联不能删
        Long relatedCount = setmealDishMapper.selectCount(
                new LambdaQueryWrapper<SetmealDish>().in(SetmealDish::getDishId, idList));
        if (relatedCount != null && relatedCount > 0) {
            throw new BusinessException(ErrorCode.ERROR, "被套餐关联的菜品不能删除");
        }

        // 先删子表口味，再删主表
        dishFlavorMapper.delete(
                new LambdaQueryWrapper<DishFlavor>().in(DishFlavor::getDishId, idList));
        dishMapper.deleteByIds(idList);
        log.info("删除菜品成功, ids={}", idList);
    }

    /**
     * 启售 / 停售。
     * ids 支持单个或逗号分隔批量，例如 "1" 或 "1,2,3"（兼容前端单 id，也方便批量）。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void enableOrDisable(String ids, DishEnableOrDisableDTO enableOrDisableDTO) {
        List<Long> idList = parseIdList(ids);

        List<Dish> dishList = dishMapper.selectByIds(idList);
        if (dishList.size() != idList.size()) {
            throw new BusinessException(ErrorCode.ERROR, "菜品不存在");
        }

        // 一次 UPDATE ... WHERE id IN (...)，避免循环 updateById
        Dish patch = new Dish();
        patch.setStatus(enableOrDisableDTO.getStatus());
        dishMapper.update(patch, new LambdaQueryWrapper<Dish>().in(Dish::getId, idList));

        log.info("{}菜品成功, ids={}",
                enableOrDisableDTO.getStatus() == SaleStatus.ENABLE ? "启售" : "停售",
                idList);
    }

    /**
     * 解析逗号分隔 id；非法数字转为业务异常，避免直接抛 NumberFormatException。
     */
    private List<Long> parseIdList(String ids) {
        if (!StringUtils.hasText(ids)) {
            throw new BusinessException(ErrorCode.ERROR, "菜品id不能为空");
        }
        try {
            List<Long> idList = Arrays.stream(ids.split(","))
                    .map(String::trim)
                    .filter(StringUtils::hasText)
                    .map(Long::parseLong)
                    .distinct()
                    .collect(Collectors.toList());
            if (idList.isEmpty()) {
                throw new BusinessException(ErrorCode.ERROR, "菜品id不能为空");
            }
            return idList;
        } catch (NumberFormatException e) {
            throw new BusinessException(ErrorCode.ERROR, "菜品id格式不正确");
        }
    }

    /**
     * 批量插入口味。
     * 口味条数通常很少（几个选项维度），逐条 insert 足够；
     * 若日后需要大批量写入，可改为自定义 XML foreach 批量插入。
     */
    private void insertFlavors(Long dishId, List<DishFlavorDTO> flavorList) {
        if (flavorList == null || flavorList.isEmpty()) {
            return;
        }
        flavorList.stream()
                .map(flavorDTO -> {
                    DishFlavor flavor = new DishFlavor();
                    flavor.setDishId(dishId);
                    flavor.setName(flavorDTO.getName());
                    flavor.setValue(flavorDTO.getValue());
                    return flavor;
                })
                .forEach(dishFlavorMapper::insert);
    }

    /** 按分类 id 批量查名称，返回 id → name。 */
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
                .imageOssPath(dish.getImage())
                .imageUrl(ossService.toAccessUrl(dish.getImage()))
                .description(dish.getDescription())
                .status(dish.getStatus())
                .createTime(dish.getCreateTime())
                .updateTime(dish.getUpdateTime())
                .build();
    }

    private DishFlavorVO toFlavorVO(DishFlavor flavor) {
        DishFlavorVO vo = new DishFlavorVO();
        vo.setId(flavor.getId());
        vo.setDishId(flavor.getDishId());
        vo.setName(flavor.getName());
        vo.setValue(flavor.getValue());
        return vo;
    }
}
