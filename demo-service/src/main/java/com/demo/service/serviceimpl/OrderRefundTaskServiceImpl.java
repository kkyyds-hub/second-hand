package com.demo.service.serviceimpl;

import com.demo.entity.OrderRefundTask;
import com.demo.mapper.OrderRefundTaskMapper;
import com.demo.service.OrderRefundTaskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

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
    private final OrderRefundTaskProcessor refundTaskProcessor;

    @Override
    public int processRunnableTasks(int limit) {
        List<OrderRefundTask> tasks = refundTaskMapper.listRunnable(limit);
        if (tasks == null || tasks.isEmpty()) {
            return 0;
        }

        int success = 0;
        for (OrderRefundTask task : tasks) {
            try {
                if (refundTaskProcessor.processOne(task)) {
                    success++;
                }
            } catch (Exception ex) {
                log.error("process refund task failed, taskId={}, orderId={}", task.getId(), task.getOrderId(), ex);
            }
        }
        return success;
    }
}
