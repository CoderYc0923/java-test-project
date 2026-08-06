package com.sky.takeout.admin.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.sky.takeout.common.result.Result;
import com.sky.takeout.pojo.dto.dish.DishEnableOrDisableDTO;
import com.sky.takeout.pojo.dto.dish.DishQueryDTO;
import com.sky.takeout.pojo.dto.dish.DishSaveDTO;
import com.sky.takeout.pojo.dto.dish.DishUpdateDTO;
import com.sky.takeout.pojo.vo.dish.DishVO;
import com.sky.takeout.system.service.DishService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "菜品管理")
@RestController
@RequestMapping("/admin/dish")
public class DishController {

    @Autowired
    private DishService dishService;

    @Operation(summary = "分页查询菜品")
    @GetMapping("/page")
    public Result<IPage<DishVO>> page(DishQueryDTO queryDTO) {
        IPage<DishVO> voPage = dishService.page(queryDTO);

        return Result.success(voPage);
    }

    @Operation(summary = "按分类查询菜品列表")
    @GetMapping("/list")
    public Result<List<DishVO>> list(@RequestParam(required = false) Long categoryId) {
        return Result.success(dishService.list(categoryId));
    }

    @Operation(summary = "根据 id 查询菜品详情")
    @GetMapping("/{id}")
    public Result<DishVO> getById(@PathVariable Long id) {
        DishVO vo = dishService.getById(id);

        return Result.success(vo);
    }

    @Operation(summary = "新增菜品")
    @PostMapping
    public Result<Void> save(@RequestBody DishSaveDTO saveDTO) {
        dishService.save(saveDTO);
        return Result.success();
    }

    @Operation(summary = "修改菜品")
    @PutMapping
    public Result<Void> update(@RequestBody DishUpdateDTO updateDTO) {
        dishService.update(updateDTO);
        return Result.success();
    }

    @Operation(summary = "删除菜品（支持批量，ids 逗号分隔）")
    @DeleteMapping
    public Result<Void> delete(@RequestParam String ids) {
        dishService.delete(ids);
        return Result.success();
    }

    @Operation(summary = "启售 / 停售菜品（id 支持逗号批量）")
    @PostMapping("/{id}/status")
    public Result<Void> enableOrDisable(
            @PathVariable String id,
            @RequestBody DishEnableOrDisableDTO enableOrDisableDTO) {
        // path 用 String，兼容 "1" 与 "1,2,3"；现有前端传单个 id 不受影响
        dishService.enableOrDisable(id, enableOrDisableDTO);
        return Result.success();
    }
}
