package com.demo.dto.admin;

import lombok.Data;

/**
 * 违规统计项 DTO（管理端）。
 */
@Data
public class ViolationStatisticsDTO {

    /** 违规类型编码。 */
    private String violationType;

    /** 违规数量。 */
    private Long cnt;

    /** 关联扣分总和（可为空）。 */
    private Integer creditSum;
}
