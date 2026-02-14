package com.demo.job;

import com.demo.service.OrderShipTimeoutService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 发货超时任务扫描 Job。
 *
 * 调度逻辑：
 * - 周期拉取到期可执行的 PENDING 任务
 * - 推进订单 paid -> cancelled(ship_timeout)
 * - 失败任务留在 PENDING 并延迟重试
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderShipTimeoutTaskJob {

    private final OrderShipTimeoutService orderShipTimeoutService;

    @Value("${order.ship-timeout.batch-size:200}")
    private int batchSize;

    @Scheduled(fixedDelayString = "${order.ship-timeout.fixed-delay-ms:60000}")
    public void run() {
        int size = batchSize <= 0 ? 200 : batchSize;
        log.info("ship-timeout task job start, batchSize={}", size);
        int success = orderShipTimeoutService.processDueTasks(size);
        log.info("ship-timeout task job finish, success={}", success);
    }
}
