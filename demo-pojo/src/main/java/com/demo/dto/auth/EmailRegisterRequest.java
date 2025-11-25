package com.demo.dto.auth;

import lombok.Data;

import javax.validation.constraints.Email;
import javax.validation.constraints.NotBlank;

/**
 * 邮箱注册请求
 */
@Data
public class EmailRegisterRequest {

    @Email(message = "邮箱格式不正确")
    @NotBlank(message = "邮箱不能为空")
    private String email;

    @NotBlank(message = "密码不能为空")
    private String password;
}
