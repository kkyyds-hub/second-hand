package com.demo.controller.admin;

import com.demo.entity.OrderRefundTask;
import com.demo.entity.OrderShipReminderTask;
import com.demo.entity.OrderShipTimeoutTask;
import com.demo.mapper.OrderRefundTaskMapper;
import com.demo.mapper.OrderShipReminderTaskMapper;
import com.demo.mapper.OrderShipTimeoutTaskMapper;
import com.demo.result.Result;
import com.demo.service.OrderRefundTaskService;
import com.demo.service.OrderShipReminderTaskService;
import com.demo.service.OrderShipTimeoutService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.validation.constraints.Min;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 管理员任务运维接口（仅后端排障/演示用）。
 *
 * 提供三类能力：
 * 1) 任务可观测：查询发货超时任务、退款任务当前状态
 * 2) 手动触发：不等定时器，立即执行一批任务
 * 3) 人工补偿：把任务推进到“可立即重试”状态
 */
@Slf4j
@RestController
@Validated
@RequiredArgsConstructor
@RequestMapping("/admin/ops/tasks")
public class AdminTaskOpsController {

    private static final int DEFAULT_LIMIT = 50;
    private static final int DEFAULT_RUN_BATCH = 200;
    private static final int MAX_LIMIT = 500;

    private final OrderShipTimeoutTaskMapper shipTimeoutTaskMapper;
    private final OrderShipReminderTaskMapper shipReminderTaskMapper;
    private final OrderRefundTaskMapper refundTaskMapper;
    private final OrderShipTimeoutService shipTimeoutService;
    private final OrderShipReminderTaskService shipReminderTaskService;
    private final OrderRefundTaskService refundTaskService;

    /**
     * 查询发货超时任务。
     *
     * 示例：
     * GET /admin/ops/tasks/ship-timeout?orderId=1001&status=PENDING&limit=50
     */
    @GetMapping("/ship-timeout")
    public Result<List<OrderShipTimeoutTask>> listShipTimeoutTasks(
            @RequestParam(value = "orderId", required = false) Long orderId,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "limit", required = false) Integer limit) {

        int size = normalize(limit, DEFAULT_LIMIT);
        List<OrderShipTimeoutTask> tasks = shipTimeoutTaskMapper.listForAdmin(orderId, safeStatus(status), size);
        return Result.success(tasks);
    }

    /**
     * 查询退款任务。
     *
     * 示例：
     * GET /admin/ops/tasks/refund?orderId=1001&status=FAILED&limit=50
     */
    @GetMapping("/refund")
    public Result<List<OrderRefundTask>> listRefundTasks(
            @RequestParam(value = "orderId", required = false) Long orderId,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "limit", required = false) Integer limit) {

        int size = normalize(limit, DEFAULT_LIMIT);
        List<OrderRefundTask> tasks = refundTaskMapper.listForAdmin(orderId, safeStatus(status), size);
        return Result.success(tasks);
    }

    /**
     * 查询发货提醒任务。
     *
     * 示例：
     * GET /admin/ops/tasks/ship-reminder?orderId=1001&status=FAILED&limit=50
     */
    @GetMapping("/ship-reminder")
    public Result<List<OrderShipReminderTask>> listShipReminderTasks(
            @RequestParam(value = "orderId", required = false) Long orderId,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "limit", required = false) Integer limit) {

        int size = normalize(limit, DEFAULT_LIMIT);
        List<OrderShipReminderTask> tasks = shipReminderTaskMapper.listForAdmin(orderId, safeStatus(status), size);
        return Result.success(tasks);
    }

    /**
     * 手动执行一批发货超时任务。
     *
     * 示例：
     * POST /admin/ops/tasks/ship-timeout/run-once?limit=200
     */
    @PostMapping("/ship-timeout/run-once")
    public Result<Map<String, Object>> runShipTimeoutOnce(
            @RequestParam(value = "limit", required = false) Integer limit) {

        int size = normalize(limit, DEFAULT_RUN_BATCH);
        int success = shipTimeoutService.processDueTasks(size);
        log.info("admin run ship-timeout once, limit={}, success={}", size, success);
        return Result.success(runResult("ship-timeout", size, success));
    }

    /**
     * 手动执行一批退款任务。
     *
     * 示例：
     * POST /admin/ops/tasks/refund/run-once?limit=200
     */
    @PostMapping("/refund/run-once")
    public Result<Map<String, Object>> runRefundOnce(
            @RequestParam(value = "limit", required = false) Integer limit) {

        int size = normalize(limit, DEFAULT_RUN_BATCH);
        int success = refundTaskService.processRunnableTasks(size);
        log.info("admin run refund once, limit={}, success={}", size, success);
        return Result.success(runResult("refund", size, success));
    }

    /**
     * 手动执行一批发货提醒任务。
     *
     * 示例：
     * POST /admin/ops/tasks/ship-reminder/run-once?limit=200
     */
    @PostMapping("/ship-reminder/run-once")
    public Result<Map<String, Object>> runShipReminderOnce(
            @RequestParam(value = "limit", required = false) Integer limit) {

        int size = normalize(limit, DEFAULT_RUN_BATCH);
        int success = shipReminderTaskService.processDueTasks(size);
        log.info("admin run ship-reminder once, limit={}, success={}", size, success);
        return Result.success(runResult("ship-reminder", size, success));
    }

    /**
     * 让某条发货超时任务“立即可重试”。
     *
     * 说明：
     * - 仅对 PENDING 生效
     * - 行为是清空 next_retry_time / last_error
     */
    @PostMapping("/ship-timeout/{taskId}/trigger-now")
    public Result<Map<String, Object>> triggerShipTimeoutNow(
            @PathVariable("taskId") @Min(value = 1, message = "taskId必须大于0") Long taskId) {

        int rows = shipTimeoutTaskMapper.triggerNow(taskId);
        log.info("admin trigger ship-timeout now, taskId={}, rows={}", taskId, rows);
        return Result.success(singleRowResult(taskId, rows));
    }

    /**
     * 重置退款任务：FAILED -> PENDING。
     *
     * 说明：
     * - 仅 FAILED 可被重置
     * - 清空 fail_reason / next_retry_time
     */
    @PostMapping("/refund/{taskId}/reset")
    public Result<Map<String, Object>> resetRefundTask(
            @PathVariable("taskId") @Min(value = 1, message = "taskId必须大于0") Long taskId) {

        int rows = refundTaskMapper.resetFailedToPending(taskId);
        log.info("admin reset refund task, taskId={}, rows={}", taskId, rows);
        return Result.success(singleRowResult(taskId, rows));
    }

    /**
     * 让某条发货提醒任务“立即可重试”。
     *
     * 说明：
     * - 仅 PENDING/FAILED 生效
     * - RUNNING/SUCCESS/CANCELLED 不会被该接口修改
     */
    @PostMapping("/ship-reminder/{taskId}/trigger-now")
    public Result<Map<String, Object>> triggerShipReminderNow(
            @PathVariable("taskId") @Min(value = 1, message = "taskId必须大于0") Long taskId) {

        int rows = shipReminderTaskMapper.triggerNow(taskId);
        log.info("admin trigger ship-reminder now, taskId={}, rows={}", taskId, rows);
        return Result.success(singleRowResult(taskId, rows));
    }

    private int normalize(Integer value, int defaultValue) {
        int target = value == null ? defaultValue : value;
        if (target <= 0) {
            target = defaultValue;
        }
        return Math.min(target, MAX_LIMIT);
    }

    private String safeStatus(String status) {
        if (status == null) {
            return null;
        }
        String trimmed = status.trim();
        return trimmed.isEmpty() ? null : trimmed.toUpperCase();
    }

    private Map<String, Object> runResult(String taskType, int batchSize, int success) {
        Map<String, Object> data = new HashMap<>(4);
        data.put("taskType", taskType);
        data.put("batchSize", batchSize);
        data.put("success", success);
        data.put("processedAt", System.currentTimeMillis());
        return data;
    }

    private Map<String, Object> singleRowResult(Long taskId, int rows) {
        Map<String, Object> data = new HashMap<>(3);
        data.put("taskId", taskId);
        data.put("updatedRows", rows);
        data.put("success", rows > 0);
        return data;
    }
}
