package com.demo.dto.user;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户信用信息 DTO
 */
@Data
public class UserCreditDTO {
    /** 用户 ID。 */
    private Long userId;
    /** 字段：creditScore。 */
    private Integer creditScore;
    private String creditLevel;  // 信用等级 dbValue（如 "lv3"）
    /** 字段：creditUpdatedAt。 */
    private LocalDateTime creditUpdatedAt;
}

