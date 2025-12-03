package com.demo.dto.user;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

/**
 * 用户修改个人资料请求
 *
 * 说明：
 * - 不再包含 userId 字段，当前用户由 BaseContext 决定
 * - Controller 需要配合 @Valid 使用，才能触发这些校验注解
 */
@Data
public class UpdateProfileRequest {

    /**
     * 昵称
     */
    @NotBlank(message = "昵称不能为空")
    @Size(min = 1, max = 20, message = "昵称长度需在1-20个字符内")
    private String nickname;

    /**
     * 头像地址
     * 可以为空（用户不一定每次都改头像），如果有值则在 Service 里做 URL / 后缀校验
     */
    @Size(max = 255, message = "头像地址长度不能超过255个字符")
    private String avatar;

    /**
     * 个性签名 / 简介
     */
    @Size(max = 150, message = "简介不能超过150个字符")
    private String bio;
}
