package com.demo.service;

/**
 * 已支付待发货超时处理服务
 *
 * 职责：
 * 1) 扫描并处理到期任务（来自 order_ship_timeout_task）
 * 2) 执行订单状态变更：paid -> cancelled(ship_timeout)
 * 3) 在关单成功后释放商品占用
 */
public interface OrderShipTimeoutService {
    /**
     * 处理“到期可执行”的发货超时任务
     *
     * @param limit 本次最多处理条数（批大小）
     * @return 成功关单数量（仅统计真正执行了 paid->cancelled 的记录）
     */
    int processDueTasks(int limit);
}
