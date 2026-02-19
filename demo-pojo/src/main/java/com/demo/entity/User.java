package com.demo.entity;

import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

/**
 * 用户实体。
 */
@Data
public class User {
    /** 用户 ID。 */
    private Long id;
    /** 用户名。 */
    private String username;
    /** 登录密码（加密后存储）。 */
    private String password;
    /** 手机号。 */
    private String mobile;
    /** 昵称。 */
    private String nickname;
    /** 个人简介。 */
    private String bio;
    /** 邮箱。 */
    private String email;
    /** 头像地址。 */
    private String avatar;
    /** 信用分（默认 100）。 */
    private Integer creditScore = 100;
    /** 信用等级（默认 lv3）。 */
    private String creditLevel = "lv3";
    /** 信用分最近更新时间。 */
    private LocalDateTime creditUpdatedAt;
    /** 账号状态（如 active/banned）。 */
    private String status = "active";
    /** 是否卖家标记。 */
    private Integer isSeller;

    /** 创建时间。 */
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
    /** 更新时间。 */
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;
}
