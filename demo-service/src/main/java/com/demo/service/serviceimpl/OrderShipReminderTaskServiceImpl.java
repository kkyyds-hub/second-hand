package com.demo.service.serviceimpl;

import com.demo.entity.Order;
import com.demo.entity.OrderShipReminderTask;
import com.demo.mapper.OrderMapper;
import com.demo.mapper.OrderShipReminderTaskMapper;
import com.demo.service.OrderShipReminderTaskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

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

    private final OrderMapper orderMapper;
    private final OrderShipReminderTaskMapper reminderTaskMapper;
    private final OrderShipReminderTaskProcessor reminderTaskProcessor;

    @Value("${order.ship-reminder.running-timeout-minutes:5}")
    private int runningTimeoutMinutes;

    /**
     * 在订单支付后按固定档位创建提醒任务（H24/H6/H1）。
     *
     * 幂等口径：
     * - 依赖 UNIQUE(order_id, level)；
     * - 重复创建不会新增脏数据，只记录幂等命中日志。
     */
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
            if (inserted == 1) {
                log.info("创建发货提醒任务成功：orderId={}, level={}", orderId, level);
            } else {
                log.info("幂等命中：action=createShipReminderTask, idemKey=orderId:{}|level:{}, detail=insertIgnoreRows=0",
                        orderId, level);
            }
        }
    }

    /**
     * 处理到期提醒任务（批处理入口）。
     *
     * 执行顺序：
     * 1) 回收卡死 RUNNING；
     * 2) 批量抢占 PENDING/FAILED；
     * 3) 逐条委派 Processor，在单条事务内推进状态。
     */
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

        // P3-S4：批量预加载订单，避免循环内固定 selectById 形成 N+1
        Map<Long, Order> orderMap = loadReminderOrderMap(tasks);

        int success = 0;
        for (OrderShipReminderTask task : tasks) {
            try {
                Order preloadedOrder = orderMap.get(task.getOrderId());
                if (reminderTaskProcessor.processOne(task, preloadedOrder)) {
                    success++;
                }
            } catch (Exception ex) {
                log.error("处理发货提醒任务失败：taskId={}, orderId={}",
                        task.getId(), task.getOrderId(), ex);
            }
        }
        return success;
    }

    /**
     * 批量加载提醒任务所需订单字段并构建映射。
     */
    private Map<Long, Order> loadReminderOrderMap(List<OrderShipReminderTask> tasks) {
        Set<Long> orderIds = tasks.stream()
                .map(OrderShipReminderTask::getOrderId)
                .filter(id -> id != null && id > 0)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (orderIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return orderMapper.selectOrderForReminderByIds(List.copyOf(orderIds)).stream()
                .collect(Collectors.toMap(Order::getId, order -> order));
    }

    /**
     * 回收超时 RUNNING 任务：
     * - 典型场景：节点在“发送消息后、更新任务前”崩溃
     * - 处理方式：转成 FAILED 并按退避策略设置下次 remind_time
     */
    private void recycleStaleRunningTasks(int limit) {
        // 1) 找出超时 RUNNING 任务：这些任务通常是“执行中宕机/中断”遗留的数据
        int timeout = runningTimeoutMinutes <= 0 ? 5 : runningTimeoutMinutes;
        LocalDateTime staleBefore = LocalDateTime.now().minusMinutes(timeout);
        List<OrderShipReminderTask> staleTasks = reminderTaskMapper.listStaleRunning(staleBefore, limit);
        if (staleTasks == null || staleTasks.isEmpty()) {
            return;
        }

        // 2) 按“下一次重试延时”分组，目的是把逐条 update 压缩成分组批量 update
        //    例如 2/5/15/30 分钟四个档位，最多 4 次 SQL 即可覆盖整批任务
        Map<Integer, List<Long>> idsGroupByDelay = new HashMap<>();
        for (OrderShipReminderTask task : staleTasks) {
            // stale 任务每次回收都视为一次失败重试，因此 retryCount + 1
            int nextRetryCount = (task.getRetryCount() == null ? 0 : task.getRetryCount()) + 1;
            int delayMinutes = nextDelayMinutes(nextRetryCount);
            idsGroupByDelay.computeIfAbsent(delayMinutes, key -> new java.util.ArrayList<>()).add(task.getId());
        }

        // 3) 基于同一基准时间计算 next_remind_time，避免同批任务时间漂移
        LocalDateTime baseNow = LocalDateTime.now();
        for (Map.Entry<Integer, List<Long>> entry : idsGroupByDelay.entrySet()) {
            LocalDateTime nextRemind = baseNow.plusMinutes(entry.getKey());
            // 批量将 RUNNING -> FAILED，并写入统一错误码，方便后续排查
            int rows = reminderTaskMapper.markFailBatch(entry.getValue(), nextRemind, "running_timeout_recycle");
            if (rows != entry.getValue().size()) {
                log.info("发货提醒任务回收 CAS 未命中：expectedRows={}, actualRows={}, delayMinutes={}",
                        entry.getValue().size(), rows, entry.getKey());
            }
        }
        log.warn("回收卡死发货提醒任务：count={}", staleTasks.size());
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
