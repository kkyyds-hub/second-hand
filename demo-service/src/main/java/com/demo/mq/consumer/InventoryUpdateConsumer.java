package com.demo.mq.consumer;

import com.demo.dto.mq.EventMessage;
import com.demo.dto.mq.OrderCreatedPayload;
import com.demo.entity.MqConsumeLog;
import com.demo.mapper.MqConsumeLogMapper;
import com.demo.mapper.OrderMapper;
import com.rabbitmq.client.Channel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

/**
 * Day14 - 库存更新消费者
 *
 * 职责：
 * 1) 监听 inventory.update.queue（订单创建事件）
 * 2) 根据 productId 把商品状态改为 SOLD（幂等）
 * 3) 手动 ACK / NACK
 *
 * 说明：
 * 当前订单创建时已同步改为 SOLD，这里作为“异步同步/兜底”。
 * 未来若改成纯异步库存更新，这个消费者就是主流程。
 */
@Slf4j
@Component
public class InventoryUpdateConsumer {

    /** 订单相关 Mapper（包含 markProductSoldIfOnSale） */
    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private MqConsumeLogMapper mqConsumeLogMapper;

    /** 幂等标识：消费者名称 */
    private static final String CONSUMER_NAME = "InventoryUpdateConsumer";

    /**
     * 监听订单创建事件（库存更新）
     *
     * @param message     统一事件信封（包含 OrderCreatedPayload）
     * @param channel     RabbitMQ 通道（用于 ACK/NACK）
     * @param amqpMessage 原生消息（用于 deliveryTag）
     */
    @RabbitListener(queues = "${demo.rabbitmq.queue.inventory-update}")
    public void onMessage(EventMessage<OrderCreatedPayload> message,
                          Channel channel,
                          Message amqpMessage) throws Exception {
        long tag = amqpMessage.getMessageProperties().getDeliveryTag();
        MqConsumeLog logRecord = null;
        try {
            // 1) 空消息兜底
            if (message == null || message.getPayload() == null) {
                log.warn("INVENTORY_UPDATE payload empty, ack and drop.");
                channel.basicAck(tag, false);
                return;
            }

            // 1.1) eventId 必须存在（幂等关键字段）
            if (message.getEventId() == null || message.getEventId().trim().isEmpty()) {
                log.warn("INVENTORY_UPDATE message missing eventId, ack and drop.");
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
                log.info("INVENTORY_UPDATE duplicate consume, eventId={}", message.getEventId());
                channel.basicAck(tag, false);
                return;
            }

            OrderCreatedPayload payload = message.getPayload();

            // 2) productId 必须存在
            if (payload.getProductId() == null) {
                log.warn("INVENTORY_UPDATE missing productId, ack and drop.");
                mqConsumeLogMapper.updateStatus(logRecord.getId(), "OK");
                channel.basicAck(tag, false);
                return;
            }

            // 3) 幂等更新：只在 on_sale 时改为 sold
            int rows = orderMapper.markProductSoldIfOnSale(payload.getProductId());

            log.info("INVENTORY_UPDATE handled, productId={}, updatedRows={}",
                    payload.getProductId(), rows);

            // 4) 成功：标记 OK + ACK
            mqConsumeLogMapper.updateStatus(logRecord.getId(), "OK");
            channel.basicAck(tag, false);
        } catch (Exception ex) {
            // 系统异常进入 DLQ
            if (logRecord != null && logRecord.getId() != null) {
                mqConsumeLogMapper.updateStatus(logRecord.getId(), "FAIL");
            }
            log.error("INVENTORY_UPDATE failed, nack to DLQ. msg={}", message, ex);
            channel.basicNack(tag, false, false);
        }
    }
}
