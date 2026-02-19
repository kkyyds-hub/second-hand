package com.demo.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * JWT 配置项。
 * 分别维护管理端与用户端的密钥、Token 名称和有效期。
 */
@Component
@ConfigurationProperties(prefix = "demo.jwt")
@Data
public class JwtProperties {

    /** 管理端 JWT 密钥（admin-secret-key）。 */
    private String adminSecretKey;

    /** 管理端 Token 名称（admin-token-name）。 */
    private String adminTokenName;

    /** 管理端 Token 有效期（admin-ttl，毫秒）。 */
    private long adminTtl;

    /** 用户端 JWT 密钥（user-secret-key）。 */
    private String userSecretKey;

    /** 用户端 Token 名称（user-token-name）。 */
    private String userTokenName;

    /** 用户端 Token 有效期（user-ttl，毫秒）。 */
    private long userTtl;
}
