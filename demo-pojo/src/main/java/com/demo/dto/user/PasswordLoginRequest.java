package com.demo.dto.user;

import lombok.Data;

import javax.validation.constraints.NotBlank;

/**
 * 密码登录请求
 * 支持用 手机号 或 邮箱 作为登录标识
 */
@Data
public class PasswordLoginRequest {

    /**
     * 登录标识，可以是手机号或邮箱
     */
    @NotBlank(message = "登录账号不能为空")
    private String loginId;

    /**
     * 登录密码（明文）
     */
    @NotBlank(message = "密码不能为空")
    private String password;
}
