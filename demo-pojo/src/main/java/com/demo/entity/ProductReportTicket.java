package com.demo.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 商品举报工单实体。
 * 对应表：product_report_ticket
 */
@Data
public class ProductReportTicket {
    /** 主键ID。 */
    private Long id;
    /** 工单号（唯一）。 */
    private String ticketNo;
    /** 商品ID。 */
    private Long productId;
    /** 举报人ID。 */
    private Long reporterId;
    /** 举报类型。 */
    private String reportType;
    /** 举报描述。 */
    private String description;
    /** 举报证据（JSON字符串）。 */
    private String evidenceUrls;
    /** 工单状态。 */
    private String status;
    /** 处理人ID。 */
    private Long resolverId;
    /** 处理动作。 */
    private String resolveAction;
    /** 处理备注。 */
    private String resolveRemark;
    /** 创建时间。 */
    private LocalDateTime createTime;
    /** 更新时间。 */
    private LocalDateTime updateTime;
}

