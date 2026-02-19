package com.demo.controller;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 开发环境 Token 生成辅助接口。
 */
@RestController
@RequestMapping("/dev/token")
@Profile("dev")
public class Jwtcontroller {

    /**
     * 生成测试用 JWT（仅 dev 环境可用）。
     */
    @GetMapping("/safe-token")
    public String generateToken() {
        try {
            // 固定示例声明，便于本地联调管理端鉴权流程。
            Map<String, Object> claims = new LinkedHashMap<>();
            claims.put("empId", 1L);
            claims.put("username", "admin");

            String secretKey = "second hand";
            byte[] keyBytes = Arrays.copyOf(
                    secretKey.getBytes(StandardCharsets.UTF_8),
                    32
            );

            return Jwts.builder()
                    .setClaims(claims)
                    .signWith(SignatureAlgorithm.HS256, keyBytes)
                    .setExpiration(new Date(System.currentTimeMillis() + 7_200_000))
                    .compact();
        } catch (Exception e) {
            return "Safe Error: " + e.getClass().getSimpleName() + " - " + e.getMessage();
        }
    }
}
