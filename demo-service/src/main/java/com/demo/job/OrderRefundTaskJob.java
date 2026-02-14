package com.demo.job;

import com.demo.service.OrderRefundTaskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 退款任务扫描 Job。
 *
 * 调度逻辑：
 * - 周期拉取 PENDING/FAILED 且到达重试时间的任务
 * - 逐条处理并推进为 SUCCESS 或继续 FAILED
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderRefundTaskJob {

    private final OrderRefundTaskService orderRefundTaskService;

    @Value("${order.refund.batch-size:200}")
    private int batchSize;

    @Scheduled(fixedDelayString = "${order.refund.fixed-delay-ms:60000}")
    public void run() {
        int size = batchSize <= 0 ? 200 : batchSize;
        log.info("refund-task job start, batchSize={}", size);
        int success = orderRefundTaskService.processRunnableTasks(size);
        log.info("refund-task job finish, success={}", success);
    }
}
