package com.demo.concurrency;

import com.demo.dto.mq.EventMessage;
import com.demo.dto.mq.OrderCreatedPayload;
import com.demo.entity.MqConsumeLog;
import com.demo.entity.Order;
import com.demo.mapper.MqConsumeLogMapper;
import com.demo.mapper.OrderMapper;
import com.demo.mq.consumer.InventoryUpdateConsumer;
import com.rabbitmq.client.Channel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Day19 P5-S1：库存更新消费者并发回写兜底回归。
 *
 * 这组测试专门验证本次修复的两个核心语义：
 * 1) 如果 `ORDER_CREATED` 消息到达时，订单已经进入 `cancelled`，
 *    消费者必须跳过 `markProductSoldIfOnSale`，避免把已释放的商品重新打回 `sold`；
 * 2) 如果订单仍处于有效态（例如 `pending`），消费者仍应按原设计继续执行库存回写，
 *    证明本次保护没有误伤正常链路。
 */
@ExtendWith(MockitoExtension.class)
class InventoryUpdateConsumerConcurrencyTest {

    @Mock
    private OrderMapper orderMapper;
    @Mock
    private MqConsumeLogMapper mqConsumeLogMapper;
    @Mock
    private Channel channel;

    @InjectMocks
    private InventoryUpdateConsumer consumer;

    @Test
    void shouldSkipMarkSoldWhenOrderAlreadyCancelled() throws Exception {
        // 构造一个“历史上的订单创建事件”：
        // 该消息本身合法，但它到达消费者时，订单已经被别的链路取消。
        EventMessage<OrderCreatedPayload> message = buildMessage("evt-cancelled", 90001L, 80001L);
        Message amqpMessage = buildAmqpMessage(11L);
        Order order = new Order();
        order.setId(90001L);
        order.setStatus("cancelled");

        doAnswer(invocation -> {
            MqConsumeLog log = invocation.getArgument(0);
            log.setId(101L);
            return 1;
        }).when(mqConsumeLogMapper).insert(any(MqConsumeLog.class));
        when(orderMapper.selectOrderBasicById(90001L)).thenReturn(order);

        consumer.onMessage(message, channel, amqpMessage);

        // 断言重点：
        // 1) 不再回写商品为 sold；
        // 2) 消息被视为“已正确处理”，因此记录 OK 并 ACK；
        // 3) 这说明我们处理的是“过时消息”，而不是“异常消息”。
        verify(orderMapper, never()).markProductSoldIfOnSale(any());
        verify(mqConsumeLogMapper).updateStatus(101L, "OK");
        verify(channel).basicAck(11L, false);
    }

    @Test
    void shouldMarkSoldWhenOrderStillActive() throws Exception {
        // 构造一个正常场景：消息到达时订单仍然有效，消费者应继续按旧语义推进库存状态。
        EventMessage<OrderCreatedPayload> message = buildMessage("evt-pending", 90002L, 80002L);
        Message amqpMessage = buildAmqpMessage(12L);
        Order order = new Order();
        order.setId(90002L);
        order.setStatus("pending");

        doAnswer(invocation -> {
            MqConsumeLog log = invocation.getArgument(0);
            log.setId(102L);
            return 1;
        }).when(mqConsumeLogMapper).insert(any(MqConsumeLog.class));
        when(orderMapper.selectOrderBasicById(90002L)).thenReturn(order);
        when(orderMapper.markProductSoldIfOnSale(80002L)).thenReturn(1);

        consumer.onMessage(message, channel, amqpMessage);

        // 断言重点：
        // 本次修复只拦截“订单已取消”的异常回写，不影响正常的库存异步更新链路。
        verify(orderMapper).markProductSoldIfOnSale(80002L);
        verify(mqConsumeLogMapper).updateStatus(102L, "OK");
        verify(channel).basicAck(12L, false);
    }

    /**
     * 构造最小可用的库存更新消息。
     *
     * 测试里只关心 `eventId / orderId / productId` 三个字段，
     * 其余字段不是本次保护逻辑的决策条件，因此保持最小输入即可。
     */
    private EventMessage<OrderCreatedPayload> buildMessage(String eventId, Long orderId, Long productId) {
        OrderCreatedPayload payload = new OrderCreatedPayload();
        payload.setOrderId(orderId);
        payload.setProductId(productId);

        EventMessage<OrderCreatedPayload> message = new EventMessage<>();
        message.setEventId(eventId);
        message.setPayload(payload);
        return message;
    }

    /**
     * 构造带 deliveryTag 的 AMQP 原生消息。
     *
     * `InventoryUpdateConsumer` 会使用 deliveryTag 做 `basicAck/basicNack`，
     * 因此测试需要显式提供一个可验证的 tag。
     */
    private Message buildAmqpMessage(long deliveryTag) {
        MessageProperties properties = new MessageProperties();
        properties.setDeliveryTag(deliveryTag);
        return new Message(new byte[0], properties);
    }
}
