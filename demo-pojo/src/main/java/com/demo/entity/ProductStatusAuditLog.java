package com.demo.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 商品状态审计日志实体。
 * 对应表：product_status_audit_log
 */
@Data
public class ProductStatusAuditLog {
    /** 主键ID。 */
    private Long id;
    /** 商品ID。 */
    private Long productId;
    /** 动作编码（建议使用 ProductActionType.code）。 */
    private String action;
    /** 操作人ID。 */
    private Long operatorId;
    /** 操作人角色（admin/seller/system）。 */
    private String operatorRole;
    /** 迁移前状态。 */
    private String beforeStatus;
    /** 迁移后状态。 */
    private String afterStatus;
    /** 原因编码。 */
    private String reasonCode;
    /** 原因文本。 */
    private String reasonText;
    /** 扩展字段JSON（可存 reportTicketNo 等）。 */
    private String extraJson;
    /** 创建时间。 */
    private LocalDateTime createTime;
}

