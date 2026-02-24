package com.demo.service.serviceimpl;

import com.demo.entity.Order;
import com.demo.entity.OrderRefundTask;
import com.demo.mapper.OrderMapper;
import com.demo.mapper.OrderRefundTaskMapper;
import com.demo.service.OrderRefundTaskService;
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
 * 退款任务批处理服务实现。
 *
 * 说明：
 * - 该类负责“批处理调度”，不负责事务细节。
 * - 单条任务事务由 OrderRefundTaskProcessor 承担。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderRefundTaskServiceImpl implements OrderRefundTaskService {

    private final OrderRefundTaskMapper refundTaskMapper;
    private final OrderMapper orderMapper;
    private final OrderRefundTaskProcessor refundTaskProcessor;

    /**
     * 拉取可执行退款任务并逐条处理。
     */
    @Override
    public int processRunnableTasks(int limit) {
        List<OrderRefundTask> tasks = refundTaskMapper.listRunnable(limit);
        if (tasks == null || tasks.isEmpty()) {
            return 0;
        }

        // P3-S4：批量预加载订单，避免循环内固定 selectById 形成 N+1
        Map<Long, Order> orderMap = loadOrderMap(tasks);

        int success = 0;
        for (OrderRefundTask task : tasks) {
            try {
                Order preloadedOrder = orderMap.get(task.getOrderId());
                if (refundTaskProcessor.processOne(task, preloadedOrder)) {
                    success++;
                }
            } catch (Exception ex) {
                log.error("处理退款任务失败：taskId={}, orderId={}", task.getId(), task.getOrderId(), ex);
            }
        }
        return success;
    }

    /**
     * 预加载本批任务关联订单：
     * - 先去重 orderId
     * - 一次性 IN 查询
     * - 组装为 orderId -> Order 映射供循环内 O(1) 命中
     */
    private Map<Long, Order> loadOrderMap(List<OrderRefundTask> tasks) {
        Set<Long> orderIds = tasks.stream()
                .map(OrderRefundTask::getOrderId)
                .filter(id -> id != null && id > 0)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (orderIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return orderMapper.selectOrderBasicByIds(List.copyOf(orderIds)).stream()
                .collect(Collectors.toMap(Order::getId, order -> order));
    }
}
