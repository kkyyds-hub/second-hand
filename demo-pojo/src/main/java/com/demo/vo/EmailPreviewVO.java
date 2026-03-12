package com.demo.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 激活邮件预览视图对象。
 */
@Data
public class EmailPreviewVO {

    /**
     * 预览记录 ID。
     */
    private String previewId;

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
     * 实际发送 provider。
     */
    private String provider;

    /**
     * 发送时间。
     */
    private LocalDateTime sentAt;

    /**
     * 失效时间。
     */
    private LocalDateTime expireAt;
}
