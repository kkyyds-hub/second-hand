package com.demo.service.serviceimpl;

import com.demo.entity.Order;
import com.demo.entity.OrderShipReminderTask;
import com.demo.enumeration.OrderStatus;
import com.demo.mapper.OrderMapper;
import com.demo.mapper.OrderShipReminderTaskMapper;
import com.demo.service.OrderNoticeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 单条发货提醒任务处理器。
 *
 * 设计说明：
 * 1) 单条处理失败不抛死整批，失败写回 FAILED 并走退避重试。
 * 2) 每次发送前都回查订单状态，保证“状态正确性优先于提醒发送”。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderShipReminderTaskProcessor {

    private static final DateTimeFormatter DEADLINE_DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final OrderMapper orderMapper;
    private final OrderShipReminderTaskMapper reminderTaskMapper;
    private final OrderNoticeService orderNoticeService;

    /**
     * 处理单条提醒任务。
     *
     * @param task 提醒任务
     * @return true=推进到 SUCCESS；false=未成功（FAILED/CANCELLED）
     */
    public boolean processOne(OrderShipReminderTask task) {
        return processOne(task, null);
    }

    /**
     * 处理单条提醒任务（支持批处理链路传入预加载订单）。
     *
     * @param task 提醒任务
     * @param preloadedOrder 批量预加载订单；为空时回退单条查询
     * @return true=推进到 SUCCESS；false=未成功（FAILED/CANCELLED）
     */
    public boolean processOne(OrderShipReminderTask task, Order preloadedOrder) {
        if (task == null || task.getId() == null || task.getOrderId() == null) {
            return false;
        }

        Long taskId = task.getId();
        try {
            // 1) 回查订单状态，避免“已发货/已取消还发提醒”
            // P3-S4：优先命中预加载订单，避免循环内固定 selectById
            Order order = preloadedOrder != null ? preloadedOrder : orderMapper.selectOrderForReminder(task.getOrderId());
            if (order == null) {
                markCancelled(taskId, "order_not_found");
                return false;
            }

            OrderStatus status = OrderStatus.fromDbValue(order.getStatus());
            if (status == null) {
                markFail(task, "invalid_order_status");
                return false;
            }

            // 2) 订单终态/非待发货状态：直接取消提醒任务
            if (status == OrderStatus.SHIPPED
                    || status == OrderStatus.COMPLETED
                    || status == OrderStatus.CANCELLED
                    || status == OrderStatus.PENDING) {
                markCancelled(taskId, "order_terminal_or_not_paid:" + status.getDbValue());
                return false;
            }

            if (status != OrderStatus.PAID) {
                markFail(task, "unexpected_status:" + status.getDbValue());
                return false;
            }

            // 3) 生成稳定幂等键（deadlineDate + level + orderId）
            String level = safeLevel(task.getLevel());
            String datePart = (task.getDeadlineTime() == null ? LocalDateTime.now() : task.getDeadlineTime())
                    .format(DEADLINE_DATE_FMT);
            String clientMsgId = "SHIP-REMIND-" + datePart + "-" + level + "-" + task.getOrderId();

            // 4) 动态计算“剩余时间”文案，让提醒更贴近真实平台
            String remaining = renderRemaining(task.getDeadlineTime());
            orderNoticeService.notifyShipReminder(order, level, remaining, clientMsgId);

            // 5) 发送成功（或幂等命中）后，任务推进为 SUCCESS
            int rows = reminderTaskMapper.markSuccess(taskId, LocalDateTime.now(), clientMsgId);
            if (rows == 1) {
                return true;
            }

            OrderShipReminderTask latest = reminderTaskMapper.selectById(taskId);
            String latestStatus = latest == null ? "NOT_FOUND" : latest.getStatus();
            if ("SUCCESS".equalsIgnoreCase(latestStatus)) {
                log.info("幂等命中：action=markShipReminderSuccess, idemKey=taskId:{}, detail=latestStatus=SUCCESS", taskId);
            } else {
                log.warn("发货提醒任务标记成功 CAS 未命中：taskId={}, expectedStatus=RUNNING, latestStatus={}",
                        taskId, latestStatus);
            }
            return false;
        } catch (Exception ex) {
            log.error("处理发货提醒任务失败：taskId={}, orderId={}", taskId, task.getOrderId(), ex);
            markFail(task, "send_error:" + ex.getMessage());
            return false;
        }
    }

    /**
     * 写回失败状态，并根据 retry_count 计算下一次执行时间。
     */
    private void markFail(OrderShipReminderTask task, String err) {
        int nextRetryCount = (task.getRetryCount() == null ? 0 : task.getRetryCount()) + 1;
        LocalDateTime nextRemindTime = LocalDateTime.now().plusMinutes(nextDelayMinutes(nextRetryCount));
        String safeErr = trimErr(err);
        int rows = reminderTaskMapper.markFail(task.getId(), nextRemindTime, safeErr);
        if (rows == 0) {
            OrderShipReminderTask latest = reminderTaskMapper.selectById(task.getId());
            String latestStatus = latest == null ? "NOT_FOUND" : latest.getStatus();
            log.warn("发货提醒任务标记失败 CAS 未命中：taskId={}, expectedStatus=RUNNING, latestStatus={}, reason={}",
                    task.getId(), latestStatus, safeErr);
        }
    }

    /**
     * 取消任务并记录并发分流语义。
     */
    private void markCancelled(Long taskId, String scene) {
        int rows = reminderTaskMapper.markCancelled(taskId);
        if (rows == 0) {
            OrderShipReminderTask latest = reminderTaskMapper.selectById(taskId);
            String latestStatus = latest == null ? "NOT_FOUND" : latest.getStatus();
            log.info("发货提醒任务取消 CAS 未命中：taskId={}, scene={}, latestStatus={}",
                    taskId, scene, latestStatus);
        }
    }

    /**
     * 退避策略：2m / 5m / 15m / 30m。
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

    /**
     * 统一剩余时间显示：
     * - >=1h 显示“X小时Y分钟”
     * - <1h 显示“不足1小时”
     * - 已过期显示“已到期，立即处理”
     */
    private String renderRemaining(LocalDateTime deadlineTime) {
        if (deadlineTime == null) {
            return "时间待校准";
        }
        Duration d = Duration.between(LocalDateTime.now(), deadlineTime);
        long totalMinutes = d.toMinutes();
        if (totalMinutes <= 0) {
            return "已到期，立即处理";
        }
        if (totalMinutes < 60) {
            return "不足1小时";
        }
        long h = totalMinutes / 60;
        long m = totalMinutes % 60;
        if (m == 0) {
            return h + "小时";
        }
        return h + "小时" + m + "分钟";
    }

    private String safeLevel(String level) {
        if ("H24".equalsIgnoreCase(level)) {
            return "H24";
        }
        if ("H6".equalsIgnoreCase(level)) {
            return "H6";
        }
        if ("H1".equalsIgnoreCase(level)) {
            return "H1";
        }
        return "H1";
    }

    private String trimErr(String err) {
        String val = err == null ? "unknown" : err;
        if (val.length() > 240) {
            return val.substring(0, 240);
        }
        return val;
    }
}
