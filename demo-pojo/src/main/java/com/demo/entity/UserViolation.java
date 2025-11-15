package com.demo.entity;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class UserViolation {
    private Long id;
    private Long userId;
    private String violationType;         // 违规类型：false_delivery, fake_product等
    private String description;           // 违规描述
    private String evidence;              // 证据附件
    private String punishment;            // 处理结果
    private LocalDateTime recordTime;     // 记录时间
    private String status;                // active-有效, revoked-已撤销
    private LocalDateTime createTime;
}