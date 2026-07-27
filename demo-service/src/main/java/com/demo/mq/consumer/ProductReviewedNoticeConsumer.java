package com.demo.mq.consumer;

import com.demo.dto.mq.EventMessage;
import com.demo.dto.mq.ProductEventType;
import com.demo.dto.mq.ProductReviewedPayload;
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

@Slf4j
@Component
public class ProductReviewedNoticeConsumer {

    private static final String NAME = "ProductReviewedNoticeConsumer";
    @Autowired private MqConsumeGuard guard;
    @Autowired private SystemNoticeService noticeService;
    @Autowired private ProductNoticeContentBuilder contentBuilder;
    @Value("${product.notice.reviewed-enabled:true}") private boolean enabled;

    @RabbitListener(queues = "${demo.rabbitmq.queue.product-reviewed-notice}")
    public void onMessage(EventMessage<ProductReviewedPayload> envelope, Channel channel, Message amqpMessage) throws Exception {
        long tag = amqpMessage.getMessageProperties().getDeliveryTag();

        if (envelope == null || envelope.getPayload() == null || isEmpty(envelope.getEventId())) {
            channel.basicAck(tag, false);
            return;
        }
        if (!ProductEventType.PRODUCT_REVIEWED.getCode().equals(envelope.getEventType())) {
            channel.basicAck(tag, false);
            return;
        }

        MqConsumeGuard.AcquireResult ar = guard.acquire(NAME, envelope.getEventId());
        String lease = ar.leaseToken();
        switch (ar.type()) {
            case ALREADY_COMPLETED: channel.basicAck(tag, false); return;
            case IN_PROGRESS_RECENT: case UNKNOWN_STATE_RETRY: channel.basicNack(tag, false, true); return;
            case ACQUIRED_NEW: case RECOVERED_STALE: case RETRYABLE_FAILED: break;
        }

        try {
            ProductReviewedPayload p = envelope.getPayload();
            if (p.getOwnerId() == null || p.getProductId() == null) {
                guard.markSuccess(ar.logId(), lease);
                channel.basicAck(tag, false);
                return;
            }
            if (!enabled) {
                guard.markSuccess(ar.logId(), lease);
                channel.basicAck(tag, false);
                return;
            }
            String content = contentBuilder.buildReviewedNotice(p);
            noticeService.sendNotice(p.getOwnerId(), "SYS-PRODUCT-REVIEWED-" + envelope.getEventId(), content);

            guard.markSuccess(ar.logId(), lease);
            ackSafely(channel, tag, NAME, envelope.getEventId());
        } catch (BusinessException ex) {
            guard.markSuccess(ar.logId(), lease);
            log.warn("PRODUCT_REVIEWED business ex eventId={}", envelope.getEventId(), ex);
            channel.basicAck(tag, false);
        } catch (Exception ex) {
            guard.markFailure(ar.logId(), lease, ex.getMessage());
            log.error("PRODUCT_REVIEWED fail eventId={}", envelope.getEventId(), ex);
            channel.basicNack(tag, false, false);
        }
    }

    private void ackSafely(Channel c, long t, String n, String e) { try { c.basicAck(t, false); } catch (Exception ex) { log.warn("{} ACK fail eventId={}", n, e, ex); } }
    private static boolean isEmpty(String s) { return s == null || s.trim().isEmpty(); }
}
