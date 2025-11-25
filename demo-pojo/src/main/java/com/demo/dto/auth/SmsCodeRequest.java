package com.demo.dto.auth;

import lombok.Data;

import javax.validation.constraints.NotBlank;

/**
 * 请求发送短信验证码
 */
@Data
public class SmsCodeRequest {

    /**
     * 目标手机号
     */
    @NotBlank(message = "手机号不能为空")
    private String mobile;
}
