package com.demo.mq.consumer;

import com.demo.dto.mq.EventMessage;
import com.demo.dto.mq.ProductEventType;
import com.demo.dto.mq.ProductReviewedPayload;
import com.demo.entity.MqConsumeLog;
import com.demo.exception.BusinessException;
import com.demo.mapper.MqConsumeLogMapper;
import com.demo.service.SystemNoticeService;
import com.demo.service.support.ProductNoticeContentBuilder;
import com.rabbitmq.client.Channel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

/**
 * Day16 - 商品审核结果通知消费者。
 *
 * 消费事件：
 * - PRODUCT_REVIEWED
 *
 * 处理结果：
 * - 给商品卖家发送系统站内信（审核通过/驳回原因）
 */
@Slf4j
@Component
public class ProductReviewedNoticeConsumer {

    private static final String CONSUMER_NAME = "ProductReviewedNoticeConsumer";

    @Autowired
    private MqConsumeLogMapper mqConsumeLogMapper;

    @Autowired
    private SystemNoticeService systemNoticeService;

    @Autowired
    private ProductNoticeContentBuilder noticeContentBuilder;

    /**
     * Day16 通知开关：
     * - true：正常消费并发送站内信
     * - false：只 ACK 并记录日志（主链路不受影响）
     */
    @Value("${product.notice.reviewed-enabled:true}")
    private boolean reviewedNoticeEnabled;

    @RabbitListener(queues = "${demo.rabbitmq.queue.product-reviewed-notice}")
    public void onMessage(EventMessage<ProductReviewedPayload> message,
                          Channel channel,
                          Message amqpMessage) throws Exception {
        long tag = amqpMessage.getMessageProperties().getDeliveryTag();
        MqConsumeLog logRecord = null;
        try {
            if (message == null || message.getPayload() == null) {
                log.warn("PRODUCT_REVIEWED payload empty, ack and drop.");
                channel.basicAck(tag, false);
                return;
            }
            if (message.getEventId() == null || message.getEventId().trim().isEmpty()) {
                log.warn("PRODUCT_REVIEWED missing eventId, ack and drop.");
                channel.basicAck(tag, false);
                return;
            }
            if (!ProductEventType.PRODUCT_REVIEWED.getCode().equals(message.getEventType())) {
                log.warn("PRODUCT_REVIEWED unexpected eventType={}, ack and drop.", message.getEventType());
                channel.basicAck(tag, false);
                return;
            }

            // 幂等抢占：同一 eventId 只允许同一消费者处理一次。
            logRecord = new MqConsumeLog();
            logRecord.setConsumer(CONSUMER_NAME);
            logRecord.setEventId(message.getEventId());
            logRecord.setStatus("PROCESSING");
            try {
                mqConsumeLogMapper.insert(logRecord);
            } catch (DuplicateKeyException ex) {
                log.info("PRODUCT_REVIEWED duplicate consume, eventId={}", message.getEventId());
                channel.basicAck(tag, false);
                return;
            }

            ProductReviewedPayload payload = message.getPayload();
            if (payload.getOwnerId() == null || payload.getProductId() == null) {
                mqConsumeLogMapper.updateStatus(logRecord.getId(), "OK");
                channel.basicAck(tag, false);
                return;
            }
            if (!reviewedNoticeEnabled) {
                log.info("PRODUCT_REVIEWED notice disabled, skip send. eventId={}", message.getEventId());
                mqConsumeLogMapper.updateStatus(logRecord.getId(), "OK");
                channel.basicAck(tag, false);
                return;
            }

            String content = noticeContentBuilder.buildReviewedNotice(payload);
            systemNoticeService.sendNotice(
                    payload.getOwnerId(),
                    "SYS-PRODUCT-REVIEWED-" + message.getEventId(),
                    content
            );

            mqConsumeLogMapper.updateStatus(logRecord.getId(), "OK");
            channel.basicAck(tag, false);
        } catch (BusinessException ex) {
            if (logRecord != null && logRecord.getId() != null) {
                mqConsumeLogMapper.updateStatus(logRecord.getId(), "OK");
            }
            log.warn("PRODUCT_REVIEWED business exception, ack and drop. msg={}, err={}",
                    message, ex.getMessage());
            channel.basicAck(tag, false);
        } catch (Exception ex) {
            if (logRecord != null && logRecord.getId() != null) {
                mqConsumeLogMapper.updateStatus(logRecord.getId(), "FAIL");
            }
            log.error("PRODUCT_REVIEWED handle failed, nack to DLQ. msg={}", message, ex);
            channel.basicNack(tag, false, false);
        }
    }
}
