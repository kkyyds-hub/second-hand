package com.demo.dto.auth;

import lombok.Data;

import javax.validation.constraints.NotBlank;

/**
 * 邮箱激活请求
 */
@Data
public class EmailActivationRequest {

    @NotBlank(message = "激活令牌不能为空")
    private String token;
}