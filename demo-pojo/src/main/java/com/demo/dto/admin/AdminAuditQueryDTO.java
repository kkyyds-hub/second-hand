package com.demo.dto.admin;

import lombok.Data;

/**
 * 纠纷与违规页查询参数。
 * 用于管理端按关键字、类型、状态、风险等级筛选聚合工单。
 */
@Data
public class AdminAuditQueryDTO {

    /** 关键字（支持匹配工单编号、标题、关联对象）。 */
    private String keyword;

    /** 工单类型（DISPUTE / REPORT / RISK）。 */
    private String type;

    /** 页面状态（PENDING / PROCESSING / CLOSED）。 */
    private String status;

    /** 风险等级（HIGH / MEDIUM / LOW）。 */
    private String riskLevel;

    /** 限制返回数量，避免一次取过多数据。 */
    private Integer limit;
}
