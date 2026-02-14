package com.demo.service;

/**
 * 退款任务处理服务。
 *
 * 职责：
 * - 扫描并执行可运行退款任务（PENDING/FAILED）
 * - 将任务推进到 SUCCESS 或继续 FAILED 重试
 */
public interface OrderRefundTaskService {

    /**
     * 处理可运行退款任务。
     *
     * @param limit 本批处理上限
     * @return 成功处理数量
     */
    int processRunnableTasks(int limit);
}
