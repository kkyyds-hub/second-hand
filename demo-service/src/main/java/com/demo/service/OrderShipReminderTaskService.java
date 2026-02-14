package com.demo.service;

import java.time.LocalDateTime;

/**
 * 发货提醒任务服务。
 *
 * 职责：
 * 1) 支付成功时预生成 H24/H6/H1 三档提醒任务
 * 2) 周期扫描并执行到期任务，推进到 SUCCESS/FAILED/CANCELLED
 */
public interface OrderShipReminderTaskService {

    /**
     * 支付成功后，预生成 3 档提醒任务（幂等）。
     *
     * @param orderId  订单ID
     * @param sellerId 卖家ID（提醒接收人）
     * @param payTime  支付时间（deadline = payTime + 48h）
     */
    void createReminderTasksForPaidOrder(Long orderId, Long sellerId, LocalDateTime payTime);

    /**
     * 处理到期提醒任务。
     *
     * @param limit 本轮上限
     * @return 成功发送数量
     */
    int processDueTasks(int limit);
}
