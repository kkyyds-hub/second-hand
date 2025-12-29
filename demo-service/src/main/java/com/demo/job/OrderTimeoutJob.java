package com.demo.job;

import com.demo.mapper.OrderMapper;
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

    // 每分钟执行一次：关闭超时未支付订单
    @Scheduled(fixedDelay = 60_000)
    public void closeTimeoutPendingOrders() {
        // 1) 计算超时时间点：现在往前 15 分钟
        LocalDateTime deadline = LocalDateTime.now().minusMinutes(15);

        // 2) 查询超时的 pending 订单ID列表
        List<Long> orderIds = orderMapper.findTimeoutPendingOrderIds(deadline, 200); // 200 是一轮最多处理数量（可调）
        if (orderIds == null || orderIds.isEmpty()) {
            return;
        }

        int closed = 0;
        for (Long orderId : orderIds) {
            // 3) 条件更新：仍是 pending 才能关闭
            int rows = orderMapper.closeTimeoutOrder(orderId);
            if (rows == 1) {
                // 4) 关闭成功才释放商品
                int released = orderMapper.releaseProductsForOrder(orderId);
                closed++;
                log.info("timeout close orderId={}, released={}", orderId, released);
            }
        }
        log.info("timeout close finished, size={}, closed={}", orderIds.size(), closed);
    }
}