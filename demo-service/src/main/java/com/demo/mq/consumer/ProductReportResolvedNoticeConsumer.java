package com.demo.mq.consumer;

import com.demo.dto.mq.EventMessage;
import com.demo.dto.mq.ProductEventType;
import com.demo.dto.mq.ProductReportResolvedPayload;
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
 * Day16 - 举报工单处理结果通知消费者。
 *
 * 消费事件：
 * - PRODUCT_REPORT_RESOLVED
 *
 * 处理结果：
 * - 给举报人发送“举报成立/不成立”结果通知
 */
@Slf4j
@Component
public class ProductReportResolvedNoticeConsumer {

    private static final String CONSUMER_NAME = "ProductReportResolvedNoticeConsumer";

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
    @Value("${product.notice.report-resolved-enabled:true}")
    private boolean reportResolvedNoticeEnabled;

    @RabbitListener(queues = "${demo.rabbitmq.queue.product-report-resolved-notice}")
    public void onMessage(EventMessage<ProductReportResolvedPayload> message,
                          Channel channel,
                          Message amqpMessage) throws Exception {
        long tag = amqpMessage.getMessageProperties().getDeliveryTag();
        MqConsumeLog logRecord = null;
        try {
            if (message == null || message.getPayload() == null) {
                log.warn("举报处理结果通知消息体为空，ACK 丢弃。");
                channel.basicAck(tag, false);
                return;
            }
            if (message.getEventId() == null || message.getEventId().trim().isEmpty()) {
                log.warn("举报处理结果通知缺少 eventId，ACK 丢弃。");
                channel.basicAck(tag, false);
                return;
            }
            if (!ProductEventType.PRODUCT_REPORT_RESOLVED.getCode().equals(message.getEventType())) {
                log.warn("举报处理结果通知事件类型不匹配：eventType={}，ACK 丢弃。", message.getEventType());
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
                log.info("幂等命中：consumer=ProductReportResolvedNoticeConsumer, eventId={}", message.getEventId());
                channel.basicAck(tag, false);
                return;
            }

            ProductReportResolvedPayload payload = message.getPayload();
            if (payload.getReporterId() == null || payload.getProductId() == null) {
                mqConsumeLogMapper.updateStatus(logRecord.getId(), "OK");
                channel.basicAck(tag, false);
                return;
            }
            if (!reportResolvedNoticeEnabled) {
                log.info("举报处理结果通知开关关闭，跳过发送：eventId={}", message.getEventId());
                mqConsumeLogMapper.updateStatus(logRecord.getId(), "OK");
                channel.basicAck(tag, false);
                return;
            }

            String content = noticeContentBuilder.buildReportResolvedNotice(payload);
            systemNoticeService.sendNotice(
                    payload.getReporterId(),
                    "SYS-PRODUCT-REPORT-RESOLVED-" + message.getEventId(),
                    content
            );

            mqConsumeLogMapper.updateStatus(logRecord.getId(), "OK");
            channel.basicAck(tag, false);
        } catch (BusinessException ex) {
            if (logRecord != null && logRecord.getId() != null) {
                mqConsumeLogMapper.updateStatus(logRecord.getId(), "OK");
            }
            log.warn("举报处理结果通知业务异常，ACK 丢弃。msg={}, err={}",
                    message, ex.getMessage());
            channel.basicAck(tag, false);
        } catch (Exception ex) {
            if (logRecord != null && logRecord.getId() != null) {
                mqConsumeLogMapper.updateStatus(logRecord.getId(), "FAIL");
            }
            log.error("举报处理结果通知处理失败，NACK 进入 DLQ。msg={}", message, ex);
            channel.basicNack(tag, false, false);
        }
    }
}
