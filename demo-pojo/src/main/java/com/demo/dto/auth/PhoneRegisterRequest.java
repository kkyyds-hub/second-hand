package com.demo.dto.auth;

import lombok.Data;

import javax.validation.constraints.NotBlank;

/**
 * 手机号注册请求
 */
@Data
public class PhoneRegisterRequest {

    @NotBlank(message = "手机号不能为空")
    private String mobile;

    @NotBlank(message = "验证码不能为空")
    private String code;

    @NotBlank(message = "密码不能为空")
    private String password;
}
