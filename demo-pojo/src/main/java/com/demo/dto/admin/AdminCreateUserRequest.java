package com.demo.dto.admin;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;

/**
 * 管理员手动建档请求参数。
 */
@Data
public class AdminCreateUserRequest {

    /** 用户展示昵称。 */
    @NotBlank(message = "昵称不能为空")
    @Size(max = 20, message = "昵称长度不能超过 20 个字符")
    private String name;

    /** 用户手机号（11位大陆手机号）。 */
    @NotBlank(message = "手机号不能为空")
    @Pattern(regexp = "^1\\d{10}$", message = "手机号格式不正确")
    private String phone;

    /** 初始业务角色。 */
    @NotBlank(message = "角色不能为空")
    private String role;
}
