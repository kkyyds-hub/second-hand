package com.demo.mq.consumer;

import com.demo.dto.mq.EventMessage;
import com.demo.dto.mq.OrderTimeoutPayload;
import com.demo.exception.BusinessException;
import com.demo.service.MqConsumeGuard;
import com.demo.service.OrderTimeoutService;
import com.rabbitmq.client.Channel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Slf4j
@Component
public class OrderTimeoutConsumer {

    @Autowired private OrderTimeoutService orderTimeoutService;
    @Autowired private MqConsumeGuard guard;
    private static final String NAME = "OrderTimeoutConsumer";

    @RabbitListener(queues = "${demo.rabbitmq.queue.order-timeout}")
    public void onMessage(EventMessage<OrderTimeoutPayload> envelope, Channel channel, Message amqpMessage) throws Exception {
        long tag = amqpMessage.getMessageProperties().getDeliveryTag();

        if (envelope == null || envelope.getPayload() == null || isEmpty(envelope.getEventId())) {
            channel.basicAck(tag, false);
            return;
        }

        MqConsumeGuard.AcquireResult ar = guard.acquire(NAME, envelope.getEventId());
        String lease = ar.leaseToken();

        switch (ar.type()) {
            case ALREADY_COMPLETED: channel.basicAck(tag, false); return;
            case IN_PROGRESS_RECENT: case UNKNOWN_STATE_RETRY: channel.basicNack(tag, false, true); return;
            case ACQUIRED_NEW: case RECOVERED_STALE: case RETRYABLE_FAILED: break;
        }

        try {
            OrderTimeoutPayload p = envelope.getPayload();
            LocalDateTime deadline = p.getTimeoutAt() != null ? p.getTimeoutAt() : LocalDateTime.now();
            boolean closed = orderTimeoutService.closeTimeoutOrderAndRelease(p.getOrderId(), deadline);
            log.info("ORDER_TIMEOUT done orderId={} closed={}", p.getOrderId(), closed);

            guard.markSuccess(ar.logId(), lease);
            ackSafely(channel, tag, "ORDER_TIMEOUT", envelope.getEventId());
        } catch (BusinessException ex) {
            guard.markSuccess(ar.logId(), lease);
            log.warn("ORDER_TIMEOUT business ex eventId={}", envelope.getEventId(), ex);
            channel.basicAck(tag, false);
        } catch (Exception ex) {
            guard.markFailure(ar.logId(), lease, ex.getMessage());
            log.error("ORDER_TIMEOUT fail eventId={}", envelope.getEventId(), ex);
            channel.basicNack(tag, false, false);
        }
    }

    private void ackSafely(Channel c, long t, String name, String eid) { try { c.basicAck(t, false); } catch (Exception ex) { log.warn("{} ACK fail eventId={}", name, eid, ex); } }
    private static boolean isEmpty(String s) { return s == null || s.trim().isEmpty(); }
}
