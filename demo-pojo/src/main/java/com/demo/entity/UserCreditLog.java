package com.demo.entity;

import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

/**
 * 用户信用分变更流水
 */
@Data
public class UserCreditLog {
    private Long id;
    private Long userId;
    private Integer delta;  // 信用分变动值（正数为加分，负数为扣分）
    private String reasonType;  // 变更原因类型（存 dbValue，如 "order_completed"）
    private Long refId;  // 关联业务ID（如订单ID，可为空）
    private Integer scoreBefore;  // 变更前分数
    private Integer scoreAfter;  // 变更后分数
    private String reasonNote;
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;  // 创建时间
}

