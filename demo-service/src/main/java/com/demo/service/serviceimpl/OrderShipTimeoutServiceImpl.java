package com.demo.service.serviceimpl;

import com.demo.entity.Order;
import com.demo.entity.OrderShipTimeoutTask;
import com.demo.mapper.OrderMapper;
import com.demo.mapper.OrderShipTimeoutTaskMapper;
import com.demo.service.OrderShipTimeoutService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Step6-3：发货超时处理实现
 *
 * 处理策略：
 * 1) 从任务表拉取到期任务（PENDING + deadline_time<=now + next_retry_time可执行）
 * 2) 逐条执行，失败不影响整批（单条失败走重试）
 * 3) 订单状态流转严格依赖“条件更新 rows=1”
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderShipTimeoutServiceImpl implements OrderShipTimeoutService {

    private final OrderMapper orderMapper;
    private final OrderShipTimeoutTaskMapper taskMapper;
    private final OrderShipTimeoutTaskProcessor taskProcessor;

    /**
     * 处理对应业务流程。
     */
    @Override
    public int processDueTasks(int limit) {
        List<OrderShipTimeoutTask> tasks = taskMapper.listDuePending(limit);
        if (tasks == null || tasks.isEmpty()) {
            return 0;
        }

        // P3-S4：批量预加载订单，避免循环内固定 selectById 造成 N+1
        Map<Long, Order> orderMap = loadOrderMap(tasks);

        int successClosed = 0;
        for (OrderShipTimeoutTask task : tasks) {
            try {
                // 通过独立 Processor Bean 调用事务方法，确保 @Transactional 生效
                Order preloadedOrder = orderMap.get(task.getOrderId());
                boolean closed = taskProcessor.processOne(task, preloadedOrder);
                if (closed) {
                    successClosed++;
                }
            } catch (Exception ex) {
                // 单条异常不应中断整批任务
                log.error("处理发货超时任务失败：taskId={}, orderId={}",
                        task.getId(), task.getOrderId(), ex);
            }
        }
        return successClosed;
    }

    /**
     * 批量加载任务关联订单并组装映射，供循环内快速读取。
     */
    private Map<Long, Order> loadOrderMap(List<OrderShipTimeoutTask> tasks) {
        Set<Long> orderIds = tasks.stream()
                .map(OrderShipTimeoutTask::getOrderId)
                .filter(id -> id != null && id > 0)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (orderIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return orderMapper.selectOrderBasicByIds(List.copyOf(orderIds)).stream()
                .collect(Collectors.toMap(Order::getId, order -> order));
    }
}
