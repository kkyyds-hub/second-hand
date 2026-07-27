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

@Slf4j
@Component
public class InventoryUpdateConsumer {

    @Autowired private OrderMapper orderMapper;
    @Autowired private MqConsumeGuard guard;
    private static final String NAME = "InventoryUpdateConsumer";

    @RabbitListener(queues = "${demo.rabbitmq.queue.inventory-update}")
    public void onMessage(EventMessage<OrderCreatedPayload> envelope, Channel channel, Message amqpMessage) throws Exception {
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
            OrderCreatedPayload p = envelope.getPayload();
            if (p.getProductId() == null) {
                guard.markSuccess(ar.logId(), lease);
                channel.basicAck(tag, false);
                return;
            }
            if (p.getOrderId() != null) {
                Order order = orderMapper.selectOrderBasicById(p.getOrderId());
                if (order == null || OrderStatus.fromDbValue(order.getStatus()) == OrderStatus.CANCELLED) {
                    log.info("INVENTORY skip orderId={} status={}", p.getOrderId(),
                            order != null ? order.getStatus() : "null");
                    guard.markSuccess(ar.logId(), lease);
                    channel.basicAck(tag, false);
                    return;
                }
            }
            int rows = orderMapper.markProductSoldIfOnSale(p.getProductId());
            log.info("INVENTORY done productId={} rows={}", p.getProductId(), rows);

            guard.markSuccess(ar.logId(), lease);
            ackSafely(channel, tag, NAME, envelope.getEventId());
        } catch (Exception ex) {
            guard.markFailure(ar.logId(), lease, ex.getMessage());
            log.error("INVENTORY fail eventId={}", envelope.getEventId(), ex);
            channel.basicNack(tag, false, false);
        }
    }

    private void ackSafely(Channel c, long t, String n, String e) { try { c.basicAck(t, false); } catch (Exception ex) { log.warn("{} ACK fail eventId={}", n, e, ex); } }
    private static boolean isEmpty(String s) { return s == null || s.trim().isEmpty(); }
}
