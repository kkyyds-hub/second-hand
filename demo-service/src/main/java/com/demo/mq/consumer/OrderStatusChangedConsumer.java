package com.demo.mq.consumer;

import com.demo.dto.message.SendMessageRequest;
import com.demo.dto.mq.EventMessage;
import com.demo.dto.mq.OrderStatusChangedPayload;
import com.demo.entity.MqConsumeLog;
import com.demo.entity.Order;
import com.demo.enumeration.OrderStatus;
import com.demo.exception.BusinessException;
import com.demo.mapper.MqConsumeLogMapper;
import com.demo.mapper.OrderMapper;
import com.demo.service.MessageService;
import com.rabbitmq.client.Channel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

/**
 * Day14 - 订单状态变更消费者
 *
 * 职责：
 * 1) 监听 order.status.sync.queue
 * 2) 校验订单存在与状态合法
 * 3) 给对方发送站内消息（提醒发货/确认收货）
 * 4) 手动 ACK / NACK
 */
@Slf4j
@Component
public class OrderStatusChangedConsumer {

    /** 订单查询（用于拿 buyerId/sellerId） */
    @Autowired
    private OrderMapper orderMapper;

    /** 站内消息服务（通知对方） */
    @Autowired
    private MessageService messageService;

    @Autowired
    private MqConsumeLogMapper mqConsumeLogMapper;

    /**
     * Step8 通知联动开关：是否发送“订单状态变更通知”（如发货成功通知买家）。
     */
    @Value("${order.notice.status-changed-enabled:true}")
    private boolean statusChangedNoticeEnabled;

    /** 幂等标识：消费者名称 */
    private static final String CONSUMER_NAME = "OrderStatusChangedConsumer";

    /**
     * 消费订单状态变更消息
     *
     * @param message     统一事件信封（包含 OrderStatusChangedPayload）
     * @param channel     RabbitMQ 通道（用于 ACK/NACK）
     * @param amqpMessage 原生消息（用于 deliveryTag）
     */
    @RabbitListener(queues = "${demo.rabbitmq.queue.order-status-sync}")
    public void onMessage(EventMessage<OrderStatusChangedPayload> message,
                          Channel channel,
                          Message amqpMessage) throws Exception {
        long tag = amqpMessage.getMessageProperties().getDeliveryTag();
        MqConsumeLog logRecord = null;
        try {
            // 1) 兜底：空消息直接 ACK
            if (message == null || message.getPayload() == null) {
                log.warn("ORDER_STATUS_CHANGED payload empty, ack and drop.");
                channel.basicAck(tag, false);
                return;
            }

            // 1.1) eventId 必须存在（幂等关键字段）
            if (message.getEventId() == null || message.getEventId().trim().isEmpty()) {
                log.warn("ORDER_STATUS_CHANGED message missing eventId, ack and drop.");
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
                log.info("ORDER_STATUS_CHANGED duplicate consume, eventId={}", message.getEventId());
                channel.basicAck(tag, false);
                return;
            }

            OrderStatusChangedPayload payload = message.getPayload();

            // 2) 查询订单
            Order order = orderMapper.selectOrderBasicById(payload.getOrderId());
            if (order == null) {
                log.warn("ORDER_STATUS_CHANGED: order not found, orderId={}", payload.getOrderId());
                mqConsumeLogMapper.updateStatus(logRecord.getId(), "OK");
                channel.basicAck(tag, false);
                return;
            }

            // 3) 判断变更后的状态
            OrderStatus newStatus = OrderStatus.fromDbValue(payload.getNewStatus());
            if (newStatus == null) {
                log.warn("ORDER_STATUS_CHANGED: invalid status, orderId={}, status={}",
                        payload.getOrderId(), payload.getNewStatus());
                mqConsumeLogMapper.updateStatus(logRecord.getId(), "OK");
                channel.basicAck(tag, false);
                return;
            }

            // 4) 计算消息接收方（对方）
            Long operatorId = payload.getOperatorId();
            if (operatorId == null) {
                log.warn("ORDER_STATUS_CHANGED: operatorId is null, orderId={}", payload.getOrderId());
                mqConsumeLogMapper.updateStatus(logRecord.getId(), "OK");
                channel.basicAck(tag, false);
                return;
            }
            Long toUserId = operatorId != null && operatorId.equals(order.getBuyerId())
                    ? order.getSellerId()
                    : order.getBuyerId();

            // 5) 发送站内消息（可通过配置开关关闭）
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

            log.info("ORDER_STATUS_CHANGED handled, orderId={}, newStatus={}",
                    order.getId(), payload.getNewStatus());

            // 7) 处理成功：标记 OK + ACK
            mqConsumeLogMapper.updateStatus(logRecord.getId(), "OK");
            channel.basicAck(tag, false);
        } catch (BusinessException ex) {
            // 业务异常一般无需重试，ACK 丢弃
            if (logRecord != null && logRecord.getId() != null) {
                mqConsumeLogMapper.updateStatus(logRecord.getId(), "OK");
            }
            log.warn("ORDER_STATUS_CHANGED business exception, ack and drop. msg={}, err={}",
                    message, ex.getMessage());
            channel.basicAck(tag, false);
        } catch (Exception ex) {
            // 系统异常：NACK 进入 DLQ
            if (logRecord != null && logRecord.getId() != null) {
                mqConsumeLogMapper.updateStatus(logRecord.getId(), "FAIL");
            }
            log.error("ORDER_STATUS_CHANGED handle failed, nack to DLQ. msg={}", message, ex);
            channel.basicNack(tag, false, false);
        }
    }
}
