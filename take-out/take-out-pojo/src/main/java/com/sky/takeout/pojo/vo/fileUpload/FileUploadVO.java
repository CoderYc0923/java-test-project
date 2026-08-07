package com.sky.takeout.pojo.vo.fileUpload;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class FileUploadVO {

    // 入库用：永久objectKey
    private String objectKey;

    // 前端用：临时URL
    private String url;
}
