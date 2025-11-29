package com.demo.vo;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

/**
 * 头像直传上传配置
 */
@Data
@Builder
public class AvatarUploadConfigVO {
    /**
     * 前端直传使用的预签名上传 URL
     */
    private String uploadUrl;

    /**
     * 资源的最终访问 URL
     */
    private String resourceUrl;

    /**
     * 过期秒数
     */
    private Integer expiresIn;

    /**
     * 需要附带的额外头信息，如 content-type
     */
    private Map<String, String> extraHeaders;
}