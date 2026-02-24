package com.demo.service;

import com.demo.entity.Product;
import com.demo.entity.ProductReportTicket;

/**
 * Day16 - 商品治理事件服务。
 *
 * 作用：
 * 1) 在主事务内把商品治理事件写入 Outbox。
 * 2) 屏蔽 EventMessage/Outbox 组装细节，避免业务服务重复拼装消息。
 */
public interface ProductGovernanceEventService {

    /**
     * 发布“商品审核完成”事件（通过/驳回）。
     */
    void publishProductReviewed(Product product,
                                String reviewAction,
                                String beforeStatus,
                                String afterStatus,
                                String reasonText);

    /**
     * 发布“商品强制下架”事件。
     */
    void publishProductForceOffShelf(Product product,
                                     Long operatorId,
                                     String beforeStatus,
                                     String afterStatus,
                                     String reasonCode,
                                     String reasonText,
                                     String reportTicketNo);

    /**
     * 发布“举报工单已处理”事件。
     */
    void publishProductReportResolved(ProductReportTicket ticket,
                                      Long resolverId,
                                      String resolveAction,
                                      String targetStatus,
                                      String remark);
}
