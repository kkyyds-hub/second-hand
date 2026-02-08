package com.demo.mq.consumer;

import com.demo.entity.MqConsumeLog;
import com.demo.exception.BusinessException;
import com.demo.mapper.MqConsumeLogMapper;
import org.springframework.dao.DuplicateKeyException;
import com.demo.dto.mq.EventMessage;
import com.demo.dto.mq.OrderTimeoutPayload;
import com.demo.service.OrderTimeoutService;
import com.rabbitmq.client.Channel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Day14 - 订单超时消费者（执行关单逻辑）
 *
 * 职责：
 * 1) 监听 order.timeout.queue
 * 2) 解析消息载荷（订单ID、超时时间等）
 * 3) 调用业务服务完成“超时关单 + 释放商品 + 影响信用分”
 * 4) 手动 ACK / NACK（避免消息丢失或无限重试）
 */
@Slf4j
@Component
public class OrderTimeoutConsumer {

    /** 超时关单业务服务（已有实现：OrderTimeoutServiceImpl） */
    @Autowired
    private OrderTimeoutService orderTimeoutService;

    @Autowired
    private MqConsumeLogMapper mqConsumeLogMapper;

    /** 幂等标识：消费者名称 */
    private static final String CONSUMER_NAME = "OrderTimeoutConsumer";


    /**
     * 监听订单超时队列
     *
     * @param message     统一事件信封（包含 OrderTimeoutPayload）
     * @param channel     RabbitMQ 原生通道（用于手动 ACK/NACK）
     * @param amqpMessage RabbitMQ 原生消息（用于获取 deliveryTag）
     */
    @RabbitListener(queues = "${demo.rabbitmq.queue.order-timeout}")
    public void onMessage(EventMessage<OrderTimeoutPayload> message,
                          Channel channel,
                          Message amqpMessage) throws Exception {
        long tag = amqpMessage.getMessageProperties().getDeliveryTag();
        MqConsumeLog logRecord = null;
    
        try {
            // 0) 兜底：空消息直接 ACK
            if (message == null || message.getPayload() == null) {
                log.warn("ORDER_TIMEOUT message payload empty, ack and drop.");
                channel.basicAck(tag, false);
                return;
            }
    
            // 1) eventId 必须存在（幂等关键字段）
            if (message.getEventId() == null || message.getEventId().trim().isEmpty()) {
                log.warn("ORDER_TIMEOUT message missing eventId, ack and drop.");
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
                log.info("ORDER_TIMEOUT duplicate consume, eventId={}", message.getEventId());
                channel.basicAck(tag, false);
                return;
            }
    
            // 3) 正常业务处理
            OrderTimeoutPayload payload = message.getPayload();
            LocalDateTime deadline = payload.getTimeoutAt() != null
                    ? payload.getTimeoutAt()
                    : LocalDateTime.now();
    
            boolean closed = orderTimeoutService.closeTimeoutOrderAndRelease(
                    payload.getOrderId(), deadline
            );
    
            log.info("ORDER_TIMEOUT handled, orderId={}, closed={}", payload.getOrderId(), closed);
    
            // 4) 标记消费成功（OK）
            mqConsumeLogMapper.updateStatus(logRecord.getId(), "OK");
    
            // 5) ACK
            channel.basicAck(tag, false);
    
        } catch (BusinessException ex) {
            // 业务异常：不需要重试，标记 OK + ACK
            if (logRecord != null && logRecord.getId() != null) {
                mqConsumeLogMapper.updateStatus(logRecord.getId(), "OK");
            }
            log.warn("ORDER_TIMEOUT business exception, ack and drop. msg={}, err={}", message, ex.getMessage());
            channel.basicAck(tag, false);
    
        } catch (Exception ex) {
            // 系统异常：标记 FAIL + NACK 进入 DLQ
            if (logRecord != null && logRecord.getId() != null) {
                mqConsumeLogMapper.updateStatus(logRecord.getId(), "FAIL");
            }
            log.error("ORDER_TIMEOUT handle failed, nack to DLQ. message={}", message, ex);
            channel.basicNack(tag, false, false);
        }
    }
    
}
