package com.demo.dto.auth;

import lombok.Data;

import javax.validation.constraints.Email;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

/**
 * 邮箱注册请求
 */
@Data
public class EmailRegisterRequest {

    @NotBlank(message = "邮箱不能为空")
    @Email(message = "邮箱格式不正确")
    private String email;

    @NotBlank(message = "邮箱验证码不能为空")
    private String emailCode;

    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 32, message = "密码长度需在6-32位之间")
    private String password;

    @NotBlank(message = "昵称不能为空")
    @Size(min = 1, max = 20, message = "昵称长度需在1-20个字符内")
    private String nickname;
}