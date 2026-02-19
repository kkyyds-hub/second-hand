package com.demo.job;

import com.demo.mapper.OrderMapper;
import com.demo.service.OrderTimeoutService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 订单超时关闭定时任务。
 * 按配置扫描超时未支付订单并触发关闭流程。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderTimeoutJob {

    private final OrderMapper orderMapper;
    private final OrderTimeoutService orderTimeoutService;

    /** 超时阈值（分钟）。 */
    @Value("${order.timeout.pending-minutes:15}")
    private int timeoutMinutes;

    /** 单次处理批量大小。 */
    @Value("${order.timeout.batch-size:200}")
    private int batchSize;

    /**
     * 按固定间隔执行超时关单任务。
     */
    @Scheduled(fixedDelayString = "${order.timeout.fixed-delay-ms:60000}")
    public void closeTimeoutPendingOrders() {
        LocalDateTime deadline = LocalDateTime.now().minusMinutes(timeoutMinutes);
        log.info("timeout close start, deadline={}, batchSize={}, timeoutMinutes={}", deadline, batchSize, timeoutMinutes);

        List<Long> orderIds = orderMapper.findTimeoutPendingOrderIds(deadline, batchSize);
        if (orderIds == null || orderIds.isEmpty()) {
            log.debug("timeout close no pending orders, deadline={}", deadline);
            return;
        }

        int closed = 0;
        for (Long orderId : orderIds) {
            try {
                boolean ok = orderTimeoutService.closeTimeoutOrderAndRelease(orderId, deadline);
                if (ok) {
                    closed++;
                }
            } catch (Exception e) {
                log.error("timeout close failed, orderId={}", orderId, e);
            }
        }
        log.info("timeout close finished, size={}, closed={}", orderIds.size(), closed);
    }
}
