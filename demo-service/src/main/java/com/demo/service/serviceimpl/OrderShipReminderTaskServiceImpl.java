package com.demo.service.serviceimpl;

import com.demo.entity.OrderShipReminderTask;
import com.demo.mapper.OrderShipReminderTaskMapper;
import com.demo.service.OrderShipReminderTaskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;

/**
 * 发货提醒任务批处理服务实现。
 *
 * 设计原则：
 * 1) 该类负责“批处理调度”（抢占、回收、循环调用）
 * 2) 单条业务处理下沉到 Processor，避免大事务拖垮整批
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderShipReminderTaskServiceImpl implements OrderShipReminderTaskService {

    private static final List<String> LEVELS = Arrays.asList("H24", "H6", "H1");

    private final OrderShipReminderTaskMapper reminderTaskMapper;
    private final OrderShipReminderTaskProcessor reminderTaskProcessor;

    @Value("${order.ship-reminder.running-timeout-minutes:5}")
    private int runningTimeoutMinutes;

    @Override
    public void createReminderTasksForPaidOrder(Long orderId, Long sellerId, LocalDateTime payTime) {
        if (orderId == null || sellerId == null) {
            return;
        }
        LocalDateTime realPayTime = payTime == null ? LocalDateTime.now() : payTime;
        LocalDateTime deadline = realPayTime.plusHours(48);

        for (String level : LEVELS) {
            OrderShipReminderTask task = new OrderShipReminderTask();
            task.setOrderId(orderId);
            task.setSellerId(sellerId);
            task.setLevel(level);
            task.setDeadlineTime(deadline);
            task.setRemindTime(calcRemindTime(deadline, level));
            task.setStatus("PENDING");
            task.setRetryCount(0);
            task.setRunningAt(null);
            task.setSentAt(null);
            task.setClientMsgId(null);
            task.setLastError(null);

            int inserted = reminderTaskMapper.insertIgnore(task);
            log.info("create ship reminder task, orderId={}, level={}, inserted={}", orderId, level, inserted);
        }
    }

    @Override
    public int processDueTasks(int limit) {
        int size = limit <= 0 ? 200 : limit;

        // 1) 先回收“卡死 RUNNING”任务，避免长期占用导致永不重试
        recycleStaleRunningTasks(size);

        // 2) 抢占本轮到期任务
        LocalDateTime roundTs = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        int claimed = reminderTaskMapper.markRunningBatch(roundTs, size);
        if (claimed <= 0) {
            return 0;
        }

        // 3) 仅处理本轮抢占到的任务，避免和并发节点相互干扰
        List<OrderShipReminderTask> tasks = reminderTaskMapper.listRunningByRound(roundTs, size);
        if (tasks == null || tasks.isEmpty()) {
            return 0;
        }

        int success = 0;
        for (OrderShipReminderTask task : tasks) {
            try {
                if (reminderTaskProcessor.processOne(task)) {
                    success++;
                }
            } catch (Exception ex) {
                log.error("process ship reminder task failed, taskId={}, orderId={}",
                        task.getId(), task.getOrderId(), ex);
            }
        }
        return success;
    }

    /**
     * 回收超时 RUNNING 任务：
     * - 典型场景：节点在“发送消息后、更新任务前”崩溃
     * - 处理方式：转成 FAILED 并按退避策略设置下次 remind_time
     */
    private void recycleStaleRunningTasks(int limit) {
        int timeout = runningTimeoutMinutes <= 0 ? 5 : runningTimeoutMinutes;
        LocalDateTime staleBefore = LocalDateTime.now().minusMinutes(timeout);
        List<OrderShipReminderTask> staleTasks = reminderTaskMapper.listStaleRunning(staleBefore, limit);
        if (staleTasks == null || staleTasks.isEmpty()) {
            return;
        }

        for (OrderShipReminderTask task : staleTasks) {
            int nextRetryCount = (task.getRetryCount() == null ? 0 : task.getRetryCount()) + 1;
            LocalDateTime nextRemind = LocalDateTime.now().plusMinutes(nextDelayMinutes(nextRetryCount));
            reminderTaskMapper.markFail(task.getId(), nextRemind, "running_timeout_recycle");
        }
        log.warn("recycle stale ship-reminder tasks, count={}", staleTasks.size());
    }

    /**
     * 计算三档提醒时间。
     */
    private LocalDateTime calcRemindTime(LocalDateTime deadline, String level) {
        if ("H24".equalsIgnoreCase(level)) {
            return deadline.minusHours(24);
        }
        if ("H6".equalsIgnoreCase(level)) {
            return deadline.minusHours(6);
        }
        return deadline.minusHours(1);
    }

    /**
     * 指数退避档位（2m / 5m / 15m / 30m）。
     */
    private int nextDelayMinutes(int retryCount) {
        if (retryCount <= 1) {
            return 2;
        }
        if (retryCount == 2) {
            return 5;
        }
        if (retryCount == 3) {
            return 15;
        }
        return 30;
    }
}
