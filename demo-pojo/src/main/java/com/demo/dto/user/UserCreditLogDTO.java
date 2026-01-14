package com.demo.dto.user;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户信用分变更流水 DTO
 */
@Data
public class UserCreditLogDTO {
    private Long id;
    private Long userId;
    private Integer delta;
    private String reasonType;  // 变更原因类型 dbValue
    private Long refId;
    private Integer scoreBefore;
    private Integer scoreAfter;
    private String reasonNote;
    private LocalDateTime createTime;
}

