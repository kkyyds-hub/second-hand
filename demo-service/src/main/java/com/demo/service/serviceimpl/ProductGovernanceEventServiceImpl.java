package com.demo.service.serviceimpl;

import com.demo.dto.mq.EventMessage;
import com.demo.dto.mq.ProductEventType;
import com.demo.dto.mq.ProductForceOffShelfPayload;
import com.demo.dto.mq.ProductReportResolvedPayload;
import com.demo.dto.mq.ProductReviewedPayload;
import com.demo.entity.MessageOutbox;
import com.demo.entity.Product;
import com.demo.entity.ProductReportTicket;
import com.demo.exception.BusinessException;
import com.demo.service.OutboxService;
import com.demo.service.ProductGovernanceEventService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Day16 - 商品治理事件出站实现。
 *
 * 设计说明：
 * 1) 仅负责“事件对象 -> Outbox 行”的转换与落库。
 * 2) 不直接发送 MQ，发送由 OutboxPublishJob 统一处理。
 * 3) 与业务服务同事务运行，保证“业务成功 + 事件可追溯”原子性。
 */
@Slf4j
@Service
public class ProductGovernanceEventServiceImpl implements ProductGovernanceEventService {

    @Autowired
    private OutboxService outboxService;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * 复用现有 order-events 交换机，避免 Day16 额外引入新交换机。
     */
    @Value("${demo.rabbitmq.exchange.order-events:order.events.exchange}")
    private String governanceExchange;

    @Value("${demo.rabbitmq.routing-key.product-reviewed:product.reviewed}")
    private String productReviewedRoutingKey;

    @Value("${demo.rabbitmq.routing-key.product-force-off-shelf:product.force.off.shelf}")
    private String productForceOffShelfRoutingKey;

    @Value("${demo.rabbitmq.routing-key.product-report-resolved:product.report.resolved}")
    private String productReportResolvedRoutingKey;

    @Override
    public void publishProductReviewed(Product product,
                                       String reviewAction,
                                       String beforeStatus,
                                       String afterStatus,
                                       String reasonText) {
        if (product == null || product.getId() == null || product.getOwnerId() == null) {
            throw new BusinessException("发布 PRODUCT_REVIEWED 事件失败：商品关键信息缺失");
        }

        ProductReviewedPayload payload = new ProductReviewedPayload();
        payload.setProductId(product.getId());
        payload.setOwnerId(product.getOwnerId());
        payload.setReviewAction(reviewAction);
        payload.setBeforeStatus(beforeStatus);
        payload.setAfterStatus(afterStatus);
        payload.setReasonText(reasonText);
        payload.setReviewedAt(LocalDateTime.now());

        saveOutbox(
                ProductEventType.PRODUCT_REVIEWED,
                productReviewedRoutingKey,
                product.getId(),
                payload
        );
    }

    @Override
    public void publishProductForceOffShelf(Product product,
                                            Long operatorId,
                                            String beforeStatus,
                                            String afterStatus,
                                            String reasonCode,
                                            String reasonText,
                                            String reportTicketNo) {
        if (product == null || product.getId() == null || product.getOwnerId() == null) {
            throw new BusinessException("发布 PRODUCT_FORCE_OFF_SHELF 事件失败：商品关键信息缺失");
        }

        ProductForceOffShelfPayload payload = new ProductForceOffShelfPayload();
        payload.setProductId(product.getId());
        payload.setOwnerId(product.getOwnerId());
        payload.setOperatorId(operatorId);
        payload.setBeforeStatus(beforeStatus);
        payload.setAfterStatus(afterStatus);
        payload.setReasonCode(reasonCode);
        payload.setReasonText(reasonText);
        payload.setReportTicketNo(reportTicketNo);
        payload.setForcedAt(LocalDateTime.now());

        saveOutbox(
                ProductEventType.PRODUCT_FORCE_OFF_SHELF,
                productForceOffShelfRoutingKey,
                product.getId(),
                payload
        );
    }

    @Override
    public void publishProductReportResolved(ProductReportTicket ticket,
                                             Long resolverId,
                                             String resolveAction,
                                             String targetStatus,
                                             String remark) {
        if (ticket == null || ticket.getProductId() == null || ticket.getReporterId() == null) {
            throw new BusinessException("发布 PRODUCT_REPORT_RESOLVED 事件失败：工单关键信息缺失");
        }

        ProductReportResolvedPayload payload = new ProductReportResolvedPayload();
        payload.setTicketNo(ticket.getTicketNo());
        payload.setProductId(ticket.getProductId());
        payload.setReporterId(ticket.getReporterId());
        payload.setResolverId(resolverId);
        payload.setResolveAction(resolveAction);
        payload.setTargetStatus(targetStatus);
        payload.setRemark(remark);
        payload.setResolvedAt(LocalDateTime.now());

        saveOutbox(
                ProductEventType.PRODUCT_REPORT_RESOLVED,
                productReportResolvedRoutingKey,
                ticket.getProductId(),
                payload
        );
    }

    /**
     * 统一 Outbox 入库模板。
     */
    private void saveOutbox(ProductEventType eventType,
                            String routingKey,
                            Long bizId,
                            Object payload) {
        EventMessage<Object> eventMessage = new EventMessage<>();
        eventMessage.setEventId(UUID.randomUUID().toString());
        eventMessage.setEventType(eventType.getCode());
        eventMessage.setRoutingKey(routingKey);
        eventMessage.setBizId(bizId);
        eventMessage.setOccurredAt(LocalDateTime.now());
        eventMessage.setPayload(payload);

        MessageOutbox outbox = new MessageOutbox();
        outbox.setEventId(eventMessage.getEventId());
        outbox.setEventType(eventMessage.getEventType());
        outbox.setRoutingKey(eventMessage.getRoutingKey());
        outbox.setExchangeName(governanceExchange);
        outbox.setBizId(eventMessage.getBizId());
        outbox.setPayloadJson(toJsonSafely(eventMessage));
        outbox.setStatus("NEW");
        outbox.setRetryCount(0);
        outbox.setNextRetryTime(null);

        outboxService.save(outbox);
        log.info("商品治理事件写入 Outbox 成功：eventType={}, bizId={}, eventId={}",
                eventType.getCode(), bizId, eventMessage.getEventId());
    }

    /**
     * JSON 序列化兜底：失败直接抛业务异常，触发事务回滚，避免“业务成功但消息丢失”。
     */
    private String toJsonSafely(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new BusinessException("商品治理事件序列化失败");
        }
    }
}
