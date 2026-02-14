package com.demo.service.serviceimpl;

import com.demo.entity.OrderShipTimeoutTask;
import com.demo.mapper.OrderShipTimeoutTaskMapper;
import com.demo.service.OrderShipTimeoutService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.util.List;

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

    private final OrderShipTimeoutTaskMapper taskMapper;
    private final OrderShipTimeoutTaskProcessor taskProcessor;

    @Override
    public int processDueTasks(int limit) {
        List<OrderShipTimeoutTask> tasks = taskMapper.listDuePending(limit);
        if (tasks == null || tasks.isEmpty()) {
            return 0;
        }

        int successClosed = 0;
        for (OrderShipTimeoutTask task : tasks) {
            try {
                // 通过独立 Processor Bean 调用事务方法，确保 @Transactional 生效
                boolean closed = taskProcessor.processOne(task);
                if (closed) {
                    successClosed++;
                }
            } catch (Exception ex) {
                // 单条异常不应中断整批任务
                log.error("process ship-timeout task failed, taskId={}, orderId={}",
                        task.getId(), task.getOrderId(), ex);
            }
        }
        return successClosed;
    }
}
