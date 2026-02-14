package com.demo.service;

import com.demo.entity.Order;
import com.demo.entity.OrderRefundTask;

/**
 * 退款记账编排服务。
 *
 * 职责：
 * - 在退款任务成功前后，处理钱包侧记账逻辑
 * - 作为统一扩展点，后续可接入更完整的资金台账能力
 */
public interface OrderRefundAccountingService {

    /**
     * 执行退款记账（编排入口）。
     *
     * @param order 订单
     * @param refundTask 退款任务
     */
    void recordRefund(Order order, OrderRefundTask refundTask);
}

