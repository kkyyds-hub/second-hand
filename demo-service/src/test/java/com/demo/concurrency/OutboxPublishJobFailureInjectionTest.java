package com.demo.concurrency;

import com.demo.entity.MessageOutbox;
import com.demo.job.OutboxPublishJob;
import com.demo.mapper.MessageOutboxMapper;
import com.demo.service.serviceimpl.OutboxBatchStatusService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * P7-S2：Outbox 发布链路失败注入回归。
 *
 * 覆盖两类关键异常：
 * 1) MQ 发送阶段局部失败：应继续处理剩余消息，并按 sent/fail 分桶回写。
 * 2) DB 批量回写失败：应抛出异常，让外层调度在下一轮重试（至少一次语义）。
 */
@ExtendWith(MockitoExtension.class)
class OutboxPublishJobFailureInjectionTest {

    @Mock
    private MessageOutboxMapper messageOutboxMapper;
    @Mock
    private RabbitTemplate rabbitTemplate;
    @Mock
    private OutboxBatchStatusService outboxBatchStatusService;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private OutboxPublishJob outboxPublishJob;

    @Test
    void shouldContinueAndSplitSentFailBucketsWhenMqPartiallyFails() {
        MessageOutbox failMsg = buildOutbox(1001L, "evt-fail", "rk.fail");
        MessageOutbox okMsg = buildOutbox(1002L, "evt-ok", "rk.ok");

        when(messageOutboxMapper.listPending(eq(2), anyList())).thenReturn(List.of(failMsg, okMsg));
        doThrow(new RuntimeException("mq-down"))
                .when(rabbitTemplate)
                .convertAndSend(eq("order.events.exchange"), eq("rk.fail"), any(Object.class));

        Map<String, Object> result = outboxPublishJob.publishOutboxMessagesOnce(2);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Long>> sentIdsCaptor = ArgumentCaptor.forClass(List.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Long>> failIdsCaptor = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<LocalDateTime> retryCaptor = ArgumentCaptor.forClass(LocalDateTime.class);

        verify(outboxBatchStatusService).flushPublishResult(
                sentIdsCaptor.capture(),
                failIdsCaptor.capture(),
                retryCaptor.capture()
        );

        Assertions.assertEquals(List.of(1002L), sentIdsCaptor.getValue());
        Assertions.assertEquals(List.of(1001L), failIdsCaptor.getValue());
        Assertions.assertNotNull(retryCaptor.getValue());
        Assertions.assertEquals(2, result.get("pulled"));
        Assertions.assertEquals(1, result.get("sent"));
        Assertions.assertEquals(1, result.get("failed"));
    }

    @Test
    void shouldThrowWhenBatchFlushFailsToPreserveRetrySemantics() {
        MessageOutbox okMsg = buildOutbox(2001L, "evt-db-fail", "rk.ok");
        when(messageOutboxMapper.listPending(eq(1), anyList())).thenReturn(List.of(okMsg));
        doThrow(new RuntimeException("db-write-failed"))
                .when(outboxBatchStatusService)
                .flushPublishResult(any(), any(), any(LocalDateTime.class));

        RuntimeException ex = Assertions.assertThrows(
                RuntimeException.class,
                () -> outboxPublishJob.publishOutboxMessagesOnce(1)
        );

        Assertions.assertTrue(ex.getMessage().contains("db-write-failed"));
        verify(rabbitTemplate).convertAndSend(eq("order.events.exchange"), eq("rk.ok"), any(Object.class));
    }

    private MessageOutbox buildOutbox(Long id, String eventId, String routingKey) {
        MessageOutbox outbox = new MessageOutbox();
        outbox.setId(id);
        outbox.setEventId(eventId);
        outbox.setEventType("ORDER_STATUS_CHANGED");
        outbox.setExchangeName("order.events.exchange");
        outbox.setRoutingKey(routingKey);
        outbox.setBizId(900055L);
        outbox.setStatus("NEW");
        outbox.setRetryCount(0);
        outbox.setPayloadJson(
                "{\"eventId\":\"" + eventId + "\"," +
                        "\"eventType\":\"ORDER_STATUS_CHANGED\"," +
                        "\"routingKey\":\"" + routingKey + "\"," +
                        "\"bizId\":900055," +
                        "\"payload\":{\"orderId\":900055}," +
                        "\"version\":1}"
        );
        return outbox;
    }
}

