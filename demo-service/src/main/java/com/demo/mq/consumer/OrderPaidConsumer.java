package com.demo.mq.consumer;

import com.demo.dto.message.SendMessageRequest;
import com.demo.dto.mq.EventMessage;
import com.demo.dto.mq.OrderPaidPayload;
import com.demo.entity.Order;
import com.demo.enumeration.OrderStatus;
import com.demo.exception.BusinessException;
import com.demo.mapper.OrderMapper;
import com.demo.service.MessageService;
import com.demo.service.MqConsumeGuard;
import com.demo.service.OrderShipReminderTaskService;
import com.rabbitmq.client.Channel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class OrderPaidConsumer {

    @Autowired private OrderMapper orderMapper;
    @Autowired private MessageService messageService;
    @Autowired private OrderShipReminderTaskService shipReminderTaskService;
    @Autowired private MqConsumeGuard guard;

    @Value("${order.notice.paid-notify-seller-enabled:true}")
    private boolean paidNotifySellerEnabled;

    private static final String NAME = "OrderPaidConsumer";

    @RabbitListener(queues = "${demo.rabbitmq.queue.order-fulfillment}")
    public void onMessage(EventMessage<OrderPaidPayload> envelope,
                          Channel channel, Message amqpMessage) throws Exception {
        long tag = amqpMessage.getMessageProperties().getDeliveryTag();

        if (envelope == null || envelope.getPayload() == null || isEmpty(envelope.getEventId())) {
            channel.basicAck(tag, false);
            return;
        }

        MqConsumeGuard.AcquireResult ar = guard.acquire(NAME, envelope.getEventId());
        String lease = ar.leaseToken();

        switch (ar.type()) {
            case ALREADY_COMPLETED:
                channel.basicAck(tag, false);
                return;
            case IN_PROGRESS_RECENT:
            case UNKNOWN_STATE_RETRY:
                channel.basicNack(tag, false, true);
                return;
            case ACQUIRED_NEW:
            case RECOVERED_STALE:
            case RETRYABLE_FAILED:
                break;
        }

        try {
            OrderPaidPayload p = envelope.getPayload();
            Order order = orderMapper.selectOrderBasicById(p.getOrderId());
            if (order == null || OrderStatus.fromDbValue(order.getStatus()) != OrderStatus.PAID) {
                guard.markSuccess(ar.logId(), lease);
                channel.basicAck(tag, false);
                return;
            }
            if (paidNotifySellerEnabled) {
                SendMessageRequest req = new SendMessageRequest();
                req.setToUserId(order.getSellerId());
                req.setClientMsgId("SYS-PAY-" + envelope.getEventId());
                req.setContent("订单已付款，请尽快发货。订单号：" + p.getOrderNo());
                messageService.sendMessage(order.getId(), p.getBuyerId(), req);
            }
            shipReminderTaskService.createReminderTasksForPaidOrder(
                    order.getId(), order.getSellerId(), p.getPayTime());
            log.info("ORDER_PAID done orderId={}", order.getId());

            guard.markSuccess(ar.logId(), lease);
            ackSafely(channel, tag, "ORDER_PAID", envelope.getEventId());
        } catch (BusinessException ex) {
            guard.markSuccess(ar.logId(), lease);
            log.warn("ORDER_PAID business ex eventId={} err={}", envelope.getEventId(), ex.getMessage());
            channel.basicAck(tag, false);
        } catch (Exception ex) {
            guard.markFailure(ar.logId(), lease, ex.getMessage());
            log.error("ORDER_PAID fail eventId={}", envelope.getEventId(), ex);
            channel.basicNack(tag, false, false);
        }
    }

    private void ackSafely(Channel channel, long tag, String consumer, String eventId) {
        try { channel.basicAck(tag, false); }
        catch (Exception ex) { log.warn("{} ACK failed eventId={} (state=OK preserved)", consumer, eventId, ex); }
    }

    private static boolean isEmpty(String s) { return s == null || s.trim().isEmpty(); }
}
