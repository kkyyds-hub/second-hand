package com.demo.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ProductViolation {
    private Long id;
    private Long productId; // 商品ID
    private String violationType; // 违规类型
    private String description; // 违规描述
    private String evidenceUrls; // 违规证据URL
    private String punishmentResult; // 处罚结果
    private Integer creditScoreChange; // 信用分变更
    private String status; // 状态（active, inactive等）
    private LocalDateTime recordTime; // 记录时间
}
