package com.demo.dto.user;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Positive;

/**
 * 头像上传票据参数。
 */
@Data
public class AvatarUploadTicketRequest {

    /**
     * 本次上传对应的对象 key。
     */
    @NotBlank(message = "上传 key 不能为空")
    private String key;

    /**
     * 票据过期时间（Unix 秒）。
     */
    @NotNull(message = "上传过期时间不能为空")
    @Positive(message = "上传过期时间不合法")
    private Long expires;

    /**
     * 本地 provider 生成的签名。
     */
    @NotBlank(message = "上传签名不能为空")
    private String signature;
}
