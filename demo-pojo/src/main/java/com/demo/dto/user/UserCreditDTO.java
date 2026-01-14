package com.demo.dto.user;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户信用信息 DTO
 */
@Data
public class UserCreditDTO {
    private Long userId;
    private Integer creditScore;
    private String creditLevel;  // 信用等级 dbValue（如 "lv3"）
    private LocalDateTime creditUpdatedAt;
}

