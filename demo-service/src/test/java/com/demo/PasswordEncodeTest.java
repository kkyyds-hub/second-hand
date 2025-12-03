package com.demo;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 用于生成测试用的加密密码（不依赖 Spring 容器）
 */
public class PasswordEncodeTest {

    // 直接手动创建一个密码加密器
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Test
    void generatePassword() {
        // 这里写你希望用户登录时输入的明文密码
        String raw = "123456";

        // 生成加密后的密码
        String encoded = passwordEncoder.encode(raw);

        // 打印到控制台
        System.out.println("raw password = " + raw);
        System.out.println("encoded password = " + encoded);
    }
}
