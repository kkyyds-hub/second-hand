package com.demo.mq.consumer;

import com.demo.dto.mq.EventMessage;
import com.demo.dto.mq.OrderCreatedPayload;
import com.demo.entity.Order;
import com.demo.enumeration.OrderStatus;
import com.demo.mapper.OrderMapper;
import com.demo.service.MqConsumeGuard;
import com.rabbitmq.client.Channel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Day14 - 库存更新消费者。
 * 使用 MqConsumeGuard 统一幂等抢占。
 *
 * P5-S1 保护：若订单已取消则跳过，避免历史消息覆盖当前业务真相。
 */
@Slf4j
@Component
public class InventoryUpdateConsumer {

    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private MqConsumeGuard consumeGuard;

    private static final String CONSUMER_NAME = "InventoryUpdateConsumer";

    @RabbitListener(queues = "${demo.rabbitmq.queue.inventory-update}")
    public void onMessage(EventMessage<OrderCreatedPayload> message,
                          Channel channel,
                          Message amqpMessage) throws Exception {
        long tag = amqpMessage.getMessageProperties().getDeliveryTag();

        if (message == null || message.getPayload() == null) {
            log.warn("库存更新消息体为空，ACK 丢弃。");
            channel.basicAck(tag, false);
            return;
        }
        if (message.getEventId() == null || message.getEventId().trim().isEmpty()) {
            log.warn("库存更新消息缺少 eventId，ACK 丢弃。");
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
            OrderCreatedPayload payload = message.getPayload();

            if (payload.getProductId() == null) {
                log.warn("库存更新消息缺少 productId，ACK 丢弃。");
                consumeGuard.markSuccess(logId);
                channel.basicAck(tag, false);
                return;
            }

            if (payload.getOrderId() != null) {
                Order order = orderMapper.selectOrderBasicById(payload.getOrderId());
                if (order == null) {
                    log.warn("库存更新跳过：orderId={} 不存在，eventId={}", payload.getOrderId(), message.getEventId());
                    consumeGuard.markSuccess(logId);
                    channel.basicAck(tag, false);
                    return;
                }
                OrderStatus orderStatus = OrderStatus.fromDbValue(order.getStatus());
                if (orderStatus == null) {
                    log.warn("库存更新跳过：orderId={} 状态异常，status={}", payload.getOrderId(), order.getStatus());
                    consumeGuard.markSuccess(logId);
                    channel.basicAck(tag, false);
                    return;
                }
                if (orderStatus == OrderStatus.CANCELLED) {
                    log.info("库存更新跳过：orderId={} 已取消，productId={}, eventId={}",
                            payload.getOrderId(), payload.getProductId(), message.getEventId());
                    consumeGuard.markSuccess(logId);
                    channel.basicAck(tag, false);
                    return;
                }
            }

            int rows = orderMapper.markProductSoldIfOnSale(payload.getProductId());

            log.info("库存更新处理完成：productId={}, updatedRows={}", payload.getProductId(), rows);

            consumeGuard.markSuccess(logId);
            channel.basicAck(tag, false);

        } catch (Exception ex) {
            consumeGuard.markFailure(logId);
            log.error("库存更新处理失败，NACK 进入 DLQ。msg={}", message, ex);
            channel.basicNack(tag, false, false);
        }
    }
}
