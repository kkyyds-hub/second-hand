package com.demo.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 阿里云 OSS 配置项。
 */
@Component
@ConfigurationProperties(prefix = "sky.alioss")
@Data
public class AliOssProperties {

    /** OSS 访问域名。 */
    private String endpoint;

    /** 访问密钥 ID。 */
    private String accessKeyId;

    /** 访问密钥 Secret。 */
    private String accessKeySecret;

    /** OSS Bucket 名称。 */
    private String bucketName;
}
