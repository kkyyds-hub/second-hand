package com.demo.service.serviceimpl;

import com.demo.entity.Order;
import com.demo.entity.OrderRefundTask;
import com.demo.entity.OrderShipTimeoutTask;
import com.demo.enumeration.OrderStatus;
import com.demo.mapper.OrderMapper;
import com.demo.mapper.OrderRefundTaskMapper;
import com.demo.mapper.OrderShipTimeoutTaskMapper;
import com.demo.service.OrderNoticeService;
import com.demo.service.OrderShipTimeoutPenaltyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;

/**
 * 单条“发货超时任务”事务处理器。
 *
 * 设计原因：
 * 1) Spring 事务默认基于代理（proxy），同类内部调用不会走代理。
 * 2) 因此把单条任务处理拆到独立 Bean，确保 @Transactional 生效。
 *
 * 事务边界：
 * - 单条任务内的“关单 + 释放商品 + 任务状态更新”在一个事务中完成。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderShipTimeoutTaskProcessor {

    private final OrderMapper orderMapper;
    private final OrderShipTimeoutTaskMapper taskMapper;
    private final OrderRefundTaskMapper refundTaskMapper;
    private final OrderNoticeService orderNoticeService;
    private final OrderShipTimeoutPenaltyService penaltyService;

    /**
     * 失败后的重试间隔（秒）
     */
    @Value("${order.ship-timeout.retry-delay-seconds:120}")
    private int retryDelaySeconds;

    /**
     * 处理单条任务（事务方法，必须由外部 Bean 调用）。
     *
     * @param task 发货超时任务
     * @return true=本次确实完成了关单；false=未关单（如任务取消/重试）
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean processOne(OrderShipTimeoutTask task) {
        Long taskId = task.getId();
        Long orderId = task.getOrderId();

        // 1) 读取订单当前状态做分流
        Order order = orderMapper.selectOrderBasicById(orderId);
        if (order == null) {
            // 订单不存在：任务失效，直接取消任务
            taskMapper.markCancelled(taskId);
            return false;
        }

        OrderStatus status = OrderStatus.fromDbValue(order.getStatus());
        if (status == null) {
            markRetry(taskId, "invalid_order_status");
            return false;
        }

        // 2) 已发货/已完成/已取消：任务无意义，标记取消
        if (status == OrderStatus.SHIPPED || status == OrderStatus.COMPLETED || status == OrderStatus.CANCELLED) {
            taskMapper.markCancelled(taskId);
            return false;
        }

        // 3) 仅 paid 才允许走“超时未发货关单”
        if (status != OrderStatus.PAID) {
            markRetry(taskId, "unexpected_status:" + status.getDbValue());
            return false;
        }

        // 4) 条件更新：paid -> cancelled(ship_timeout)
        int rows = orderMapper.closeShipTimeoutOrder(orderId, task.getDeadlineTime());
        if (rows == 1) {
            // 5) 关单成功后释放商品占用（sold -> on_sale）
            orderMapper.releaseProductsForOrder(orderId);

            // 6) 关单成功后创建退款任务（幂等）
            // 注意：该步骤与关单同事务，确保不会出现“已关单但无退款任务”的不一致状态。
            createRefundTaskIfAbsent(order);

            // 6.1) 关单成功后执行处罚编排（框架扩展点）
            // 说明：当前默认开关关闭，不改变既有行为；后续在实现层补齐处罚策略。
            penaltyService.applyPenalty(order);

            // 7) 任务状态收敛为 DONE
            taskMapper.markDone(taskId);

            // 8) 事务提交后发送“超时取消”通知，避免回滚时提前落库消息
            registerAfterCommit(() -> orderNoticeService.notifyShipTimeoutCancelled(order));

            log.info("ship-timeout close success, orderId={}, taskId={}", orderId, taskId);
            return true;
        }

        // 7) rows=0：并发场景二次判定
        Order latest = orderMapper.selectOrderBasicById(orderId);
        if (latest == null) {
            taskMapper.markCancelled(taskId);
            return false;
        }

        OrderStatus latestStatus = OrderStatus.fromDbValue(latest.getStatus());
        if (latestStatus == OrderStatus.SHIPPED
                || latestStatus == OrderStatus.COMPLETED
                || latestStatus == OrderStatus.CANCELLED) {
            taskMapper.markCancelled(taskId);
            return false;
        }

        // 仍未成功关单：进入重试
        markRetry(taskId, "close_rows_0_status_" + latest.getStatus());
        return false;
    }

    /**
     * 幂等创建“发货超时退款任务”。
     *
     * 幂等策略：
     * - 依赖 order_refund_task 表唯一键（order_id + refund_type / idempotency_key）
     * - insertIgnore 返回 0 表示任务已存在，按幂等命中处理
     */
    private void createRefundTaskIfAbsent(Order order) {
        if (order == null || order.getId() == null) {
            throw new IllegalStateException("cannot create refund task because order is null");
        }
        if (order.getTotalAmount() == null) {
            throw new IllegalStateException("cannot create refund task because totalAmount is null, orderId=" + order.getId());
        }

        OrderRefundTask refundTask = new OrderRefundTask();
        refundTask.setOrderId(order.getId());
        refundTask.setRefundType("ship_timeout");
        refundTask.setAmount(order.getTotalAmount());
        refundTask.setStatus("PENDING");
        refundTask.setIdempotencyKey("refund:ship_timeout:" + order.getId());
        refundTask.setRetryCount(0);
        refundTask.setNextRetryTime(null);
        refundTask.setFailReason(null);

        int inserted = refundTaskMapper.insertIgnore(refundTask);
        if (inserted == 1) {
            log.info("create refund task success, orderId={}, refundType={}", order.getId(), refundTask.getRefundType());
        } else {
            log.info("refund task already exists, orderId={}, refundType={}", order.getId(), refundTask.getRefundType());
        }
    }

    /**
     * 在事务提交后执行回调。
     * - 有事务：afterCommit 执行
     * - 无事务：直接执行（兜底）
     */
    private void registerAfterCommit(Runnable callback) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                /**
                 * 实现接口定义的方法。
                 */
                @Override
                public void afterCommit() {
                    callback.run();
                }
            });
            return;
        }
        callback.run();
    }

    /**
     * 标记重试：
     * - retry_count +1
     * - 设置 next_retry_time
     * - 截断异常文本，避免超过字段长度
     */
    private void markRetry(Long taskId, String err) {
        int delay = retryDelaySeconds <= 0 ? 120 : retryDelaySeconds;
        String safeErr = err == null ? "unknown" : err;
        if (safeErr.length() > 240) {
            safeErr = safeErr.substring(0, 240);
        }
        taskMapper.markRetry(taskId, LocalDateTime.now().plusSeconds(delay), safeErr);
    }
}
