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

/**
 * Day14 - 订单已支付消费者。
 *
 * 职责：
 * 1) 监听 order.fulfillment.queue（绑定 order.paid）
 * 2) 使用 MqConsumeGuard 统一幂等抢占
 * 3) 发送“提醒卖家发货”站内消息
 * 4) 手工 ACK / NACK
 */
@Slf4j
@Component
public class OrderPaidConsumer {

    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private MessageService messageService;

    @Autowired
    private OrderShipReminderTaskService shipReminderTaskService;

    @Autowired
    private MqConsumeGuard consumeGuard;

    @Value("${order.notice.paid-notify-seller-enabled:true}")
    private boolean paidNotifySellerEnabled;

    private static final String CONSUMER_NAME = "OrderPaidConsumer";

    @RabbitListener(queues = "${demo.rabbitmq.queue.order-fulfillment}")
    public void onMessage(EventMessage<OrderPaidPayload> message,
                          Channel channel,
                          Message amqpMessage) throws Exception {
        long tag = amqpMessage.getMessageProperties().getDeliveryTag();

        // 兜底
        if (message == null || message.getPayload() == null) {
            log.warn("ORDER_PAID 消息体为空，ACK 丢弃。");
            channel.basicAck(tag, false);
            return;
        }
        if (message.getEventId() == null || message.getEventId().trim().isEmpty()) {
            log.warn("ORDER_PAID 缺少 eventId，ACK 丢弃。");
            channel.basicAck(tag, false);
            return;
        }

        // 统一幂等抢占
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
            OrderPaidPayload payload = message.getPayload();
            Order order = orderMapper.selectOrderBasicById(payload.getOrderId());
            if (order == null) {
                log.warn("ORDER_PAID 订单不存在，orderId={}", payload.getOrderId());
                consumeGuard.markSuccess(logId);
                channel.basicAck(tag, false);
                return;
            }

            OrderStatus status = OrderStatus.fromDbValue(order.getStatus());
            if (status != OrderStatus.PAID) {
                log.info("ORDER_PAID 跳过：当前状态={}, orderId={}", status, order.getId());
                consumeGuard.markSuccess(logId);
                channel.basicAck(tag, false);
                return;
            }

            if (paidNotifySellerEnabled) {
                SendMessageRequest req = new SendMessageRequest();
                req.setToUserId(order.getSellerId());
                req.setClientMsgId("SYS-PAY-" + message.getEventId());
                req.setContent("订单已付款，请尽快发货。订单号：" + payload.getOrderNo());
                messageService.sendMessage(order.getId(), payload.getBuyerId(), req);
            }

            shipReminderTaskService.createReminderTasksForPaidOrder(
                    order.getId(), order.getSellerId(), payload.getPayTime());

            log.info("ORDER_PAID 处理完成：orderId={}", order.getId());

            consumeGuard.markSuccess(logId);
            channel.basicAck(tag, false);

        } catch (BusinessException ex) {
            consumeGuard.markSuccess(logId);
            log.warn("ORDER_PAID 业务异常，ACK。msg={}, err={}", message, ex.getMessage());
            channel.basicAck(tag, false);
        } catch (Exception ex) {
            consumeGuard.markFailure(logId);
            log.error("ORDER_PAID 处理失败，NACK 进入 DLQ。msg={}", message, ex);
            channel.basicNack(tag, false, false);
        }
    }
}
