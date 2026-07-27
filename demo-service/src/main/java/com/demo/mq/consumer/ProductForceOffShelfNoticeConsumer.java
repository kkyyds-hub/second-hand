package com.demo.mq.consumer;

import com.demo.dto.mq.EventMessage;
import com.demo.dto.mq.ProductEventType;
import com.demo.dto.mq.ProductForceOffShelfPayload;
import com.demo.exception.BusinessException;
import com.demo.service.MqConsumeGuard;
import com.demo.service.SystemNoticeService;
import com.demo.service.support.ProductNoticeContentBuilder;
import com.rabbitmq.client.Channel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Day16 - 商品强制下架通知消费者。
 * 使用 MqConsumeGuard 统一幂等抢占。
 */
@Slf4j
@Component
public class ProductForceOffShelfNoticeConsumer {

    private static final String CONSUMER_NAME = "ProductForceOffShelfNoticeConsumer";

    @Autowired
    private MqConsumeGuard consumeGuard;

    @Autowired
    private SystemNoticeService systemNoticeService;

    @Autowired
    private ProductNoticeContentBuilder noticeContentBuilder;

    @Value("${product.notice.force-off-shelf-enabled:true}")
    private boolean forceOffShelfNoticeEnabled;

    @RabbitListener(queues = "${demo.rabbitmq.queue.product-force-off-shelf-notice}")
    public void onMessage(EventMessage<ProductForceOffShelfPayload> message,
                          Channel channel,
                          Message amqpMessage) throws Exception {
        long tag = amqpMessage.getMessageProperties().getDeliveryTag();

        if (message == null || message.getPayload() == null) {
            log.warn("强制下架通知消息体为空，ACK 丢弃。");
            channel.basicAck(tag, false);
            return;
        }
        if (message.getEventId() == null || message.getEventId().trim().isEmpty()) {
            log.warn("强制下架通知缺少 eventId，ACK 丢弃。");
            channel.basicAck(tag, false);
            return;
        }
        if (!ProductEventType.PRODUCT_FORCE_OFF_SHELF.getCode().equals(message.getEventType())) {
            log.warn("强制下架通知事件类型不匹配：eventType={}，ACK 丢弃。", message.getEventType());
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
            ProductForceOffShelfPayload payload = message.getPayload();
            if (payload.getOwnerId() == null || payload.getProductId() == null) {
                consumeGuard.markSuccess(logId);
                channel.basicAck(tag, false);
                return;
            }
            if (!forceOffShelfNoticeEnabled) {
                log.info("强制下架通知开关关闭，跳过发送：eventId={}", message.getEventId());
                consumeGuard.markSuccess(logId);
                channel.basicAck(tag, false);
                return;
            }

            String content = noticeContentBuilder.buildForceOffShelfNotice(payload);
            systemNoticeService.sendNotice(
                    payload.getOwnerId(),
                    "SYS-PRODUCT-FORCE-OFF-SHELF-" + message.getEventId(),
                    content);

            consumeGuard.markSuccess(logId);
            channel.basicAck(tag, false);

        } catch (BusinessException ex) {
            consumeGuard.markSuccess(logId);
            log.warn("强制下架通知业务异常，ACK。msg={}, err={}", message, ex.getMessage());
            channel.basicAck(tag, false);
        } catch (Exception ex) {
            consumeGuard.markFailure(logId);
            log.error("强制下架通知处理失败，NACK 进入 DLQ。msg={}", message, ex);
            channel.basicNack(tag, false, false);
        }
    }
}
