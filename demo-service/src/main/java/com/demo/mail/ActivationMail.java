package com.demo.mail;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 激活邮件载荷。
 */
@Data
@Builder
public class ActivationMail {

    /**
     * 目标用户 ID。
     */
    private Long userId;

    /**
     * 目标邮箱。
     */
    private String email;

    /**
     * 邮件主题。
     */
    private String subject;

    /**
     * 邮件正文。
     */
    private String content;

    /**
     * 激活 token。
     */
    private String token;

    /**
     * 激活链接。
     */
    private String activationUrl;

    /**
     * 失效时间。
     */
    private LocalDateTime expireAt;
}
