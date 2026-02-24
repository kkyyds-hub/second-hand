package com.demo.mq.consumer;

import com.demo.dto.mq.EventMessage;
import com.demo.dto.mq.ProductEventType;
import com.demo.dto.mq.ProductForceOffShelfPayload;
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
 * Day16 - 商品强制下架通知消费者。
 *
 * 消费事件：
 * - PRODUCT_FORCE_OFF_SHELF
 *
 * 处理结果：
 * - 给商品卖家发送系统站内信（含原因）
 */
@Slf4j
@Component
public class ProductForceOffShelfNoticeConsumer {

    private static final String CONSUMER_NAME = "ProductForceOffShelfNoticeConsumer";

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
    @Value("${product.notice.force-off-shelf-enabled:true}")
    private boolean forceOffShelfNoticeEnabled;

    @RabbitListener(queues = "${demo.rabbitmq.queue.product-force-off-shelf-notice}")
    public void onMessage(EventMessage<ProductForceOffShelfPayload> message,
                          Channel channel,
                          Message amqpMessage) throws Exception {
        long tag = amqpMessage.getMessageProperties().getDeliveryTag();
        MqConsumeLog logRecord = null;
        try {
            if (message == null || message.getPayload() == null) {
                log.warn("商品强制下架通知消息体为空，ACK 丢弃。");
                channel.basicAck(tag, false);
                return;
            }
            if (message.getEventId() == null || message.getEventId().trim().isEmpty()) {
                log.warn("商品强制下架通知缺少 eventId，ACK 丢弃。");
                channel.basicAck(tag, false);
                return;
            }
            if (!ProductEventType.PRODUCT_FORCE_OFF_SHELF.getCode().equals(message.getEventType())) {
                log.warn("商品强制下架通知事件类型不匹配：eventType={}，ACK 丢弃。", message.getEventType());
                channel.basicAck(tag, false);
                return;
            }

            logRecord = new MqConsumeLog();
            logRecord.setConsumer(CONSUMER_NAME);
            logRecord.setEventId(message.getEventId());
            logRecord.setStatus("PROCESSING");
            try {
                mqConsumeLogMapper.insert(logRecord);
            } catch (DuplicateKeyException ex) {
                log.info("幂等命中：consumer=ProductForceOffShelfNoticeConsumer, eventId={}", message.getEventId());
                channel.basicAck(tag, false);
                return;
            }

            ProductForceOffShelfPayload payload = message.getPayload();
            if (payload.getOwnerId() == null || payload.getProductId() == null) {
                mqConsumeLogMapper.updateStatus(logRecord.getId(), "OK");
                channel.basicAck(tag, false);
                return;
            }
            if (!forceOffShelfNoticeEnabled) {
                log.info("商品强制下架通知开关关闭，跳过发送：eventId={}", message.getEventId());
                mqConsumeLogMapper.updateStatus(logRecord.getId(), "OK");
                channel.basicAck(tag, false);
                return;
            }

            String content = noticeContentBuilder.buildForceOffShelfNotice(payload);
            systemNoticeService.sendNotice(
                    payload.getOwnerId(),
                    "SYS-PRODUCT-FORCE-OFF-SHELF-" + message.getEventId(),
                    content
            );

            mqConsumeLogMapper.updateStatus(logRecord.getId(), "OK");
            channel.basicAck(tag, false);
        } catch (BusinessException ex) {
            if (logRecord != null && logRecord.getId() != null) {
                mqConsumeLogMapper.updateStatus(logRecord.getId(), "OK");
            }
            log.warn("商品强制下架通知业务异常，ACK 丢弃。msg={}, err={}",
                    message, ex.getMessage());
            channel.basicAck(tag, false);
        } catch (Exception ex) {
            if (logRecord != null && logRecord.getId() != null) {
                mqConsumeLogMapper.updateStatus(logRecord.getId(), "FAIL");
            }
            log.error("商品强制下架通知处理失败，NACK 进入 DLQ。msg={}", message, ex);
            channel.basicNack(tag, false, false);
        }
    }
}
