package com.demo.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 邮件认证演示配置。
 */
@Data
@Component
@ConfigurationProperties(prefix = "demo.auth.email")
public class EmailProperties {

    /**
     * 当前启用的邮件 provider。
     * 支持：mock / smtp
     */
    private String provider = "mock";

    /**
     * 邮箱注册时是否强制校验邮箱验证码。
     * 演示环境默认关闭，避免依赖额外的“发邮箱验证码”链路。
     */
    private boolean registerCodeRequired = false;

    /**
     * 激活链接有效期，单位小时。
     */
    private int activationTtlHours = 24;

    /**
     * 激活页面对外基础地址。
     * 这里应指向用户端前台 origin，而不是 backend JSON 接口 origin。
     */
    private String activationBaseUrl = "http://localhost:5173";

    /**
     * mock provider 子配置。
     */
    private Mock mock = new Mock();

    public String normalizedActivationBaseUrl() {
        String baseUrl = StringUtils.hasText(activationBaseUrl) ? activationBaseUrl.trim() : "http://localhost:5173";
        while (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        return baseUrl;
    }

    @Data
    public static class Mock {
        /**
         * 是否启用 mock provider。
         */
        private boolean enabled = true;

        /**
         * 是否保存邮件预览记录。
         */
        private boolean storePreview = true;

        /**
         * 最近保留的邮件预览条数。
         */
        private int recentLimit = 20;

        /**
         * 是否在日志中打印激活链接。
         */
        private boolean logActivationUrl = true;
    }
}
