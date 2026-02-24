package com.demo.job;

import com.demo.dto.mq.EventMessage;
import com.demo.entity.MessageOutbox;
import com.demo.mapper.MessageOutboxMapper;
import com.demo.service.serviceimpl.OutboxBatchStatusService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Day14 - Outbox 定时发布任务。
 *
 * 核心设计：
 * 1) 先逐条发送消息，保证每条消息的发送异常彼此隔离；
 * 2) 再批量回写发送结果，减少数据库往返次数；
 * 3) 回写失败由事务统一回滚，下次调度可重试（至少一次投递语义）。
 */
@Slf4j
@Component
public class OutboxPublishJob {

    @Autowired
    private MessageOutboxMapper messageOutboxMapper;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private OutboxBatchStatusService outboxBatchStatusService;

    /**
     * 每轮拉取上限。
     * 可通过配置动态调整，默认 50。
     */
    @Value("${outbox.publish.batch-size:50}")
    private int defaultBatchSize;

    /**
     * 发送失败后的默认重试间隔（秒）。
     */
    @Value("${outbox.publish.retry-delay-seconds:30}")
    private int retryDelaySeconds;

    /**
     * 每 5 秒扫描一次 Outbox。
     */
    @Scheduled(fixedDelay = 5000)
    public void publishOutboxMessages() {
        publishOutboxMessagesOnce(null);
    }

    /**
     * 执行一轮 Outbox 发布。
     *
     * 说明：
     * 1) 该方法既供定时任务调用，也供人工触发调用；
     * 2) 返回本轮统计结果，便于运维侧观察发送效果。
     *
     * @param limit 本轮拉取上限；为空或<=0时回退到配置值
     * @return 本轮发布统计
     */
    public java.util.Map<String, Object> publishOutboxMessagesOnce(Integer limit) {
        int effectiveLimit = (limit == null || limit <= 0) ? Math.max(defaultBatchSize, 1) : limit;

        // 第一步：拉取一批可发送的 Outbox（NEW/FAIL 且满足 next_retry_time 条件）
        List<MessageOutbox> list = messageOutboxMapper.listPending(effectiveLimit);
        if (list == null || list.isEmpty()) {
            return publishResult(effectiveLimit, 0, 0, 0);
        }

        // 分桶收集本轮发送结果：
        // - sentIds：发送成功，后续批量更新为 SENT
        // - failIds：发送失败，后续批量更新为 FAIL 并刷新 next_retry_time
        List<Long> sentIds = new ArrayList<>();
        List<Long> failIds = new ArrayList<>();

        for (MessageOutbox outbox : list) {
            try {
                // 第二步：反序列化 payload，恢复标准事件结构
                EventMessage<Object> eventMessage = objectMapper.readValue(
                        outbox.getPayloadJson(),
                        new TypeReference<EventMessage<Object>>() {
                        }
                );

                // 第三步：投递到 MQ。这里只负责“发消息”，不在此处做 DB 更新
                rabbitTemplate.convertAndSend(
                        outbox.getExchangeName(),
                        outbox.getRoutingKey(),
                        eventMessage
                );

                // 发送成功仅记录 ID，避免每条都 update 一次数据库
                sentIds.add(outbox.getId());
                log.info("Outbox 发送成功：id={}, eventId={}, eventType={}, routingKey={}",
                        outbox.getId(), outbox.getEventId(), outbox.getEventType(), outbox.getRoutingKey());
            } catch (Exception ex) {
                // 发送异常不打断本批次，记录失败 ID 进入批量回写阶段
                failIds.add(outbox.getId());
                log.error("Outbox 发送失败：id={}, eventId={}, eventType={}", outbox.getId(), outbox.getEventId(), outbox.getEventType(), ex);
            }
        }

        if (!sentIds.isEmpty() || !failIds.isEmpty()) {
            // 第四步：统一批量回写本轮发送结果（单事务）
            // 失败记录统一写入 nextRetry，便于下一轮调度重试
            int delay = retryDelaySeconds <= 0 ? 30 : retryDelaySeconds;
            LocalDateTime nextRetry = LocalDateTime.now().plusSeconds(delay);
            outboxBatchStatusService.flushPublishResult(sentIds, failIds, nextRetry);
            log.info("Outbox 回写完成：pulled={}, sent={}, failed={}, nextRetry={}",
                    list.size(), sentIds.size(), failIds.size(), nextRetry);
        }
        return publishResult(effectiveLimit, list.size(), sentIds.size(), failIds.size());
    }

    private java.util.Map<String, Object> publishResult(int limit, int pulled, int sent, int failed) {
        java.util.Map<String, Object> data = new java.util.HashMap<>(5);
        data.put("limit", limit);
        data.put("pulled", pulled);
        data.put("sent", sent);
        data.put("failed", failed);
        data.put("processedAt", System.currentTimeMillis());
        return data;
    }
}
