package com.sky.takeout.system.service;

import java.util.List;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.sky.takeout.pojo.dto.dish.DishEnableOrDisableDTO;
import com.sky.takeout.pojo.dto.dish.DishQueryDTO;
import com.sky.takeout.pojo.dto.dish.DishSaveDTO;
import com.sky.takeout.pojo.dto.dish.DishUpdateDTO;
import com.sky.takeout.pojo.entity.Dish;
import com.sky.takeout.pojo.vo.dish.DishVO;    

public interface DishService {

    IPage<DishVO> page(DishQueryDTO queryDTO);

    List<Dish> list(Long categoryId);

    DishVO getById(Long id);

    void save(DishSaveDTO saveDTO);

    void update(DishUpdateDTO updateDTO);

    void delete(String ids);

    void enableOrDisable(Long id, DishEnableOrDisableDTO enableOrDisableDTO);
}
