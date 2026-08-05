package com.sky.takeout.admin.controller;

import org.springframework.web.bind.annotation.RestController;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.sky.takeout.common.result.Result;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;

import com.sky.takeout.pojo.dto.category.CategoryQueryDTO;
import com.sky.takeout.pojo.dto.category.CategorySaveDTO;
import com.sky.takeout.pojo.dto.category.CategoryUpdateDTO;
import com.sky.takeout.pojo.dto.category.CategoryEnableOrDisableDTO;
import com.sky.takeout.pojo.vo.category.CategoryVO;
import com.sky.takeout.pojo.entity.Category;

import com.sky.takeout.system.service.CategoryService;


@Tag(name = "分类管理")
@RestController
@RequestMapping("/admin/category")
public class CategoryController {

    @Autowired
    private CategoryService categoryService;

    @Operation(summary = "分页查询分类")
    @GetMapping("/page")
    public Result<IPage<CategoryVO>> page(CategoryQueryDTO categoryQueryDTO) {
        IPage<Category> page = categoryService.page(categoryQueryDTO);
        IPage<CategoryVO> voPage = page.convert(CategoryController::toVO);
        return Result.success(voPage);
    }

    @Operation(summary = "新增分类")
    @PostMapping
    public Result<Void> save(@RequestBody CategorySaveDTO categorySaveDTO) {
        return Result.success();
    }

    @Operation(summary = "修改分类")
    @PutMapping
    public Result<Void> update(@RequestBody CategoryUpdateDTO categoryUpdateDTO) {
        return Result.success();
    }

    @Operation(summary = "删除分类")
    @DeleteMapping
    public Result<Void> delete(@PathVariable Long id) {
        return Result.success();
    }


    @Operation(summary = "启用禁用分类")
    @PostMapping("/{id}/status")
    public Result<Void> enableOrDisable(@PathVariable Long id, @RequestBody CategoryEnableOrDisableDTO categoryEnableOrDisableDTO) {
        return Result.success();
    }
    

    private static CategoryVO toVO(Category category) {
        CategoryVO vo = new CategoryVO();
        vo.setId(category.getId());
        vo.setName(category.getName());
        vo.setType(category.getType());
        vo.setSort(category.getSort());
        vo.setStatus(category.getStatus());
        vo.setCreateTime(category.getCreateTime());
        vo.setUpdateTime(category.getUpdateTime());
        return vo;
    }
}
