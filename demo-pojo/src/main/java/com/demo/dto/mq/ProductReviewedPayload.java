package com.demo.dto.mq;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 商品审核完成事件载荷。
 *
 * reviewAction 口径：
 * - approve：审核通过
 * - reject：审核驳回
 */
@Data
public class ProductReviewedPayload {
    /** 商品 ID */
    private Long productId;
    /** 卖家（通知接收人） */
    private Long ownerId;
    /** 审核动作：approve/reject */
    private String reviewAction;
    /** 变更前状态 */
    private String beforeStatus;
    /** 变更后状态 */
    private String afterStatus;
    /** 驳回原因（通过时为 null） */
    private String reasonText;
    /** 事件发生时间 */
    private LocalDateTime reviewedAt;
}
