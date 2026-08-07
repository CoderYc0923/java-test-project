package com.sky.takeout.admin.controller;

import java.io.IOException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.sky.takeout.common.exception.BusinessException;
import com.sky.takeout.common.result.ErrorCode;
import com.sky.takeout.common.result.Result;
import com.sky.takeout.pojo.vo.fileUpload.FileUploadVO;
import com.sky.takeout.system.oss.OssService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 通用接口
 */
@Tag(name = "通用接口")
@RestController
@RequestMapping("/admin/common")
public class CommonController {

    @Autowired
    private OssService ossService;

    @Operation(summary = "文件上传")
    @PostMapping("/upload")
    public Result<FileUploadVO> upload(@RequestParam("file") MultipartFile file,
            @RequestParam(value = "type", required = false) String type) {

        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.ERROR, "上传文件不能为空");
        }

        try {
            FileUploadVO fileUploadVO = ossService.upload(file.getInputStream(),file.getOriginalFilename(), type);
            return Result.success(fileUploadVO);
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.ERROR, "读取上传文件失败");
        }
    }
}
