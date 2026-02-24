package com.demo.dto.mq;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 商品强制下架事件载荷。
 */
@Data
public class ProductForceOffShelfPayload {
    /** 商品 ID */
    private Long productId;
    /** 卖家（通知接收人） */
    private Long ownerId;
    /** 操作管理员 ID */
    private Long operatorId;
    /** 变更前状态 */
    private String beforeStatus;
    /** 变更后状态 */
    private String afterStatus;
    /** 原因码 */
    private String reasonCode;
    /** 原因文本 */
    private String reasonText;
    /** 关联举报工单号（可空） */
    private String reportTicketNo;
    /** 事件发生时间 */
    private LocalDateTime forcedAt;
}
