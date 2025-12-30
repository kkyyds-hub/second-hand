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

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderTimeoutJob {

    private final OrderMapper orderMapper;
    private final OrderTimeoutService orderTimeoutService;

    @Value("${order.timeout.pending-minutes:15}")
    private int timeoutMinutes;

    @Value("${order.timeout.batch-size:200}")
    private int batchSize;

    // 每分钟执行一次：关闭超时未支付订单（可配置）
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
                if (ok) closed++;
            } catch (Exception e) {
                log.error("timeout close failed, orderId={}", orderId, e);
            }
        }
        log.info("timeout close finished, size={}, closed={}", orderIds.size(), closed);
    }
}
