package com.sky.takeout.system.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.sky.takeout.pojo.dto.setmeal.SetmealEnableOrDisableDTO;
import com.sky.takeout.pojo.dto.setmeal.SetmealQueryDTO;
import com.sky.takeout.pojo.dto.setmeal.SetmealSaveDTO;
import com.sky.takeout.pojo.dto.setmeal.SetmealUpdateDTO;
import com.sky.takeout.pojo.vo.setmeal.SetmealVO;

public interface SetmealService {

    IPage<SetmealVO> page(SetmealQueryDTO queryDTO);

    SetmealVO getById(Long id);

    void save(SetmealSaveDTO saveDTO);

    void update(SetmealUpdateDTO updateDTO);

    void delete(String ids);

    void enableOrDisable(String ids, SetmealEnableOrDisableDTO enableOrDisableDTO);
}
