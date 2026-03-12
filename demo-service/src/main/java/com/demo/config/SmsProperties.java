package com.demo.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 短信验证码配置。
 *
 * 设计目标：
 * 1) 默认走 mock，避免演示环境依赖真实短信网关；
 * 2) Redis 缓存、频控、过期时间仍然保留；
 * 3) 后续若接入真实短信，只需补充 provider 实现并切换配置。
 */
@Data
@Component
@ConfigurationProperties(prefix = "demo.auth.sms")
public class SmsProperties {

    /**
     * 当前启用的短信 provider。
     * 目前支持：mock
     */
    private String provider = "mock";

    /**
     * 验证码有效期，单位分钟。
     */
    private int codeTtlMinutes = 5;

    /**
     * 发送频率限制，单位秒。
     */
    private int rateLimitSeconds = 60;

    /**
     * mock provider 子配置。
     */
    private Mock mock = new Mock();

    @Data
    public static class Mock {
        /**
         * 是否启用 mock provider。
         */
        private boolean enabled = true;

        /**
         * 开发环境是否打印验证码到日志。
         */
        private boolean logCode = true;
    }
}
