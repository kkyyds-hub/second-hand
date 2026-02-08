package com.demo.job;

import com.demo.dto.mq.EventMessage;
import com.demo.entity.MessageOutbox;
import com.demo.mapper.MessageOutboxMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Day14 - Outbox 定时发布任务
 *
 * 职责：
 * 1) 定时拉取待发送消息（NEW / FAIL）
 * 2) 发送到 RabbitMQ
 * 3) 成功标记 SENT，失败标记 FAIL 并设置重试时间
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

    /**
     * 每 5 秒扫描一次 Outbox
     * 可根据实际负载调整
     */
    @Scheduled(fixedDelay = 5000)
    public void publishOutboxMessages() {
        // 1) 拉取待发送消息（最多 50 条）
        List<MessageOutbox> list = messageOutboxMapper.listPending(50);
        if (list == null || list.isEmpty()) {
            return;
        }

        for (MessageOutbox outbox : list) {
            try {
                // 2) 先把 JSON 字符串解析成 EventMessage 对象
                EventMessage<Object> eventMessage = objectMapper.readValue(
                        outbox.getPayloadJson(),
                        new TypeReference<EventMessage<Object>>() {}
                );

                // 3) 发送 EventMessage 对象（让消费者仍然收到 EventMessage）
                rabbitTemplate.convertAndSend(
                        outbox.getExchangeName(),
                        outbox.getRoutingKey(),
                        eventMessage
                );

                // 4) 成功：标记 SENT
                messageOutboxMapper.markSent(outbox.getId());
                log.info("Outbox sent success, id={}, eventId={}", outbox.getId(), outbox.getEventId());

            } catch (Exception ex) {
                // 5) 失败：标记 FAIL，并设置下次重试时间（比如 30 秒后）
                LocalDateTime nextRetry = LocalDateTime.now().plusSeconds(30);
                messageOutboxMapper.markFail(outbox.getId(), nextRetry);

                log.error("Outbox send failed, id={}, eventId={}", outbox.getId(), outbox.getEventId(), ex);
            }
        }
    }
}
