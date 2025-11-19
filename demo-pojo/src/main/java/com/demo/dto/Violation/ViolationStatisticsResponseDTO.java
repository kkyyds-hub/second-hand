package com.demo.dto.Violation;

import lombok.Data;

import java.util.List;

@Data
public class ViolationStatisticsResponseDTO {

    private List<ViolationTypeDistribution> violationTypeDistribution;

    @Data
    public static class ViolationTypeDistribution {
        private String violationType;         // 违规类型
        private String violationTypeDesc;     // 违规类型描述
        private long count;                   // 违规数量
        private double percentage;            // 违规百分比
    }
}