package com.demo.service.serviceimpl;

import com.demo.entity.Order;
import com.demo.entity.OrderRefundTask;
import com.demo.mapper.OrderMapper;
import com.demo.mapper.OrderRefundTaskMapper;
import com.demo.service.OrderRefundAccountingService;
import com.demo.service.OrderNoticeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;

/**
 * 单条退款任务事务处理器。
 *
 * 当前口径：
 * - 演示环境使用 Mock 退款成功（不对接真实支付网关）
 * - 仍保留失败重试机制，便于后续接入真实退款通道
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderRefundTaskProcessor {

    private final OrderRefundTaskMapper refundTaskMapper;
    private final OrderMapper orderMapper;
    private final OrderNoticeService orderNoticeService;
    private final OrderRefundAccountingService refundAccountingService;

    @Value("${order.refund.retry-delay-seconds:120}")
    private int retryDelaySeconds;

    /**
     * 处理单条退款任务。
     *
     * @return true=本次成功推进到 SUCCESS；false=未成功（已写失败重试或幂等命中）
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean processOne(OrderRefundTask task) {
        return processOne(task, null);
    }

    /**
     * 处理单条退款任务（支持批量链路预加载订单）。
     *
     * @param task 退款任务
     * @param preloadedOrder 预加载订单；为空时回退单条查询
     * @return true=本次成功推进到 SUCCESS；false=未成功（已写失败重试或幂等命中）
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean processOne(OrderRefundTask task, Order preloadedOrder) {
        if (task == null || task.getId() == null) {
            return false;
        }

        // P3-S4：优先使用批处理阶段预加载结果，避免循环内固定 selectById
        Order order = preloadedOrder != null ? preloadedOrder : orderMapper.selectOrderBasicById(task.getOrderId());
        if (order == null) {
            markFail(task, "order_not_found");
            return false;
        }

        try {
            // Step6-5/Step-next：先执行记账编排，再推进任务状态。
            // 这样后续接入真实记账后，能确保“记账与任务成功”同事务一致。
            refundAccountingService.recordRefund(order, task);

            // 演示口径：当前仍是 Mock 退款成功，直接推进任务状态
            String expectedStatus = task.getStatus() == null ? "PENDING" : task.getStatus();
            int rows = refundTaskMapper.markSuccess(task.getId(), expectedStatus);
            if (rows == 1) {
                // 事务提交后再通知，避免回滚导致“消息已发但状态未变”
                registerAfterCommit(() -> orderNoticeService.notifyRefundSuccess(order, task));
                log.info("退款任务处理成功：taskId={}, orderId={}", task.getId(), task.getOrderId());
                return true;
            }

            // rows=0：并发分流（幂等命中 / 已进入其他状态）
            OrderRefundTask latest = refundTaskMapper.selectById(task.getId());
            if (latest != null && "SUCCESS".equalsIgnoreCase(latest.getStatus())) {
                log.info("幂等命中：action=markRefundSuccess, idemKey=orderId:{}|refundType:{}, detail=latestStatus=SUCCESS",
                        task.getOrderId(), task.getRefundType());
                return false;
            }

            String latestStatus = latest == null ? "NOT_FOUND" : latest.getStatus();
            log.warn("退款任务 CAS 未命中：taskId={}, expectedStatus={}, latestStatus={}",
                    task.getId(), expectedStatus, latestStatus);
            return false;
        } catch (Exception ex) {
            markFail(task, "mock_refund_error:" + ex.getMessage());
            return false;
        }
    }

    private void markFail(OrderRefundTask task, String reason) {
        Long taskId = task == null ? null : task.getId();
        if (taskId == null) {
            return;
        }
        int delay = retryDelaySeconds <= 0 ? 120 : retryDelaySeconds;
        String safeReason = reason == null ? "unknown" : reason;
        if (safeReason.length() > 240) {
            safeReason = safeReason.substring(0, 240);
        }
        String expectedStatus = task.getStatus() == null ? "PENDING" : task.getStatus();
        int rows = refundTaskMapper.markFail(taskId, expectedStatus, LocalDateTime.now().plusSeconds(delay), safeReason);
        if (rows == 0) {
            OrderRefundTask latest = refundTaskMapper.selectById(taskId);
            String latestStatus = latest == null ? "NOT_FOUND" : latest.getStatus();
            log.warn("退款任务标记失败 CAS 未命中：taskId={}, expectedStatus={}, latestStatus={}, reason={}",
                    taskId, expectedStatus, latestStatus, safeReason);
        }
    }

    private void registerAfterCommit(Runnable callback) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    callback.run();
                }
            });
            return;
        }
        callback.run();
    }
}
