package com.demo.mq.consumer;

import com.demo.dto.message.SendMessageRequest;
import com.demo.dto.mq.EventMessage;
import com.demo.dto.mq.OrderStatusChangedPayload;
import com.demo.entity.Order;
import com.demo.enumeration.OrderStatus;
import com.demo.exception.BusinessException;
import com.demo.mapper.OrderMapper;
import com.demo.service.MessageService;
import com.demo.service.MqConsumeGuard;
import com.rabbitmq.client.Channel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class OrderStatusChangedConsumer {

    @Autowired private OrderMapper orderMapper;
    @Autowired private MessageService messageService;
    @Autowired private MqConsumeGuard guard;

    @Value("${order.notice.status-changed-enabled:true}")
    private boolean enabled;

    private static final String NAME = "OrderStatusChangedConsumer";

    @RabbitListener(queues = "${demo.rabbitmq.queue.order-status-sync}")
    public void onMessage(EventMessage<OrderStatusChangedPayload> envelope, Channel channel, Message amqpMessage) throws Exception {
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
            OrderStatusChangedPayload p = envelope.getPayload();
            Order order = orderMapper.selectOrderBasicById(p.getOrderId());
            if (order == null || OrderStatus.fromDbValue(p.getNewStatus()) == null || p.getOperatorId() == null) {
                guard.markSuccess(ar.logId(), lease);
                channel.basicAck(tag, false);
                return;
            }
            if (enabled) {
                Long to = p.getOperatorId().equals(order.getBuyerId()) ? order.getSellerId() : order.getBuyerId();
                OrderStatus ns = OrderStatus.fromDbValue(p.getNewStatus());
                String content = ns == OrderStatus.SHIPPED ? "卖家已发货，请注意查收。订单号：" + p.getOrderNo()
                        : ns == OrderStatus.COMPLETED ? "买家已确认收货，订单完成。订单号：" + p.getOrderNo()
                        : "订单状态更新为：" + p.getNewStatus() + "，订单号：" + p.getOrderNo();
                SendMessageRequest req = new SendMessageRequest();
                req.setToUserId(to);
                req.setClientMsgId("SYS-STATUS-" + envelope.getEventId());
                req.setContent(content);
                messageService.sendMessage(order.getId(), p.getOperatorId(), req);
            }
            log.info("ORDER_STATUS_CHANGED done orderId={}", order.getId());

            guard.markSuccess(ar.logId(), lease);
            ackSafely(channel, tag, NAME, envelope.getEventId());
        } catch (BusinessException ex) {
            guard.markSuccess(ar.logId(), lease);
            log.warn("ORDER_STATUS_CHANGED business ex eventId={}", envelope.getEventId(), ex);
            channel.basicAck(tag, false);
        } catch (Exception ex) {
            guard.markFailure(ar.logId(), lease, ex.getMessage());
            log.error("ORDER_STATUS_CHANGED fail eventId={}", envelope.getEventId(), ex);
            channel.basicNack(tag, false, false);
        }
    }

    private void ackSafely(Channel c, long t, String n, String e) { try { c.basicAck(t, false); } catch (Exception ex) { log.warn("{} ACK fail eventId={}", n, e, ex); } }
    private static boolean isEmpty(String s) { return s == null || s.trim().isEmpty(); }
}
