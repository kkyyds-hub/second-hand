package com.demo.dto.Violation;

import lombok.Data;

import java.util.List;

@Data
public class ViolationReportRequest {
    private Long userId;           // 用户ID
    private String violationType;  // 违规类型
    private String orderId;        // 相关订单ID
    private String description;    // 违规描述
    private List<String> evidenceUrls; // 证据链接
    private String punishmentResult;    // 处罚结果
    private Integer punishDuration;     // 处罚时长（天）
}
