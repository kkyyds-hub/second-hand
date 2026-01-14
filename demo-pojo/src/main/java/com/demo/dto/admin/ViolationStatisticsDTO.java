package com.demo.dto.admin;

import lombok.Data;

@Data
public class ViolationStatisticsDTO {
    private String violationType; // 违规类型
    private Long cnt;             // 数量
    private Integer creditSum;    // 扣分合计（可选）
}
