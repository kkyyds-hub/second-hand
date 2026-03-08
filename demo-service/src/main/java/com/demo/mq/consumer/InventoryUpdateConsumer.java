package com.demo.mq.consumer;

import com.demo.dto.mq.EventMessage;
import com.demo.dto.mq.OrderCreatedPayload;
import com.demo.entity.MqConsumeLog;
import com.demo.entity.Order;
import com.demo.enumeration.OrderStatus;
import com.demo.mapper.MqConsumeLogMapper;
import com.demo.mapper.OrderMapper;
import com.rabbitmq.client.Channel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

/**
 * Day14 - 库存更新消费者
 *
 * 职责：
 * 1) 监听 inventory.update.queue（订单创建事件）
 * 2) 根据 productId 把商品状态改为 SOLD（幂等）
 * 3) 手动 ACK / NACK
 *
 * 说明：
 * 当前订单创建时已同步改为 SOLD，这里作为“异步同步/兜底”。
 * 未来若改成纯异步库存更新，这个消费者就是主流程。
 *
 * P5-S1 修复背景：
 * 1) 并发回归中发现，订单创建事件是异步消息，它可能晚于“发货超时关单并释放商品”这条链路执行；
 * 2) 如果消费者仅凭 `productId` 无脑把商品从 `on_sale` 改回 `sold`，
 *    就会把已经因为订单取消而恢复上架的商品再次污染成 `sold`；
 * 3) 因此这里增加了“以当前订单真实状态为准”的保护：
 *    订单若已取消，则 ACK 并跳过库存回写，避免历史消息覆盖当前业务真相。
 */
@Slf4j
@Component
public class InventoryUpdateConsumer {

    /** 订单相关 Mapper（包含 markProductSoldIfOnSale） */
    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private MqConsumeLogMapper mqConsumeLogMapper;

    /** 幂等标识：消费者名称 */
    private static final String CONSUMER_NAME = "InventoryUpdateConsumer";

    /**
     * 监听订单创建事件（库存更新）
     *
     * @param message     统一事件信封（包含 OrderCreatedPayload）
     * @param channel     RabbitMQ 通道（用于 ACK/NACK）
     * @param amqpMessage 原生消息（用于 deliveryTag）
     */
    @RabbitListener(queues = "${demo.rabbitmq.queue.inventory-update}")
    public void onMessage(EventMessage<OrderCreatedPayload> message,
                          Channel channel,
                          Message amqpMessage) throws Exception {
        long tag = amqpMessage.getMessageProperties().getDeliveryTag();
        MqConsumeLog logRecord = null;
        try {
            // 1) 空消息兜底
            if (message == null || message.getPayload() == null) {
                log.warn("库存更新消息体为空，ACK 丢弃。");
                channel.basicAck(tag, false);
                return;
            }

            // 1.1) eventId 必须存在（幂等关键字段）
            if (message.getEventId() == null || message.getEventId().trim().isEmpty()) {
                log.warn("库存更新消息缺少 eventId，ACK 丢弃。");
                channel.basicAck(tag, false);
                return;
            }

            // 2) 幂等抢占：先插入一条消费记录（状态=PROCESSING）
            logRecord = new MqConsumeLog();
            logRecord.setConsumer(CONSUMER_NAME);
            logRecord.setEventId(message.getEventId());
            logRecord.setStatus("PROCESSING");
            try {
                mqConsumeLogMapper.insert(logRecord);
            } catch (DuplicateKeyException e) {
                // 已经处理过该消息 → 直接 ACK
                log.info("幂等命中：consumer=InventoryUpdateConsumer, eventId={}", message.getEventId());
                channel.basicAck(tag, false);
                return;
            }

            OrderCreatedPayload payload = message.getPayload();

            // 2) productId 必须存在
            if (payload.getProductId() == null) {
                log.warn("库存更新消息缺少 productId，ACK 丢弃。");
                mqConsumeLogMapper.updateStatus(logRecord.getId(), "OK");
                channel.basicAck(tag, false);
                return;
            }

            if (payload.getOrderId() != null) {
                // P5-S1 关键保护：
                // ORDER_CREATED 只代表“这个订单曾经创建过”，并不代表“此刻商品依然应该保持 sold”。
                // 如果当前订单已经被 ship-timeout / cancel 等链路关闭，那么最终业务真相是：
                //   - 订单已失效
                //   - 商品已经被释放回 on_sale
                // 这时再消费旧消息去回写 sold，会制造真正的数据反转。
                Order order = orderMapper.selectOrderBasicById(payload.getOrderId());
                if (order == null) {
                    log.warn("库存更新跳过：orderId={} 不存在，eventId={}", payload.getOrderId(), message.getEventId());
                    mqConsumeLogMapper.updateStatus(logRecord.getId(), "OK");
                    channel.basicAck(tag, false);
                    return;
                }
                OrderStatus orderStatus = OrderStatus.fromDbValue(order.getStatus());
                if (orderStatus == null) {
                    log.warn("库存更新跳过：orderId={} 状态异常，status={}, eventId={}",
                            payload.getOrderId(), order.getStatus(), message.getEventId());
                    mqConsumeLogMapper.updateStatus(logRecord.getId(), "OK");
                    channel.basicAck(tag, false);
                    return;
                }
                if (orderStatus == OrderStatus.CANCELLED) {
                    // 这里选择“ACK + 标记 OK”而不是抛错重试，因为消息本身没有问题，
                    // 问题只是它描述的是历史状态，而当前业务状态已经前进到了“订单取消”。
                    // 对这种消息继续重试没有任何收益，只会反复制造噪音。
                    log.info("库存更新跳过：orderId={} 已取消，productId={}, eventId={}",
                            payload.getOrderId(), payload.getProductId(), message.getEventId());
                    mqConsumeLogMapper.updateStatus(logRecord.getId(), "OK");
                    channel.basicAck(tag, false);
                    return;
                }
            }

            // 3) 幂等更新：只在 on_sale 时改为 sold。
            //    即使没有取消态保护，这条 SQL 也天然具备“非 on_sale 不重复改写”的幂等能力；
            //    再叠加上面的订单状态校验，就能同时兼顾“幂等”和“最终一致性”。
            int rows = orderMapper.markProductSoldIfOnSale(payload.getProductId());

            log.info("库存更新处理完成：productId={}, updatedRows={}",
                    payload.getProductId(), rows);

            // 4) 成功：标记 OK + ACK
            mqConsumeLogMapper.updateStatus(logRecord.getId(), "OK");
            channel.basicAck(tag, false);
        } catch (Exception ex) {
            // 系统异常进入 DLQ
            if (logRecord != null && logRecord.getId() != null) {
                mqConsumeLogMapper.updateStatus(logRecord.getId(), "FAIL");
            }
            log.error("库存更新处理失败，NACK 进入 DLQ。msg={}", message, ex);
            channel.basicNack(tag, false, false);
        }
    }
}
