package com.demo.utils;

import io.jsonwebtoken.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;

@Slf4j  // 添加日志
public class JwtUtil {

    /**
     * 生成JWT令牌 - 安全版本
     */
    public static String createJWT(String secretKey, long ttlMillis, Map<String, Object> claims) {
        try {
            //  添加空值检查
            if (secretKey == null || secretKey.trim().isEmpty()) {
                log.error("生成JWT失败: secretKey为空");
                throw new IllegalArgumentException("secretKey不能为空");
            }

            if (claims == null) {
                log.error("生成JWT失败: claims为空");
                throw new IllegalArgumentException("claims不能为空");
            }

            // 设置过期时间
            long expMillis = System.currentTimeMillis() + ttlMillis;
            Date exp = new Date(expMillis);

            return Jwts.builder()
                    .setClaims(claims)
                    .signWith(SignatureAlgorithm.HS256, secretKey.getBytes(StandardCharsets.UTF_8))
                    .setExpiration(exp)
                    .compact();

        } catch (Exception e) {
            log.error("生成JWT失败: {}", e.getMessage());
            throw new RuntimeException("生成JWT令牌失败", e);
        }
    }

    /**
     * 解析JWT令牌 - 安全版本
     */
    public static Claims parseJWT(String secretKey, String token) {
        try {
            // 添加详细的空值检查
            if (!StringUtils.hasText(secretKey)) {
                log.error("解析JWT失败: secretKey为空");
                return null;
            }

            if (!StringUtils.hasText(token)) {
                log.error("解析JWT失败: token为空");
                return null;
            }

            // 安全解析
            return Jwts.parser()
                    .setSigningKey(secretKey.getBytes(StandardCharsets.UTF_8))
                    .parseClaimsJws(token)
                    .getBody();

        } catch (ExpiredJwtException e) {
            log.warn("JWT令牌已过期: {}", e.getMessage());
            return null;
        } catch (UnsupportedJwtException e) {
            log.warn("不支持的JWT格式: {}", e.getMessage());
            return null;
        } catch (MalformedJwtException e) {
            log.warn("JWT令牌格式错误: {}", e.getMessage());
            return null;
        } catch (SignatureException e) {
            log.warn("JWT签名验证失败: {}", e.getMessage());
            return null;
        } catch (IllegalArgumentException e) {
            log.warn("JWT参数错误: {}", e.getMessage());
            return null;
        } catch (Exception e) {
            log.error("解析JWT失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 验证JWT令牌是否有效 - 新增方法
     */
    public static boolean validateJWT(String secretKey, String token) {
        try {
            Claims claims = parseJWT(secretKey, token);
            if (claims == null) {
                return false;
            }

            // 检查过期时间
            return !claims.getExpiration().before(new Date());
        } catch (Exception e) {
            log.warn("验证JWT失败: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 从令牌中获取指定声明 - 新增方法
     */
    public static <T> T getClaimFromToken(String secretKey, String token, String claimName, Class<T> clazz) {
        try {
            Claims claims = parseJWT(secretKey, token);
            return claims != null ? claims.get(claimName, clazz) : null;
        } catch (Exception e) {
            log.warn("获取声明失败: {}", e.getMessage());
            return null;
        }
    }
}