package com.sky.takeout.admin.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.sky.takeout.common.result.Result;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 通用接口占位（文件上传等）。
 */
@Tag(name = "通用接口")
@RestController
@RequestMapping("/admin/common")
public class CommonController {

    @Operation(summary = "文件上传")
    @PostMapping("/upload")
    public Result<String> upload(@RequestParam("file") MultipartFile file) {
        // TODO：本地存储或 OSS，返回可访问 URL
        return Result.success(null);
    }
}
