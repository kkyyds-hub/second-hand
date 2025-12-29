package com.demo.job;

import com.demo.mapper.OrderMapper;
import com.demo.service.OrderTimeoutService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

    // 每分钟执行一次：关闭超时未支付订单
    @Scheduled(fixedDelay = 60_000)
    public void closeTimeoutPendingOrders() {

        // 1) 计算超时时间点：现在往前 15 分钟
        LocalDateTime deadline = LocalDateTime.now().minusMinutes(15);

        // 2) 查询超时的 pending 订单ID列表
        log.info("timeout close start, deadline={}, limit={}", deadline, 200);
        List<Long> orderIds = orderMapper.findTimeoutPendingOrderIds(deadline, 200); // 200 是一轮最多处理数量（可调）
        if (orderIds == null || orderIds.isEmpty()) {
            log.debug("timeout close no pending orders, deadline={}", deadline);
            return;
        }

        int closed = 0;
        for (Long orderId : orderIds) {
            try {
                boolean ok = orderTimeoutService.closeTimeoutOrderAndRelease(orderId, deadline);
                if (ok) closed++;
                log.info("timeout close success, orderId={}, closed={}", orderId, closed);
            } catch (Exception e) {
                log.error("timeout close failed, orderId={}", orderId, e);
            }
        }
        log.info("timeout close finished, size={}, closed={}", orderIds.size(), closed);
    }
}