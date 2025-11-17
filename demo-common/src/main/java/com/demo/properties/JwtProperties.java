package com.demo.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "demo.jwt")  // ✅ 改为demo.jwt
@Data
public class JwtProperties {
    private String adminSecretKey;  // 对应 admin-secret-key
    private String adminTokenName;  // 对应 admin-token-name
    private long adminTtl;          // 对应 admin-ttl

    private String userSecretKey;    // 对应 user-secret-key
    private String userTokenName;   // 对应 user-token-name
    private long userTtl;           // 对应 user-ttl
}