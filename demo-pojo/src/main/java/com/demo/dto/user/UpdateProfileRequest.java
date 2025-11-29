package com.demo.dto.user;

import lombok.Data;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

/**
 * 请求更新用户基本资料（昵称/头像/简介）
 */
@Data
public class UpdateProfileRequest {

    @NotNull(message = "用户ID不能为空")
    private Long userId;

    @Size(min = 1, max = 20, message = "昵称长度需在1-20个字符内")
    private String nickname;

    /**
     * 头像地址，建议前端处理格式校验和上传
     */
    private String avatar;

    @Size(max = 150, message = "简介不能超过150个字符")
    private String bio;
}