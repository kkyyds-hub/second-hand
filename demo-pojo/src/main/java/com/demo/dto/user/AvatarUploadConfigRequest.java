package com.demo.dto.user;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;

/**
 * 请求生成头像直传上传配置
 */
@Data
public class AvatarUploadConfigRequest {

    /**
     * 原始文件名，用于生成对象名
     */
    @NotBlank(message = "文件名不能为空")
    private String fileName;

    /**
     * MIME 类型，仅支持 image/jpeg 或 image/png
     */
    @NotBlank(message = "contentType 不能为空")
    @Pattern(regexp = "image/(jpeg|png)", message = "仅支持 JPEG 或 PNG 头像上传")
    private String contentType;
}