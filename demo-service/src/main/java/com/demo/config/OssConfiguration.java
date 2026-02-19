package com.demo.config;

import com.demo.properties.AliOssProperties;
import com.demo.utils.AliOssUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OSS 组件配置类。
 * 基于外部配置构建单例 {@link AliOssUtil}。
 */
@Configuration
@Slf4j
public class OssConfiguration {

    /**
     * 当容器中不存在自定义实现时，注册默认 OSS 工具 Bean。
     */
    @Bean
    @ConditionalOnMissingBean
    public AliOssUtil aliOssUtil(AliOssProperties aliOssProperties) {
        log.info("Initializing AliOssUtil with endpoint: {}", aliOssProperties.getEndpoint());
        return new AliOssUtil(
                aliOssProperties.getEndpoint(),
                aliOssProperties.getAccessKeyId(),
                aliOssProperties.getAccessKeySecret(),
                aliOssProperties.getBucketName()
        );
    }
}
