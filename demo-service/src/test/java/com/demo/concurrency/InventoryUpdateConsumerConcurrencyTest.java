package com.demo.concurrency;

import com.demo.dto.mq.EventMessage;
import com.demo.dto.mq.OrderCreatedPayload;
import com.demo.entity.MqConsumeLog;
import com.demo.entity.Order;
import com.demo.mapper.MqConsumeLogMapper;
import com.demo.mapper.OrderMapper;
import com.demo.mq.consumer.InventoryUpdateConsumer;
import com.demo.service.MqConsumeGuard;
import com.rabbitmq.client.Channel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InventoryUpdateConsumerConcurrencyTest {

    @Mock private OrderMapper orderMapper;
    @Mock private MqConsumeLogMapper mqConsumeLogMapper;
    @Mock private MqConsumeGuard guard;
    @Mock private Channel channel;
    @InjectMocks private InventoryUpdateConsumer consumer;

    private static final String TEST_LEASE = "test-lease-token";

    private void stubAcquiredNew() {
        when(guard.acquire(anyString(), anyString()))
                .thenReturn(new MqConsumeGuard.AcquireResult(
                        MqConsumeGuard.AcquireResult.Type.ACQUIRED_NEW, 1L, TEST_LEASE));
    }

    @Test
    void shouldSkipMarkSoldWhenOrderAlreadyCancelled() throws Exception {
        stubAcquiredNew();
        EventMessage<OrderCreatedPayload> msg = buildMsg("evt-cancelled", 90001L, 80001L);
        Message amqp = buildAmqp(11L);
        Order order = new Order(); order.setId(90001L); order.setStatus("cancelled");
        when(orderMapper.selectOrderBasicById(90001L)).thenReturn(order);

        consumer.onMessage(msg, channel, amqp);

        verify(orderMapper, never()).markProductSoldIfOnSale(any());
        verify(guard).markSuccess(1L, TEST_LEASE);
        verify(channel).basicAck(11L, false);
    }

    @Test
    void shouldMarkSoldWhenOrderStillActive() throws Exception {
        stubAcquiredNew();
        EventMessage<OrderCreatedPayload> msg = buildMsg("evt-pending", 90002L, 80002L);
        Message amqp = buildAmqp(12L);
        Order order = new Order(); order.setId(90002L); order.setStatus("pending");
        when(orderMapper.selectOrderBasicById(90002L)).thenReturn(order);
        when(orderMapper.markProductSoldIfOnSale(80002L)).thenReturn(1);

        consumer.onMessage(msg, channel, amqp);

        verify(orderMapper).markProductSoldIfOnSale(80002L);
        verify(guard).markSuccess(1L, TEST_LEASE);
        verify(channel).basicAck(12L, false);
    }

    private EventMessage<OrderCreatedPayload> buildMsg(String eid, Long oid, Long pid) {
        OrderCreatedPayload p = new OrderCreatedPayload(); p.setOrderId(oid); p.setProductId(pid);
        EventMessage<OrderCreatedPayload> m = new EventMessage<>(); m.setEventId(eid); m.setPayload(p);
        return m;
    }
    private Message buildAmqp(long tag) {
        MessageProperties mp = new MessageProperties(); mp.setDeliveryTag(tag);
        return new Message(new byte[0], mp);
    }
}
