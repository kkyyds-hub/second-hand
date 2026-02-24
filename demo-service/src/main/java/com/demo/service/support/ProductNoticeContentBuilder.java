package com.demo.service.support;

import com.demo.dto.mq.ProductForceOffShelfPayload;
import com.demo.dto.mq.ProductReportResolvedPayload;
import com.demo.dto.mq.ProductReviewedPayload;
import org.springframework.stereotype.Component;

/**
 * Day16 - 商品治理站内信文案构建器。
 *
 * 设计目标：
 * 1) 把通知文案集中管理，避免散落在多个消费者里各写一套。
 * 2) 后续若要统一改措辞，只改这里即可。
 * 3) 文案仅依赖事件 payload，保证消费逻辑与文案解耦。
 */
@Component
public class ProductNoticeContentBuilder {

    /**
     * 构建“商品审核完成”通知文案。
     */
    public String buildReviewedNotice(ProductReviewedPayload payload) {
        String action = safeLower(payload.getReviewAction());
        if ("approve".equals(action)) {
            return "你的商品（ID=" + payload.getProductId() + "）审核通过，已进入在售状态。";
        }
        if ("reject".equals(action)) {
            String reason = isBlank(payload.getReasonText())
                    ? "请完善商品信息后重新提审。"
                    : ("驳回原因：" + payload.getReasonText().trim());
            return "你的商品（ID=" + payload.getProductId() + "）审核驳回。" + reason;
        }
        return "你的商品（ID=" + payload.getProductId() + "）审核状态已更新，请进入商品管理页查看详情。";
    }

    /**
     * 构建“商品强制下架”通知文案。
     */
    public String buildForceOffShelfNotice(ProductForceOffShelfPayload payload) {
        String reason = isBlank(payload.getReasonText())
                ? "平台巡检命中风险规则"
                : payload.getReasonText().trim();
        return "你的商品（ID=" + payload.getProductId() + "）已被管理员强制下架。原因：" + reason + "。";
    }

    /**
     * 构建“举报处理结果”通知文案。
     */
    public String buildReportResolvedNotice(ProductReportResolvedPayload payload) {
        String action = safeLower(payload.getResolveAction());
        String remark = isBlank(payload.getRemark()) ? null : payload.getRemark().trim();

        if ("force_off_shelf".equals(action)) {
            return remark == null
                    ? ("你举报的商品（ID=" + payload.getProductId() + "）核查成立，平台已执行下架处理。")
                    : ("你举报的商品（ID=" + payload.getProductId() + "）核查成立，平台已执行下架处理。备注：" + remark);
        }
        if ("dismiss".equals(action)) {
            return remark == null
                    ? ("你举报的商品（ID=" + payload.getProductId() + "）已处理：举报不成立。")
                    : ("你举报的商品（ID=" + payload.getProductId() + "）已处理：举报不成立。备注：" + remark);
        }
        return "你提交的举报工单（" + payload.getTicketNo() + "）已处理，请查看详情。";
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String safeLower(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }
}
