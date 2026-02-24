package com.demo.dto.mq;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 举报工单处理完成事件载荷。
 */
@Data
public class ProductReportResolvedPayload {
    /** 工单号 */
    private String ticketNo;
    /** 商品 ID */
    private Long productId;
    /** 举报人（通知接收人） */
    private Long reporterId;
    /** 处理管理员 ID */
    private Long resolverId;
    /** 处理动作：dismiss/force_off_shelf */
    private String resolveAction;
    /** 工单处理后状态：RESOLVED_VALID/RESOLVED_INVALID */
    private String targetStatus;
    /** 处理备注 */
    private String remark;
    /** 事件发生时间 */
    private LocalDateTime resolvedAt;
}
