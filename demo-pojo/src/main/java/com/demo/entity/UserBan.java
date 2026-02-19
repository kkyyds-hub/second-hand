package com.demo.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户封禁记录
 */
@Data
public class UserBan {

    private Long id;              // 封禁记录 ID
    private Long userId;          // 被封禁用户 ID
    private String banType;       // 封禁类型: TEMP(临时封禁), PERM(永久封禁)
    private String reason;        // 封禁原因
    private String source;        // 来源: ADMIN / AUTO_RISK / SYSTEM
    private LocalDateTime startTime; // 封禁开始时间
    private LocalDateTime endTime;   // 封禁结束时间（永久封禁可为 null）
    private Long createdBy;       // 操作管理员 ID（自动封禁为空）
    private LocalDateTime createTime; // 创建时间
}
