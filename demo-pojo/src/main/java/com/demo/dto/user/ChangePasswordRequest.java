package com.demo.dto.user;

import lombok.Data;

import javax.validation.constraints.NotBlank;

/**
 * 请求体：修改密码
 */
@Data
public class ChangePasswordRequest {

    /**
     * 当前密码，可选，当不提供验证码验证时必须提供
     */
    private String oldPassword;

    /**
     * 新密码
     */
    @NotBlank(message = "新密码不能为空")
    private String newPassword;

    /**
     * 验证渠道，phone 或 email，可选
     */
    private String verifyChannel;

    /**
     * 验证码
     */
    private String code;
}