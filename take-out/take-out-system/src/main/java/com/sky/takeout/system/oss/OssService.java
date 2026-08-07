package com.sky.takeout.system.oss;

import java.io.InputStream;
import java.net.URL;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.aliyun.oss.OSS;
import com.aliyun.oss.model.GeneratePresignedUrlRequest;
import com.sky.takeout.common.exception.BusinessException;
import com.sky.takeout.common.result.ErrorCode;
import com.sky.takeout.pojo.vo.fileUpload.FileUploadVO;

@Service
public class OssService {

    private static final Set<String> ALLOWED_TYPES = Set.of("dish", "setmeal", "common");
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp", "gif");

    private final OSS ossClient;
    private final OssProperties ossProperties;
    private final Logger log = LoggerFactory.getLogger(OssService.class);

    public OssService(OSS ossClient, OssProperties ossProperties) {
        this.ossClient = ossClient;
            this.ossProperties = ossProperties;
        }

    /**
     * 上传文件到私有桶
     * 
     * @param inputStream
     * @param originalFilename
     * @param type
     * @return
     */
    public FileUploadVO upload(InputStream inputStream, String originalFilename, String type) {

        if (inputStream == null) {
            throw new BusinessException(ErrorCode.ERROR, "上传文件不能为空");
        }

        String prefix = nromalizeType(type);
        String ext = getExtension(originalFilename);
        if (!ALLOWED_EXTENSIONS.contains(ext)) {
            throw new BusinessException(ErrorCode.ERROR, "不支持的文件类型");
        }

        // dish/2026/08/07/uuid.png
        String detePath = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
        String objectKey = prefix + "/" + detePath + "/" + UUID.randomUUID().toString().replace("-", "") + "." + ext;

        try {
            // 私有桶，用AK上传
            ossClient.putObject(ossProperties.getBucket(), objectKey, inputStream);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.ERROR, "文件上传OSS失败");
        }

        log.info("上传文件到OSS成功，objectKey: {}", objectKey);

        // 入库用key;url预览
        return new FileUploadVO(objectKey, signUrl(objectKey));
    }

    /**
     * 生成签名URL
     * @param objectKey
     * @return
     */
    private String signUrl(String objectKey) {

        // 签名URL有效期
        long expireMs = ossProperties.getSignExpireSeconds() * 1000L;
        Date expireDate = new Date(System.currentTimeMillis() + expireMs);

        // 生成签名URL请求
        GeneratePresignedUrlRequest request = new GeneratePresignedUrlRequest(ossProperties.getBucket(), objectKey);
        request.setExpiration(expireDate);

        // 生成签名URL
        URL url = ossClient.generatePresignedUrl(request);

        return url.toString();
    }

    /**
     * 查询展示：objectKey → 前端可访问地址。
     */
    public String toAccessUrl(String objectKey) {
        if (!StringUtils.hasText(objectKey)) {
            throw new BusinessException(ErrorCode.ERROR, "objectKey不能为空");
        }
        return signUrl(objectKey);
    }

    /**
     * 规范化文件类型
     * @param type
     * @return
     */
    private String nromalizeType(String type) {
        String t = StringUtils.hasText(type) ? type.toLowerCase() : "common";
        if (!ALLOWED_TYPES.contains(t)) {
            throw new BusinessException(ErrorCode.ERROR, "不支持的文件类型");
        }
        return t;
    }

    /**
     * 获取文件扩展名
     * @param filename
     * @return
     */
    private String getExtension(String filename) {
        if (!StringUtils.hasText(filename) || !filename.contains(".")) {
            throw new BusinessException(ErrorCode.ERROR, "文件名无效");
        }
        return filename.substring(filename.lastIndexOf(".") + 1).toLowerCase();
    }
}
