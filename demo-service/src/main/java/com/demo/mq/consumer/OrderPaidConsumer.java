package com.demo.mq.consumer;

import com.demo.dto.message.SendMessageRequest;
import com.demo.dto.mq.EventMessage;
import com.demo.dto.mq.OrderPaidPayload;
import com.demo.entity.Order;
import com.demo.enumeration.OrderStatus;
import com.demo.exception.BusinessException;
import com.demo.mapper.OrderMapper;
import com.demo.service.MessageService;
import com.demo.entity.MqConsumeLog;
import com.demo.mapper.MqConsumeLogMapper;
import org.springframework.dao.DuplicateKeyException;
import com.rabbitmq.client.Channel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Day14 - 订单已支付消费者
 * <p>
 * 职责：
 * 1) 监听 order.fulfillment.queue（绑定 order.paid）
 * 2) 校验订单状态
 * 3) 发送“提醒卖家发货”的站内消息
 * 4) 手动 ACK / NACK
 */
@Slf4j
@Component
public class OrderPaidConsumer {

    /**
     * 订单查询（用于校验订单状态、拿 sellerId）
     */
    @Autowired
    private OrderMapper orderMapper;

    /**
     * 站内消息服务（通知卖家发货）
     */
    @Autowired
    private MessageService messageService;

    @Autowired
    private MqConsumeLogMapper mqConsumeLogMapper;

    /** 幂等标识：消费者名称 */
    private static final String CONSUMER_NAME = "OrderPaidConsumer";

    /**
     * 消费订单支付消息
     *
     * @param message     统一事件信封（包含 OrderPaidPayload）
     * @param channel     RabbitMQ 原生通道（用于 ACK/NACK）
     * @param amqpMessage 原生消息（用于 deliveryTag）
     */
    @RabbitListener(queues = "${demo.rabbitmq.queue.order-fulfillment}")
public void onMessage(EventMessage<OrderPaidPayload> message,
                      Channel channel,
                      Message amqpMessage) throws Exception {
    long tag = amqpMessage.getMessageProperties().getDeliveryTag();
    MqConsumeLog logRecord = null;

    try {
        // 0) 兜底：空消息直接 ACK
        if (message == null || message.getPayload() == null) {
            log.warn("ORDER_PAID message payload empty, ack and drop.");
            channel.basicAck(tag, false);
            return;
        }

        // 1) eventId 必须存在（幂等关键字段）
        if (message.getEventId() == null || message.getEventId().trim().isEmpty()) {
            log.warn("ORDER_PAID message missing eventId, ack and drop.");
            channel.basicAck(tag, false);
            return;
        }

        // 2) 幂等抢占：先插入一条消费记录（状态=PROCESSING）
        logRecord = new MqConsumeLog();
        logRecord.setConsumer(CONSUMER_NAME);
        logRecord.setEventId(message.getEventId());
        logRecord.setStatus("PROCESSING");

        try {
            mqConsumeLogMapper.insert(logRecord);
        } catch (DuplicateKeyException e) {
            // 已经处理过该消息 → 直接 ACK
            log.info("ORDER_PAID duplicate consume, eventId={}", message.getEventId());
            channel.basicAck(tag, false);
            return;
        }

        // 3) 正常业务处理
        OrderPaidPayload payload = message.getPayload();
        Order order = orderMapper.selectOrderBasicById(payload.getOrderId());
        if (order == null) {
            log.warn("ORDER_PAID: order not found, orderId={}", payload.getOrderId());
            mqConsumeLogMapper.updateStatus(logRecord.getId(), "OK");
            channel.basicAck(tag, false);
            return;
        }

        // 只处理 PAID 状态
        OrderStatus status = OrderStatus.fromDbValue(order.getStatus());
        if (status != OrderStatus.PAID) {
            log.info("ORDER_PAID: skip, current status={}, orderId={}", status, order.getId());
            mqConsumeLogMapper.updateStatus(logRecord.getId(), "OK");
            channel.basicAck(tag, false);
            return;
        }

        // 4) 发货流程启动：给卖家发站内消息
        SendMessageRequest req = new SendMessageRequest();
        req.setToUserId(order.getSellerId());
        req.setClientMsgId("SYS-PAY-" + message.getEventId());
        req.setContent("订单已付款，请尽快发货。订单号：" + payload.getOrderNo());

        messageService.sendMessage(order.getId(), payload.getBuyerId(), req);

        log.info("ORDER_PAID handled, notify seller. orderId={}", order.getId());

        // 5) 标记消费成功 + ACK
        mqConsumeLogMapper.updateStatus(logRecord.getId(), "OK");
        channel.basicAck(tag, false);

    } catch (BusinessException ex) {
        // 业务异常：不需要重试，标记 OK + ACK
        if (logRecord != null && logRecord.getId() != null) {
            mqConsumeLogMapper.updateStatus(logRecord.getId(), "OK");
        }
        log.warn("ORDER_PAID business exception, ack and drop. msg={}, err={}", message, ex.getMessage());
        channel.basicAck(tag, false);

    } catch (Exception ex) {
        // 系统异常：标记 FAIL + NACK 进入 DLQ
        if (logRecord != null && logRecord.getId() != null) {
            mqConsumeLogMapper.updateStatus(logRecord.getId(), "FAIL");
        }
        log.error("ORDER_PAID handle failed, nack to DLQ. msg={}", message, ex);
        channel.basicNack(tag, false, false);
    }
}

}
