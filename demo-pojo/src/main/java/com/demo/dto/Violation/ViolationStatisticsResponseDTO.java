package com.demo.dto.Violation;

import lombok.Data;

import java.util.List;

/**
 * 违规统计响应 DTO。
 */
@Data
public class ViolationStatisticsResponseDTO {

    /** 各违规类型分布。 */
    private List<ViolationTypeDistribution> violationTypeDistribution;

    /**
     * 违规类型分布项。
     */
    @Data
    public static class ViolationTypeDistribution {
        /** 违规类型编码。 */
        private String violationType;
        /** 违规类型描述。 */
        private String violationTypeDesc;
        /** 数量。 */
        private long count;
        /** 占比（0-100）。 */
        private double percentage;
    }
}
