package com.demo.service;

import com.demo.entity.Order;

/**
 * 发货超时处罚编排服务。
 *
 * 职责：
 * - 在“超时关单成功”后，统一处理卖家处罚相关动作
 * - 当前阶段作为扩展点，后续可补充更多处罚策略（扣分、限制发布等）
 */
public interface OrderShipTimeoutPenaltyService {

    /**
     * 对“发货超时被取消”的订单执行处罚动作。
     *
     * @param order 已被超时关单的订单
     */
    void applyPenalty(Order order);
}

