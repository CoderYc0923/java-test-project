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
import com.sky.takeout.pojo.dto.setmeal.SetmealDishDTO;
import com.sky.takeout.pojo.dto.setmeal.SetmealEnableOrDisableDTO;
import com.sky.takeout.pojo.dto.setmeal.SetmealQueryDTO;
import com.sky.takeout.pojo.dto.setmeal.SetmealSaveDTO;
import com.sky.takeout.pojo.dto.setmeal.SetmealUpdateDTO;
import com.sky.takeout.pojo.entity.Category;
import com.sky.takeout.pojo.entity.Setmeal;
import com.sky.takeout.pojo.entity.SetmealDish;
import com.sky.takeout.pojo.enums.CategoryType;
import com.sky.takeout.pojo.enums.SaleStatus;
import com.sky.takeout.pojo.vo.setmeal.SetmealDishVO;
import com.sky.takeout.pojo.vo.setmeal.SetmealVO;
import com.sky.takeout.system.mapper.CategoryMapper;
import com.sky.takeout.system.mapper.SetmealDishMapper;
import com.sky.takeout.system.mapper.SetmealMapper;
import com.sky.takeout.system.oss.OssService;
import com.sky.takeout.system.service.SetmealService;

@Service
public class SetmealServiceImpl implements SetmealService {

    private final SetmealMapper setmealMapper;
    private final SetmealDishMapper setmealDishMapper;
    private final CategoryMapper categoryMapper;
    private final OssService ossService;

    private static final Logger log = LoggerFactory.getLogger(SetmealServiceImpl.class);

    public SetmealServiceImpl(
            SetmealMapper setmealMapper,
            SetmealDishMapper setmealDishMapper,
            CategoryMapper categoryMapper,
            OssService ossService) {
        this.setmealMapper = setmealMapper;
        this.setmealDishMapper = setmealDishMapper;
        this.categoryMapper = categoryMapper;
        this.ossService = ossService;
    }

    @Override
    public IPage<SetmealVO> page(SetmealQueryDTO queryDTO) {
        int pageNum = queryDTO.getPage() == null ? 1 : queryDTO.getPage();
        int pageSize = queryDTO.getPageSize() == null ? 10 : queryDTO.getPageSize();
        Page<Setmeal> setmealPage = new Page<>(pageNum, pageSize);

        LambdaQueryWrapper<Setmeal> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(StringUtils.hasText(queryDTO.getName()), Setmeal::getName, queryDTO.getName());
        wrapper.eq(queryDTO.getStatus() != null, Setmeal::getStatus, SaleStatus.fromCode(queryDTO.getStatus()));
        wrapper.eq(queryDTO.getCategoryId() != null, Setmeal::getCategoryId, queryDTO.getCategoryId());
        wrapper.orderByDesc(Setmeal::getUpdateTime);

        IPage<Setmeal> page = setmealMapper.selectPage(setmealPage, wrapper);
        List<Setmeal> records = page.getRecords();

        Map<Long, String> categoryNameMap = getCategoryNameMap(records.stream()
                .map(Setmeal::getCategoryId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet()));

        return page.convert(setmeal -> toVO(setmeal, categoryNameMap.get(setmeal.getCategoryId())));
    }

    @Override
    public SetmealVO getById(Long id) {
        Setmeal setmeal = setmealMapper.selectById(id);
        if (setmeal == null) {
            throw new BusinessException(ErrorCode.CONFLICT, "套餐不存在");
        }

        Map<Long, String> categoryNameMap = getCategoryNameMap(
                setmeal.getCategoryId() == null ? Collections.emptySet() : Set.of(setmeal.getCategoryId()));
        SetmealVO vo = toVO(setmeal, categoryNameMap.get(setmeal.getCategoryId()));

        List<SetmealDish> dishList = setmealDishMapper.selectList(
                new LambdaQueryWrapper<SetmealDish>().eq(SetmealDish::getSetmealId, id));
        vo.setSetmealDishes(dishList.stream().map(this::toDishVO).collect(Collectors.toList()));
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void save(SetmealSaveDTO saveDTO) {
        // 菜品列表/图片非空由 DTO 校验；这里只做业务规则
        validateNameUnique(saveDTO.getName(), null);
        Category category = requireSetmealCategory(saveDTO.getCategoryId());

        Setmeal setmeal = new Setmeal();
        setmeal.setName(saveDTO.getName());
        setmeal.setCategoryId(category.getId());
        setmeal.setPrice(saveDTO.getPrice());
        setmeal.setImage(saveDTO.getImageOssPath());
        setmeal.setDescription(saveDTO.getDescription());
        setmeal.setStatus(saveDTO.getStatus() == null
                ? SaleStatus.DISABLE
                : SaleStatus.fromCode(saveDTO.getStatus()));

        setmealMapper.insert(setmeal);
        insertSetmealDishes(setmeal.getId(), saveDTO.getSetmealDishes());
        log.info("新增套餐成功, id={}, name={}", setmeal.getId(), setmeal.getName());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(SetmealUpdateDTO updateDTO) {
        Setmeal setmeal = setmealMapper.selectById(updateDTO.getId());
        if (setmeal == null) {
            throw new BusinessException(ErrorCode.CONFLICT, "套餐不存在");
        }

        validateNameUnique(updateDTO.getName(), updateDTO.getId());
        Category category = requireSetmealCategory(updateDTO.getCategoryId());

        setmeal.setName(updateDTO.getName());
        setmeal.setCategoryId(category.getId());
        setmeal.setPrice(updateDTO.getPrice());
        setmeal.setImage(updateDTO.getImageOssPath());
        setmeal.setDescription(updateDTO.getDescription());
        setmeal.setStatus(SaleStatus.fromCode(updateDTO.getStatus()));
        setmealMapper.updateById(setmeal);

        setmealDishMapper.delete(
                new LambdaQueryWrapper<SetmealDish>().eq(SetmealDish::getSetmealId, setmeal.getId()));
        insertSetmealDishes(setmeal.getId(), updateDTO.getSetmealDishes());
        log.info("更新套餐成功, id={}, name={}", setmeal.getId(), setmeal.getName());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(String ids) {
        List<Long> idList = parseIdList(ids);

        List<Setmeal> setmealList = setmealMapper.selectByIds(idList);
        if (setmealList.size() != idList.size()) {
            throw new BusinessException(ErrorCode.ERROR, "套餐不存在");
        }

        boolean onSale = setmealList.stream().anyMatch(s -> s.getStatus() == SaleStatus.ENABLE);
        if (onSale) {
            throw new BusinessException(ErrorCode.ERROR, "启售中的套餐不能删除");
        }

        setmealDishMapper.delete(
                new LambdaQueryWrapper<SetmealDish>().in(SetmealDish::getSetmealId, idList));
        setmealMapper.deleteByIds(idList);
        log.info("删除套餐成功, ids={}", idList);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void enableOrDisable(String ids, SetmealEnableOrDisableDTO enableOrDisableDTO) {
        List<Long> idList = parseIdList(ids);

        List<Setmeal> setmealList = setmealMapper.selectByIds(idList);
        if (setmealList.size() != idList.size()) {
            throw new BusinessException(ErrorCode.ERROR, "套餐不存在");
        }

        Setmeal patch = new Setmeal();
        patch.setStatus(enableOrDisableDTO.getStatus());
        setmealMapper.update(patch, new LambdaQueryWrapper<Setmeal>().in(Setmeal::getId, idList));

        log.info("{}套餐成功, ids={}",
                enableOrDisableDTO.getStatus() == SaleStatus.ENABLE ? "启售" : "停售",
                idList);
    }

    private void validateNameUnique(String name, Long excludeId) {
        LambdaQueryWrapper<Setmeal> wrapper = new LambdaQueryWrapper<Setmeal>().eq(Setmeal::getName, name);
        if (excludeId != null) {
            wrapper.ne(Setmeal::getId, excludeId);
        }
        Long count = setmealMapper.selectCount(wrapper);
        if (count != null && count > 0) {
            throw new BusinessException(ErrorCode.ERROR, "套餐名称已存在");
        }
    }

    private Category requireSetmealCategory(Long categoryId) {
        Category category = categoryMapper.selectById(categoryId);
        if (category == null) {
            throw new BusinessException(ErrorCode.CONFLICT, "分类不存在");
        }
        if (category.getType() != CategoryType.SETMEAL) {
            throw new BusinessException(ErrorCode.ERROR, "只能选择套餐分类");
        }
        return category;
    }

    private void insertSetmealDishes(Long setmealId, List<SetmealDishDTO> dishes) {
        for (SetmealDishDTO dto : dishes) {
            SetmealDish relation = new SetmealDish();
            relation.setSetmealId(setmealId);
            relation.setDishId(dto.getDishId());
            relation.setName(dto.getName());
            relation.setPrice(dto.getPrice());
            relation.setCopies(dto.getCopies());
            setmealDishMapper.insert(relation);
        }
    }

    private List<Long> parseIdList(String ids) {
        if (!StringUtils.hasText(ids)) {
            throw new BusinessException(ErrorCode.ERROR, "套餐 id 不能为空");
        }
        try {
            List<Long> idList = Arrays.stream(ids.split(","))
                    .map(String::trim)
                    .filter(StringUtils::hasText)
                    .map(Long::parseLong)
                    .distinct()
                    .collect(Collectors.toList());
            if (idList.isEmpty()) {
                throw new BusinessException(ErrorCode.ERROR, "套餐 id 不能为空");
            }
            return idList;
        } catch (NumberFormatException e) {
            throw new BusinessException(ErrorCode.ERROR, "套餐 id 格式不正确");
        }
    }

    private Map<Long, String> getCategoryNameMap(Collection<Long> categoryIds) {
        if (categoryIds == null || categoryIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return categoryMapper.selectByIds(categoryIds).stream()
                .collect(Collectors.toMap(Category::getId, Category::getName, (a, b) -> a));
    }

    private SetmealVO toVO(Setmeal setmeal, String categoryName) {
        String image = setmeal.getImage();
        return SetmealVO.builder()
                .id(setmeal.getId())
                .name(setmeal.getName())
                .categoryId(setmeal.getCategoryId())
                .categoryName(categoryName)
                .price(setmeal.getPrice())
                .imageOssPath(image)
                .imageUrl(StringUtils.hasText(image) ? ossService.toAccessUrl(image) : null)
                .description(setmeal.getDescription())
                .status(setmeal.getStatus())
                .createTime(setmeal.getCreateTime())
                .updateTime(setmeal.getUpdateTime())
                .build();
    }

    private SetmealDishVO toDishVO(SetmealDish dish) {
        return SetmealDishVO.builder()
                .id(dish.getId())
                .setmealId(dish.getSetmealId())
                .dishId(dish.getDishId())
                .name(dish.getName())
                .price(dish.getPrice())
                .copies(dish.getCopies())
                .build();
    }
}
