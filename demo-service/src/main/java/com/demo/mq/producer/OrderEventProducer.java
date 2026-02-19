package com.demo.mq.producer;

import com.demo.dto.mq.EventMessage;
import com.demo.dto.mq.OrderCreatedPayload;
import com.demo.dto.mq.OrderEventType;
import com.demo.dto.mq.OrderPaidPayload;
import com.demo.dto.mq.OrderTimeoutPayload;
import com.demo.entity.Order;
import com.demo.entity.Product;
import com.demo.dto.mq.OrderStatusChangedPayload;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;


/**
 * Day14 - 订单事件生产者
 */
@Slf4j
@Component
public class OrderEventProducer {

    /** RabbitMQ 发送模板 */
    private final RabbitTemplate rabbitTemplate;

    /** 订单事件交换机名 */
    @Value("${demo.rabbitmq.exchange.order-events}")
    private String orderEventsExchange;

    /** 订单创建事件路由键 */
    @Value("${demo.rabbitmq.routing-key.order-created}")
    private String orderCreatedRoutingKey;

    /** 订单超时延迟路由键 */
    @Value("${demo.rabbitmq.routing-key.order-timeout-delay}")
    private String orderTimeoutDelayRoutingKey;

    /** 订单超时延迟时间 */
    @Value("${order.timeout.pending-minutes:15}")
    private Integer pendingMinutes;

    /** 订单支付路由键 */
    @Value("${demo.rabbitmq.routing-key.order-paid}")
    private String orderPaidRoutingKey;

    /** 订单状态变更事件路由键 */
    @Value("${demo.rabbitmq.routing-key.order-status-changed}")
    private String orderStatusChangedRoutingKey;


    /** 构造注入 RabbitTemplate */
    /**
     * 构造函数，初始化当前组件依赖。
     */
    public OrderEventProducer(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    /**
     * 发送订单创建事件
     * @param order  订单实体
     * @param product 商品实体（用于补充商品信息）
     */
    public void sendOrderCreated(Order order, Product product) {
        /** 1) 构造事件载荷（业务数据） */
        OrderCreatedPayload payload = new OrderCreatedPayload();
        payload.setOrderId(order.getId());
        payload.setOrderNo(order.getOrderNo());
        payload.setBuyerId(order.getBuyerId());
        payload.setSellerId(order.getSellerId());
        payload.setProductId(product.getId());
        payload.setQuantity(1);
        payload.setPrice(product.getPrice());
        payload.setTotalAmount(order.getTotalAmount());
        payload.setCreateTime(LocalDateTime.now());

        /** 2) 构造统一事件信封 */
        EventMessage<OrderCreatedPayload> message = new EventMessage<>();
        message.setEventId(UUID.randomUUID().toString());
        message.setEventType(OrderEventType.ORDER_CREATED.getCode());
        message.setRoutingKey(orderCreatedRoutingKey);
        message.setBizId(order.getId());
        message.setOccurredAt(LocalDateTime.now());
        message.setPayload(payload);

        /** 3) 发送到交换机 */
        rabbitTemplate.convertAndSend(orderEventsExchange, orderCreatedRoutingKey, message);
        log.info("Send ORDER_CREATED event, orderId={}", order.getId());
    }

 /**
 * 发送订单支付事件
 *
 * @param order     订单实体
 * @param payAmount 支付金额
 * @param payMethod 支付渠道（如 alipay/wechat/mock）
 */
public void sendOrderPaid(Order order, BigDecimal payAmount, String payMethod) {
    /** 1) 构造事件载荷（业务数据） */
    OrderPaidPayload payload = new OrderPaidPayload();
    payload.setOrderId(order.getId());
    payload.setOrderNo(order.getOrderNo());
    payload.setBuyerId(order.getBuyerId());
    payload.setPayAmount(payAmount);
    payload.setPayMethod(payMethod);
    payload.setPayTime(LocalDateTime.now());

    /** 2) 构造统一事件信封 */
    EventMessage<OrderPaidPayload> message = new EventMessage<>();
    message.setEventId(UUID.randomUUID().toString());
    message.setEventType(OrderEventType.ORDER_PAID.getCode());
    message.setRoutingKey(orderPaidRoutingKey);
    message.setBizId(order.getId());
    message.setOccurredAt(LocalDateTime.now());
    message.setPayload(payload);

    /** 3) 发送到交换机 */
    rabbitTemplate.convertAndSend(orderEventsExchange, orderPaidRoutingKey, message);
    log.info("Send ORDER_PAID event, orderId={}", order.getId());
}



   /**
 * 发送订单超时延迟消息（TTL -> DLX -> order.timeout.queue）
 *
 * @param order 订单实体
 */
public void sendOrderTimeoutDelay(Order order) {
    /** 1) 构造事件载荷（业务数据） */
    OrderTimeoutPayload payload = new OrderTimeoutPayload();
    payload.setOrderId(order.getId());
    payload.setOrderNo(order.getOrderNo());
    payload.setBuyerId(order.getBuyerId());
    payload.setPendingMinutes(pendingMinutes);
    payload.setTimeoutAt(LocalDateTime.now().plusMinutes(pendingMinutes));
    payload.setReason("timeout");

    /** 2) 构造统一事件信封 */
    EventMessage<OrderTimeoutPayload> message = new EventMessage<>();
    message.setEventId(UUID.randomUUID().toString());
    message.setEventType(OrderEventType.ORDER_TIMEOUT.getCode());
    message.setRoutingKey(orderTimeoutDelayRoutingKey);
    message.setBizId(order.getId());
    message.setOccurredAt(LocalDateTime.now());
    message.setPayload(payload);

    /** 3) 发送到交换机 */
    rabbitTemplate.convertAndSend(orderEventsExchange, orderTimeoutDelayRoutingKey, message);
    log.info("Send ORDER_TIMEOUT delay event, orderId={}, ttlMinutes={}", order.getId(), pendingMinutes);
}

/**
 * 发送订单状态变更事件
 *
 * @param orderId    订单 ID
 * @param orderNo    订单号
 * @param oldStatus  变更前状态（dbValue）
 * @param newStatus  变更后状态（dbValue）
 * @param operatorId 操作人 ID（买家/卖家）
 */
public void sendOrderStatusChanged(Long orderId,
    String orderNo,
    String oldStatus,
    String newStatus,
    Long operatorId) {
/** 1) 构造事件载荷（业务数据） */
OrderStatusChangedPayload payload = new OrderStatusChangedPayload();
payload.setOrderId(orderId);
payload.setOrderNo(orderNo);
payload.setOldStatus(oldStatus);
payload.setNewStatus(newStatus);
payload.setOperatorId(operatorId);
payload.setChangeTime(LocalDateTime.now());

/** 2) 构造统一事件信封 */
EventMessage<OrderStatusChangedPayload> message = new EventMessage<>();
message.setEventId(UUID.randomUUID().toString());
message.setEventType(OrderEventType.ORDER_STATUS_CHANGED.getCode());
message.setRoutingKey(orderStatusChangedRoutingKey);
message.setBizId(orderId);
message.setOccurredAt(LocalDateTime.now());
message.setPayload(payload);

/** 3) 发送到交换机 */
rabbitTemplate.convertAndSend(orderEventsExchange, orderStatusChangedRoutingKey, message);
log.info("Send ORDER_STATUS_CHANGED event, orderId={}, {} -> {}", orderId, oldStatus, newStatus);
}


}
