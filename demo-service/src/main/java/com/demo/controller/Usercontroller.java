package com.demo.controller;
import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/token")
public class Usercontroller {
    @GetMapping("/safe-token")
    public String getSafeToken() {
        try {
            //  安全的可变Map
            Map<String, Object> claims = new LinkedHashMap<>();
            claims.put("empId", 1L);
            claims.put("username", "admin");

            // 密钥处理（确保长度足够）
            String secretKey = "second hand";
            byte[] keyBytes = Arrays.copyOf(
                    secretKey.getBytes(StandardCharsets.UTF_8),
                    32 // HS256需要32字节
            );

            return Jwts.builder()
                    .setClaims(claims)
                    .signWith(SignatureAlgorithm.HS256, keyBytes)
                    .setExpiration(new Date(System.currentTimeMillis() + 7200000))
                    .compact();

        } catch (Exception e) {
            return "Safe Error: " + e.getClass().getSimpleName() + " - " + e.getMessage();
        }
    }
}