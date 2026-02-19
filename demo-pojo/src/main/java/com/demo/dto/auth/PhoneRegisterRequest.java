package com.demo.dto.auth;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;

/**
 * 手机号注册请求
 */
@Data
public class PhoneRegisterRequest {

    @NotBlank(message = "手机号不能为空")
    // 这里的正则你可以按自己项目调整
    @Pattern(regexp = "^1\\d{10}$", message = "手机号格式不正确")
    private String mobile;

    @NotBlank(message = "短信验证码不能为空")
    /** 字段：smsCode。 */
    private String smsCode;

    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 32, message = "密码长度需在6-32位之间")
    /** 字段：password。 */
    private String password;

    @NotBlank(message = "昵称不能为空")
    @Size(min = 1, max = 20, message = "昵称长度需在1-20个字符内")
    /** 字段：nickname。 */
    private String nickname;
}
