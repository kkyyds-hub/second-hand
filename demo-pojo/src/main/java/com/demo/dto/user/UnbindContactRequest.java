package com.demo.dto.user;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;

@Data
/**
 * UnbindContactRequest 业务组件。
 */
public class UnbindContactRequest {

    /**
     * 验证渠道：phone / email
     */
    @NotBlank(message = "验证渠道不能为空")
    @Pattern(regexp = "(?i)^(phone|email)$", message = "验证渠道仅支持phone或email")
    private String verifyChannel;

    /**
     * 验证码
     */
    @NotBlank(message = "验证码不能为空")
    private String verifyCode;

    /**
     * 当前密码（可选）
     */
    private String currentPassword;
}
