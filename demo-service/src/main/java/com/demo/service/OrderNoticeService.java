package com.demo.service;

import com.demo.entity.Order;
import com.demo.entity.OrderRefundTask;

/**
 * 订单系统通知服务。
 *
 * 说明：
 * - 该服务用于写入“系统通知型”站内消息，不走用户会话发送约束。
 * - 典型场景：超时取消、退款结果通知等。
 */
public interface OrderNoticeService {

    /**
     * 通知买卖双方：订单因超时未发货被系统取消。
     */
    void notifyShipTimeoutCancelled(Order order);

    /**
     * 通知买卖双方：退款任务处理成功。
     */
    void notifyRefundSuccess(Order order, OrderRefundTask refundTask);

    /**
     * 通知卖家：订单临近“发货超时”。
     *
     * @param order       订单基础信息
     * @param level       提醒档位（H24/H6/H1）
     * @param remaining   动态剩余时间文案
     * @param clientMsgId 消息幂等键（建议包含 orderId + level + date）
     */
    void notifyShipReminder(Order order, String level, String remaining, String clientMsgId);
}
