package com.demo.mq.consumer;

import com.demo.dto.mq.EventMessage;
import com.demo.dto.mq.ProductEventType;
import com.demo.dto.mq.ProductReportResolvedPayload;
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
 * Day16 - 举报工单处理结果通知消费者。
 * 使用 MqConsumeGuard 统一幂等抢占。
 */
@Slf4j
@Component
public class ProductReportResolvedNoticeConsumer {

    private static final String CONSUMER_NAME = "ProductReportResolvedNoticeConsumer";

    @Autowired
    private MqConsumeGuard consumeGuard;

    @Autowired
    private SystemNoticeService systemNoticeService;

    @Autowired
    private ProductNoticeContentBuilder noticeContentBuilder;

    @Value("${product.notice.report-resolved-enabled:true}")
    private boolean reportResolvedNoticeEnabled;

    @RabbitListener(queues = "${demo.rabbitmq.queue.product-report-resolved-notice}")
    public void onMessage(EventMessage<ProductReportResolvedPayload> message,
                          Channel channel,
                          Message amqpMessage) throws Exception {
        long tag = amqpMessage.getMessageProperties().getDeliveryTag();

        if (message == null || message.getPayload() == null) {
            log.warn("举报结果通知消息体为空，ACK 丢弃。");
            channel.basicAck(tag, false);
            return;
        }
        if (message.getEventId() == null || message.getEventId().trim().isEmpty()) {
            log.warn("举报结果通知缺少 eventId，ACK 丢弃。");
            channel.basicAck(tag, false);
            return;
        }
        if (!ProductEventType.PRODUCT_REPORT_RESOLVED.getCode().equals(message.getEventType())) {
            log.warn("举报结果通知事件类型不匹配：eventType={}，ACK 丢弃。", message.getEventType());
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
            ProductReportResolvedPayload payload = message.getPayload();
            if (payload.getReporterId() == null || payload.getProductId() == null) {
                consumeGuard.markSuccess(logId);
                channel.basicAck(tag, false);
                return;
            }
            if (!reportResolvedNoticeEnabled) {
                log.info("举报结果通知开关关闭，跳过发送：eventId={}", message.getEventId());
                consumeGuard.markSuccess(logId);
                channel.basicAck(tag, false);
                return;
            }

            String content = noticeContentBuilder.buildReportResolvedNotice(payload);
            systemNoticeService.sendNotice(
                    payload.getReporterId(),
                    "SYS-PRODUCT-REPORT-RESOLVED-" + message.getEventId(),
                    content);

            consumeGuard.markSuccess(logId);
            channel.basicAck(tag, false);

        } catch (BusinessException ex) {
            consumeGuard.markSuccess(logId);
            log.warn("举报结果通知业务异常，ACK。msg={}, err={}", message, ex.getMessage());
            channel.basicAck(tag, false);
        } catch (Exception ex) {
            consumeGuard.markFailure(logId);
            log.error("举报结果通知处理失败，NACK 进入 DLQ。msg={}", message, ex);
            channel.basicNack(tag, false, false);
        }
    }
}
