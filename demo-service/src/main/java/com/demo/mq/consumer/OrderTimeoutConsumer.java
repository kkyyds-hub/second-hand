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

/**
 * Day14 - 订单超时消费者（执行关单逻辑）。
 */
@Slf4j
@Component
public class OrderTimeoutConsumer {

    @Autowired
    private OrderTimeoutService orderTimeoutService;

    @Autowired
    private MqConsumeGuard consumeGuard;

    private static final String CONSUMER_NAME = "OrderTimeoutConsumer";

    @RabbitListener(queues = "${demo.rabbitmq.queue.order-timeout}")
    public void onMessage(EventMessage<OrderTimeoutPayload> message,
                          Channel channel,
                          Message amqpMessage) throws Exception {
        long tag = amqpMessage.getMessageProperties().getDeliveryTag();

        if (message == null || message.getPayload() == null) {
            log.warn("ORDER_TIMEOUT 消息体为空，ACK 丢弃。");
            channel.basicAck(tag, false);
            return;
        }
        if (message.getEventId() == null || message.getEventId().trim().isEmpty()) {
            log.warn("ORDER_TIMEOUT 缺少 eventId，ACK 丢弃。");
            channel.basicAck(tag, false);
            return;
        }

        MqConsumeGuard.AcquireResult ar = consumeGuard.acquire(CONSUMER_NAME, message.getEventId());
        Long logId = ar.logId();

        switch (ar.type()) {
            case ALREADY_COMPLETED:
                channel.basicAck(tag, false);
                return;
            case IN_PROGRESS_RECENT:
                channel.basicNack(tag, false, true);
                return;
            case ACQUIRED_NEW:
            case RECOVERED_STALE:
            case RETRYABLE_FAILED:
                break;
        }

        try {
            OrderTimeoutPayload payload = message.getPayload();
            LocalDateTime deadline = payload.getTimeoutAt() != null
                    ? payload.getTimeoutAt()
                    : LocalDateTime.now();

            boolean closed = orderTimeoutService.closeTimeoutOrderAndRelease(
                    payload.getOrderId(), deadline);

            log.info("ORDER_TIMEOUT 处理完成：orderId={}, closed={}", payload.getOrderId(), closed);

            consumeGuard.markSuccess(logId);
            channel.basicAck(tag, false);

        } catch (BusinessException ex) {
            consumeGuard.markSuccess(logId);
            log.warn("ORDER_TIMEOUT 业务异常，ACK。msg={}, err={}", message, ex.getMessage());
            channel.basicAck(tag, false);
        } catch (Exception ex) {
            consumeGuard.markFailure(logId);
            log.error("ORDER_TIMEOUT 处理失败，NACK 进入 DLQ。msg={}", message, ex);
            channel.basicNack(tag, false, false);
        }
    }
}
