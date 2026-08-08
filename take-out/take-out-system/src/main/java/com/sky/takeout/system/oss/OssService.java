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
import org.springframework.beans.factory.ObjectProvider;
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

    /** 未配置 AK 时为 null，应用仍可启动 */
    private final OSS ossClient;
    private final OssProperties ossProperties;
    private final Logger log = LoggerFactory.getLogger(OssService.class);

    public OssService(ObjectProvider<OSS> ossClientProvider, OssProperties ossProperties) {
        this.ossClient = ossClientProvider.getIfAvailable();
        this.ossProperties = ossProperties;
        if (this.ossClient == null) {
            log.warn("未配置 oss.access-key-id，OSS 上传/签名不可用；列表中的完整 http(s) 图片地址仍可正常展示");
        }
    }

    /**
     * 上传文件到私有桶
     */
    public FileUploadVO upload(InputStream inputStream, String originalFilename, String type) {
        requireOssConfigured();

        if (inputStream == null) {
            throw new BusinessException(ErrorCode.ERROR, "上传文件不能为空");
        }

        String prefix = nromalizeType(type);
        String ext = getExtension(originalFilename);
        if (!ALLOWED_EXTENSIONS.contains(ext)) {
            throw new BusinessException(ErrorCode.ERROR, "不支持的文件类型");
        }

        // dish/2026/08/07/uuid.png
        String datePath = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
        String objectKey = prefix + "/" + datePath + "/" + UUID.randomUUID().toString().replace("-", "") + "." + ext;

        try {
            ossClient.putObject(ossProperties.getBucket(), objectKey, inputStream);
        } catch (Exception e) {
            log.error("文件上传 OSS 失败", e);
            throw new BusinessException(ErrorCode.ERROR, "文件上传OSS失败");
        }

        log.info("上传文件到OSS成功，objectKey: {}", objectKey);
        return new FileUploadVO(objectKey, signUrl(objectKey));
    }

    /**
     * 查询展示：objectKey → 前端可访问地址。
     * 种子数据里已是 https 完整地址时直接返回；未配 OSS 时也不再强行签名。
     */
    public String toAccessUrl(String objectKey) {
        if (!StringUtils.hasText(objectKey)) {
            return null;
        }
        if (objectKey.startsWith("http://") || objectKey.startsWith("https://")) {
            return objectKey;
        }
        if (ossClient == null) {
            // 未配 OSS：无法签名，原样返回 key（上传功能仍不可用）
            return objectKey;
        }
        return signUrl(objectKey);
    }

    private String signUrl(String objectKey) {
        long expireMs = ossProperties.getSignExpireSeconds() * 1000L;
        Date expireDate = new Date(System.currentTimeMillis() + expireMs);

        GeneratePresignedUrlRequest request = new GeneratePresignedUrlRequest(ossProperties.getBucket(), objectKey);
        request.setExpiration(expireDate);

        URL url = ossClient.generatePresignedUrl(request);
        return url.toString();
    }

    private void requireOssConfigured() {
        if (ossClient == null) {
            throw new BusinessException(ErrorCode.ERROR,
                    "未配置阿里云 OSS：请设置环境变量 OSS_ACCESS_KEY_ID / OSS_ACCESS_KEY_SECRET / OSS_BUCKET，"
                            + "或在 application-local.yml 中填写 oss.access-key-id 等");
        }
    }

    private String nromalizeType(String type) {
        String t = StringUtils.hasText(type) ? type.toLowerCase() : "common";
        if (!ALLOWED_TYPES.contains(t)) {
            throw new BusinessException(ErrorCode.ERROR, "不支持的文件类型");
        }
        return t;
    }

    private String getExtension(String filename) {
        if (!StringUtils.hasText(filename) || !filename.contains(".")) {
            throw new BusinessException(ErrorCode.ERROR, "文件名无效");
        }
        return filename.substring(filename.lastIndexOf(".") + 1).toLowerCase();
    }
}
