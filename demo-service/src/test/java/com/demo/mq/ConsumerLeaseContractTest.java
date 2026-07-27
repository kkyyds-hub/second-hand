package com.demo.mq;

import com.demo.dto.mq.*;
import com.demo.entity.Order;
import com.demo.enumeration.OrderStatus;
import com.demo.mapper.OrderMapper;
import com.demo.mq.consumer.*;
import com.demo.service.*;
import com.demo.service.support.ProductNoticeContentBuilder;
import com.rabbitmq.client.Channel;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

import java.io.IOException;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@MockitoSettings(strictness = Strictness.LENIENT)
/**
 * 7-consumer Guard contract test.
 *
 * Covers for every consumer:
 *   ALREADY_COMPLETED → ACK, no business
 *   IN_PROGRESS_RECENT → NACK requeue, no business
 *   UNKNOWN_STATE_RETRY → NACK requeue, no business
 *
 * ACK failure test for: OrderPaidConsumer, InventoryUpdateConsumer, ProductReviewedNoticeConsumer
 */
@ExtendWith(MockitoExtension.class)
class ConsumerLeaseContractTest {

    private static final String TEST_LEASE = "test-lease-token";
    private static final String TEST_EID = "test-event-id";

    @Mock private MqConsumeGuard guard;
    @Mock private Channel channel;

    private Message amqpMsg(long tag) {
        MessageProperties mp = new MessageProperties();
        mp.setDeliveryTag(tag);
        return new Message(new byte[0], mp);
    }

    private void stubGuard(MqConsumeGuard.AcquireResult.Type type, Long logId, String lease) {
        when(guard.acquire(anyString(), eq(TEST_EID)))
                .thenReturn(new MqConsumeGuard.AcquireResult(type, logId, lease));
    }

    // ================================================================
    // Generic Guard result tests (parameterized by consumer factory)
    // ================================================================

    interface ConsumerRunner {
        void run(EventMessage<?> msg, Channel ch, Message amqp) throws Exception;
    }

    /** Core: ALREADY_COMPLETED → ACK, no business */
    void verifyAlreadyCompletedAcks(ConsumerRunner runner) throws Exception {
        stubGuard(MqConsumeGuard.AcquireResult.Type.ALREADY_COMPLETED, 1L, null);
        runner.run(dummyMsg(), channel, amqpMsg(1L));
        verify(channel).basicAck(1L, false);
        verify(guard, never()).markSuccess(anyLong(), anyString());
        verify(guard, never()).markFailure(anyLong(), anyString(), anyString());
    }

    /** Core: IN_PROGRESS_RECENT → NACK requeue, no business */
    void verifyInProgressRecentNacks(ConsumerRunner runner) throws Exception {
        stubGuard(MqConsumeGuard.AcquireResult.Type.IN_PROGRESS_RECENT, 2L, null);
        runner.run(dummyMsg(), channel, amqpMsg(2L));
        verify(channel).basicNack(2L, false, true);
        verify(guard, never()).markSuccess(anyLong(), anyString());
    }

    /** Core: UNKNOWN_STATE_RETRY → NACK requeue, no business */
    void verifyUnknownStateNacks(ConsumerRunner runner) throws Exception {
        stubGuard(MqConsumeGuard.AcquireResult.Type.UNKNOWN_STATE_RETRY, 3L, null);
        runner.run(dummyMsg(), channel, amqpMsg(3L));
        verify(channel).basicNack(3L, false, true);
        verify(guard, never()).markSuccess(anyLong(), anyString());
    }

    // ================================================================
    // OrderPaidConsumer tests
    // ================================================================
    @Mock private OrderMapper orderMapper;
    @Mock private MessageService messageService;
    @Mock private OrderShipReminderTaskService shipReminderTaskService;

    private OrderPaidConsumer paidConsumer() {
        OrderPaidConsumer c = new OrderPaidConsumer();
        setField(c, "orderMapper", orderMapper);
        setField(c, "messageService", messageService);
        setField(c, "shipReminderTaskService", shipReminderTaskService);
        setField(c, "guard", guard);
        setField(c, "paidNotifySellerEnabled", false);
        return c;
    }

    @Test @DisplayName("OrderPaidConsumer: ALREADY_COMPLETED → ACK")
    void paidConsumerAlreadyCompleted() throws Exception {
        verifyAlreadyCompletedAcks((m,ch,am) -> paidConsumer().onMessage((EventMessage<OrderPaidPayload>)m,ch,am));
    }
    @Test @DisplayName("OrderPaidConsumer: IN_PROGRESS_RECENT → NACK")
    void paidConsumerInProgress() throws Exception {
        verifyInProgressRecentNacks((m,ch,am) -> paidConsumer().onMessage((EventMessage<OrderPaidPayload>)m,ch,am));
    }
    @Test @DisplayName("OrderPaidConsumer: UNKNOWN_STATE_RETRY → NACK")
    void paidConsumerUnknown() throws Exception {
        verifyUnknownStateNacks((m,ch,am) -> paidConsumer().onMessage((EventMessage<OrderPaidPayload>)m,ch,am));
    }

    @Test @DisplayName("OrderPaidConsumer: ACK failure keeps status OK")
    void paidConsumerAckFailure() throws Exception {
        stubGuard(MqConsumeGuard.AcquireResult.Type.ACQUIRED_NEW, 10L, TEST_LEASE);
        OrderPaidConsumer c = paidConsumer();
        setField(c, "paidNotifySellerEnabled", false);

        Order order = new Order(); order.setId(100L); order.setStatus("paid"); order.setSellerId(200L); order.setBuyerId(300L);
        when(orderMapper.selectOrderBasicById(anyLong())).thenReturn(order);
        doThrow(new IOException("ack failed")).when(channel).basicAck(eq(10L), eq(false));

        c.onMessage(paidMsg(), channel, amqpMsg(10L));

        verify(guard).markSuccess(10L, TEST_LEASE);
        verify(guard, never()).markFailure(anyLong(), anyString(), anyString());
    }

    // ================================================================
    // InventoryUpdateConsumer tests
    // ================================================================
    private InventoryUpdateConsumer invConsumer() {
        InventoryUpdateConsumer c = new InventoryUpdateConsumer();
        setField(c, "orderMapper", orderMapper);
        setField(c, "guard", guard);
        return c;
    }

    @Test @DisplayName("InventoryUpdateConsumer: ALREADY_COMPLETED → ACK")
    void invConsumerAlreadyCompleted() throws Exception {
        verifyAlreadyCompletedAcks((m,ch,am) -> invConsumer().onMessage((EventMessage<OrderCreatedPayload>)m,ch,am));
    }
    @Test @DisplayName("InventoryUpdateConsumer: ACK failure keeps OK")
    void invConsumerAckFailure() throws Exception {
        stubGuard(MqConsumeGuard.AcquireResult.Type.ACQUIRED_NEW, 20L, TEST_LEASE);
        InventoryUpdateConsumer c = invConsumer();

        Order order = new Order(); order.setId(200L); order.setStatus("pending");
        when(orderMapper.selectOrderBasicById(anyLong())).thenReturn(order);
        when(orderMapper.markProductSoldIfOnSale(anyLong())).thenReturn(1);
        doThrow(new IOException("ack failed")).when(channel).basicAck(eq(20L), eq(false));

        c.onMessage(createdMsg(), channel, amqpMsg(20L));

        verify(guard).markSuccess(20L, TEST_LEASE);
        verify(guard, never()).markFailure(anyLong(), anyString(), anyString());
    }

    // ================================================================
    // ProductReviewedNoticeConsumer tests
    // ================================================================
    @Mock private SystemNoticeService noticeService;
    @Mock private ProductNoticeContentBuilder contentBuilder;

    private ProductReviewedNoticeConsumer reviewedConsumer() {
        ProductReviewedNoticeConsumer c = new ProductReviewedNoticeConsumer();
        setField(c, "guard", guard);
        setField(c, "noticeService", noticeService);
        setField(c, "contentBuilder", contentBuilder);
        setField(c, "enabled", true);
        return c;
    }

    @Test @DisplayName("ProductReviewedNoticeConsumer: ALREADY_COMPLETED → ACK")
    void reviewedConsumerAlreadyCompleted() throws Exception {
        verifyAlreadyCompletedAcks((m,ch,am) -> reviewedConsumer().onMessage((EventMessage<ProductReviewedPayload>)m,ch,am));
    }
    @Test @DisplayName("ProductReviewedNoticeConsumer: ACK failure keeps OK")
    void reviewedConsumerAckFailure() throws Exception {
        stubGuard(MqConsumeGuard.AcquireResult.Type.ACQUIRED_NEW, 30L, TEST_LEASE);
        ProductReviewedNoticeConsumer c = reviewedConsumer();
        when(contentBuilder.buildReviewedNotice(any())).thenReturn("test notice");
        doThrow(new IOException("ack failed")).when(channel).basicAck(eq(30L), eq(false));

        c.onMessage(reviewedMsg(), channel, amqpMsg(30L));

        verify(guard).markSuccess(30L, TEST_LEASE);
        verify(guard, never()).markFailure(anyLong(), anyString(), anyString());
    }

    // ================================================================
    // OrderTimeoutConsumer tests
    // ================================================================
    @Mock private OrderTimeoutService orderTimeoutService;

    @Test @DisplayName("OrderTimeoutConsumer: ALREADY_COMPLETED → ACK")
    void timeoutConsumerAlreadyCompleted() throws Exception {
        OrderTimeoutConsumer c = new OrderTimeoutConsumer();
        setField(c, "orderTimeoutService", orderTimeoutService);
        setField(c, "guard", guard);
        verifyAlreadyCompletedAcks((m,ch,am) -> c.onMessage((EventMessage<OrderTimeoutPayload>)m,ch,am));
    }
    @Test @DisplayName("OrderTimeoutConsumer: IN_PROGRESS_RECENT → NACK")
    void timeoutConsumerInProgress() throws Exception {
        OrderTimeoutConsumer c = new OrderTimeoutConsumer();
        setField(c, "orderTimeoutService", orderTimeoutService);
        setField(c, "guard", guard);
        verifyInProgressRecentNacks((m,ch,am) -> c.onMessage((EventMessage<OrderTimeoutPayload>)m,ch,am));
    }

    // ================================================================
    // OrderStatusChangedConsumer tests
    // ================================================================
    @Test @DisplayName("OrderStatusChangedConsumer: ALREADY_COMPLETED → ACK")
    void statusChangedConsumerAlreadyCompleted() throws Exception {
        OrderStatusChangedConsumer c = new OrderStatusChangedConsumer();
        setField(c, "orderMapper", orderMapper);
        setField(c, "messageService", messageService);
        setField(c, "guard", guard);
        setField(c, "enabled", false);
        verifyAlreadyCompletedAcks((m,ch,am) -> c.onMessage((EventMessage<OrderStatusChangedPayload>)m,ch,am));
    }

    // ================================================================
    // ProductReportResolvedNoticeConsumer tests
    // ================================================================
    @Test @DisplayName("ProductReportResolvedNoticeConsumer: ALREADY_COMPLETED → ACK")
    void reportResolvedConsumerAlreadyCompleted() throws Exception {
        ProductReportResolvedNoticeConsumer c = new ProductReportResolvedNoticeConsumer();
        setField(c, "guard", guard);
        setField(c, "noticeService", noticeService);
        setField(c, "contentBuilder", contentBuilder);
        setField(c, "enabled", false);
        verifyAlreadyCompletedAcks((m,ch,am) -> c.onMessage((EventMessage<ProductReportResolvedPayload>)m,ch,am));
    }

    // ================================================================
    // ProductForceOffShelfNoticeConsumer tests
    // ================================================================
    @Test @DisplayName("ProductForceOffShelfNoticeConsumer: ALREADY_COMPLETED → ACK")
    void forceOffShelfConsumerAlreadyCompleted() throws Exception {
        ProductForceOffShelfNoticeConsumer c = new ProductForceOffShelfNoticeConsumer();
        setField(c, "guard", guard);
        setField(c, "noticeService", noticeService);
        setField(c, "contentBuilder", contentBuilder);
        setField(c, "enabled", false);
        verifyAlreadyCompletedAcks((m,ch,am) -> c.onMessage((EventMessage<ProductForceOffShelfPayload>)m,ch,am));
    }

    // ================================================================
    // Helpers
    // ================================================================
    private EventMessage<?> dummyMsg() {
        EventMessage<OrderPaidPayload> m = new EventMessage<>();
        m.setEventId(TEST_EID);
        OrderPaidPayload p = new OrderPaidPayload();
        p.setOrderId(1L); p.setOrderNo("N1"); p.setBuyerId(2L);
        m.setPayload(p);
        return m;
    }

    private EventMessage<OrderPaidPayload> paidMsg() {
        EventMessage<OrderPaidPayload> m = new EventMessage<>();
        m.setEventId(TEST_EID);
        OrderPaidPayload p = new OrderPaidPayload();
        p.setOrderId(100L); p.setOrderNo("ORD-100"); p.setBuyerId(300L);
        m.setPayload(p);
        return m;
    }

    private EventMessage<OrderCreatedPayload> createdMsg() {
        EventMessage<OrderCreatedPayload> m = new EventMessage<>();
        m.setEventId(TEST_EID);
        OrderCreatedPayload p = new OrderCreatedPayload();
        p.setOrderId(200L); p.setProductId(50L);
        m.setPayload(p);
        return m;
    }

    private EventMessage<ProductReviewedPayload> reviewedMsg() {
        EventMessage<ProductReviewedPayload> m = new EventMessage<>();
        m.setEventId(TEST_EID);
        m.setEventType(ProductEventType.PRODUCT_REVIEWED.getCode());
        ProductReviewedPayload p = new ProductReviewedPayload();
        p.setOwnerId(10L); p.setProductId(20L);
        m.setPayload(p);
        return m;
    }

    private void setField(Object obj, String name, Object value) {
        try {
            java.lang.reflect.Field f = obj.getClass().getDeclaredField(name);
            f.setAccessible(true);
            f.set(obj, value);
        } catch (Exception e) { throw new RuntimeException(e); }
    }
}
