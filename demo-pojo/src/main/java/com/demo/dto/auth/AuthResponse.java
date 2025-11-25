package com.demo.dto.auth;

import com.demo.vo.UserVO;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 登录/注册后的响应
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AuthResponse {
    /**
     * JWT令牌
     */
    private String token;

    /**
     * 用户信息
     */
    private UserVO user;
}
