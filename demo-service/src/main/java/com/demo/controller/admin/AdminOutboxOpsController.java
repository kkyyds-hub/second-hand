package com.demo.controller.admin;

import com.demo.entity.MessageOutbox;
import com.demo.job.OutboxPublishJob;
import com.demo.mapper.MessageOutboxMapper;
import com.demo.result.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import java.util.HashMap;
import java.util.Map;

/**
 * Outbox 运维接口（P4-S4）。
 *
 * 能力说明：
 * 1) 事件追踪：按 eventId 查询 Outbox 当前状态；
 * 2) 手动补偿：触发指定事件立即进入可重试状态；
 * 3) 人工发布：手动执行一轮 Outbox 发送；
 * 4) 指标查看：快速查看 NEW/SENT/FAIL 计数与失败重试总量。
 */
@Slf4j
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/ops/outbox")
public class AdminOutboxOpsController {

    private final MessageOutboxMapper messageOutboxMapper;
    private final OutboxPublishJob outboxPublishJob;

    /**
     * 按事件 ID 查询 Outbox 记录。
     */
    @GetMapping("/event/{eventId}")
    public Result<MessageOutbox> getByEventId(
            @PathVariable("eventId") @NotBlank(message = "eventId 不能为空") String eventId) {
        MessageOutbox outbox = messageOutboxMapper.selectByEventId(eventId.trim());
        if (outbox == null) {
            return Result.error("未找到对应 Outbox 事件");
        }
        return Result.success(outbox);
    }

    /**
     * 手动触发单个事件“立即重试”。
     */
    @PostMapping("/event/{eventId}/trigger-now")
    public Result<Map<String, Object>> triggerNow(
            @PathVariable("eventId") @NotBlank(message = "eventId 不能为空") String eventId) {
        int rows = messageOutboxMapper.triggerNowByEventId(eventId.trim());
        log.info("管理员触发 Outbox 立即重试：eventId={}, rows={}", eventId, rows);

        Map<String, Object> data = new HashMap<>(4);
        data.put("eventId", eventId.trim());
        data.put("updatedRows", rows);
        data.put("success", rows > 0);
        data.put("processedAt", System.currentTimeMillis());
        return Result.success(data);
    }

    /**
     * 手动执行一轮 Outbox 发布（用于故障恢复或演练）。
     */
    @PostMapping("/publish-once")
    public Result<Map<String, Object>> publishOnce(
            @RequestParam(value = "limit", required = false) @Min(value = 1, message = "limit 必须大于 0") Integer limit) {
        Map<String, Object> result = outboxPublishJob.publishOutboxMessagesOnce(limit);
        log.info("管理员手动执行 Outbox 发布：limit={}, pulled={}, sent={}, failed={}",
                result.get("limit"), result.get("pulled"), result.get("sent"), result.get("failed"));
        return Result.success(result);
    }

    /**
     * 查询 Outbox 指标（用于可观测与追踪）。
     */
    @GetMapping("/metrics")
    public Result<Map<String, Object>> metrics() {
        int newCount = messageOutboxMapper.countByStatus("NEW");
        int sentCount = messageOutboxMapper.countByStatus("SENT");
        int failCount = messageOutboxMapper.countByStatus("FAIL");
        int failRetrySum = messageOutboxMapper.sumRetryCountByStatus("FAIL");

        Map<String, Object> data = new HashMap<>(5);
        data.put("new", newCount);
        data.put("sent", sentCount);
        data.put("fail", failCount);
        data.put("failRetrySum", failRetrySum);
        data.put("queriedAt", System.currentTimeMillis());
        return Result.success(data);
    }
}
