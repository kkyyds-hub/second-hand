package com.demo.dto.auth;

import lombok.Data;

import javax.validation.constraints.NotBlank;

/**
 * 第三方登录请求
 */
@Data
public class ThirdPartyLoginRequest {

    /**
     * 平台类型，例如 wechat、qq、carrier
     */
    @NotBlank(message = "第三方平台类型不能为空")
    private String provider;

    /**
     * 第三方授权码/凭证
     */
    @NotBlank(message = "授权码不能为空")
    private String authorizationCode;

    /**
     * 第三方返回的唯一标识（可选，优先使用）
     */
    private String externalId;
}