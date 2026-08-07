package com.sky.takeout.admin.controller;

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
import com.sky.takeout.pojo.dto.setmeal.SetmealEnableOrDisableDTO;
import com.sky.takeout.pojo.dto.setmeal.SetmealQueryDTO;
import com.sky.takeout.pojo.dto.setmeal.SetmealSaveDTO;
import com.sky.takeout.pojo.dto.setmeal.SetmealUpdateDTO;
import com.sky.takeout.pojo.vo.setmeal.SetmealVO;
import com.sky.takeout.system.service.SetmealService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "套餐管理")
@RestController
@RequestMapping("/admin/setmeal")
public class SetmealController {

    @Autowired
    private SetmealService setmealService;

    @Operation(summary = "分页查询套餐")
    @GetMapping("/page")
    public Result<IPage<SetmealVO>> page(SetmealQueryDTO queryDTO) {
        return Result.success(setmealService.page(queryDTO));
    }

    @Operation(summary = "根据 id 查询套餐详情")
    @GetMapping("/{id}")
    public Result<SetmealVO> getById(@PathVariable Long id) {
        return Result.success(setmealService.getById(id));
    }

    @Operation(summary = "新增套餐")
    @PostMapping
    public Result<Void> save(@RequestBody SetmealSaveDTO saveDTO) {
        setmealService.save(saveDTO);
        return Result.success();
    }

    @Operation(summary = "修改套餐")
    @PutMapping
    public Result<Void> update(@RequestBody SetmealUpdateDTO updateDTO) {
        setmealService.update(updateDTO);
        return Result.success();
    }

    @Operation(summary = "删除套餐（支持批量，ids 逗号分隔）")
    @DeleteMapping
    public Result<Void> delete(@RequestParam String ids) {
        setmealService.delete(ids);
        return Result.success();
    }

    @Operation(summary = "启售 / 停售套餐（id 支持逗号批量）")
    @PostMapping("/{id}/status")
    public Result<Void> enableOrDisable(
            @PathVariable String id,
            @RequestBody SetmealEnableOrDisableDTO enableOrDisableDTO) {
        setmealService.enableOrDisable(id, enableOrDisableDTO);
        return Result.success();
    }
}
