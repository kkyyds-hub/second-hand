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

/**
 * Day14 - 订单状态变更消费者。
 * 使用 MqConsumeGuard 统一幂等抢占。
 */
@Slf4j
@Component
public class OrderStatusChangedConsumer {

    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private MessageService messageService;

    @Autowired
    private MqConsumeGuard consumeGuard;

    @Value("${order.notice.status-changed-enabled:true}")
    private boolean statusChangedNoticeEnabled;

    private static final String CONSUMER_NAME = "OrderStatusChangedConsumer";

    @RabbitListener(queues = "${demo.rabbitmq.queue.order-status-sync}")
    public void onMessage(EventMessage<OrderStatusChangedPayload> message,
                          Channel channel,
                          Message amqpMessage) throws Exception {
        long tag = amqpMessage.getMessageProperties().getDeliveryTag();

        if (message == null || message.getPayload() == null) {
            log.warn("ORDER_STATUS_CHANGED 消息体为空，ACK 丢弃。");
            channel.basicAck(tag, false);
            return;
        }
        if (message.getEventId() == null || message.getEventId().trim().isEmpty()) {
            log.warn("ORDER_STATUS_CHANGED 缺少 eventId，ACK 丢弃。");
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
            OrderStatusChangedPayload payload = message.getPayload();

            Order order = orderMapper.selectOrderBasicById(payload.getOrderId());
            if (order == null) {
                log.warn("ORDER_STATUS_CHANGED 订单不存在，orderId={}", payload.getOrderId());
                consumeGuard.markSuccess(logId);
                channel.basicAck(tag, false);
                return;
            }

            OrderStatus newStatus = OrderStatus.fromDbValue(payload.getNewStatus());
            if (newStatus == null) {
                log.warn("ORDER_STATUS_CHANGED 状态非法，orderId={}, status={}",
                        payload.getOrderId(), payload.getNewStatus());
                consumeGuard.markSuccess(logId);
                channel.basicAck(tag, false);
                return;
            }

            Long operatorId = payload.getOperatorId();
            if (operatorId == null) {
                log.warn("ORDER_STATUS_CHANGED operatorId 为空，orderId={}", payload.getOrderId());
                consumeGuard.markSuccess(logId);
                channel.basicAck(tag, false);
                return;
            }

            Long toUserId = operatorId.equals(order.getBuyerId())
                    ? order.getSellerId()
                    : order.getBuyerId();

            if (statusChangedNoticeEnabled) {
                String content;
                if (newStatus == OrderStatus.SHIPPED) {
                    content = "卖家已发货，请注意查收。订单号：" + payload.getOrderNo();
                } else if (newStatus == OrderStatus.COMPLETED) {
                    content = "买家已确认收货，订单完成。订单号：" + payload.getOrderNo();
                } else {
                    content = "订单状态更新为：" + payload.getNewStatus() + "，订单号：" + payload.getOrderNo();
                }

                SendMessageRequest req = new SendMessageRequest();
                req.setToUserId(toUserId);
                req.setClientMsgId("SYS-STATUS-" + message.getEventId());
                req.setContent(content);
                messageService.sendMessage(order.getId(), operatorId, req);
            }

            log.info("ORDER_STATUS_CHANGED 处理完成：orderId={}, newStatus={}",
                    order.getId(), payload.getNewStatus());

            consumeGuard.markSuccess(logId);
            channel.basicAck(tag, false);

        } catch (BusinessException ex) {
            consumeGuard.markSuccess(logId);
            log.warn("ORDER_STATUS_CHANGED 业务异常，ACK。msg={}, err={}", message, ex.getMessage());
            channel.basicAck(tag, false);
        } catch (Exception ex) {
            consumeGuard.markFailure(logId);
            log.error("ORDER_STATUS_CHANGED 处理失败，NACK 进入 DLQ。msg={}", message, ex);
            channel.basicNack(tag, false, false);
        }
    }
}
