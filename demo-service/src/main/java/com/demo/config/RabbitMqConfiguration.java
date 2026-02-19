package com.demo.config;

import com.demo.json.JacksonObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;

import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;

/**
 * Day14 - RabbitMQ 拓扑自动声明（Exchange / Queue / Binding）。
 *
 * Spring Boot 启动时会通过 RabbitAdmin（由 spring-boot-starter-amqp 自动装配）
 * 将这些声明同步到 RabbitMQ；已存在则跳过（幂等）。
 */
@Configuration
@Slf4j
@ConditionalOnProperty(value = "demo.rabbitmq.enabled", havingValue = "true", matchIfMissing = true)
public class RabbitMqConfiguration {

    private static final String ARG_DLX = "x-dead-letter-exchange";
    private static final String ARG_DLX_RK = "x-dead-letter-routing-key";
    private static final String ARG_TTL = "x-message-ttl";

    /**
     * Ensure declarations are actually applied to RabbitMQ on startup.
     * Declaring Queue/Exchange/Binding beans only describes the topology in Spring;
     * RabbitAdmin is responsible for pushing them to RabbitMQ.
     */
    @Bean
    @ConditionalOnMissingBean(RabbitAdmin.class)
    public RabbitAdmin rabbitAdmin(ConnectionFactory connectionFactory) {
        RabbitAdmin admin = new RabbitAdmin(connectionFactory);
        admin.setAutoStartup(true);
        return admin;
    }

    @Bean
    /**
     * 注册 RabbitMQ JSON 消息转换器。
     */
    public MessageConverter rabbitMessageConverter() {
        // 显式使用项目统一的 JacksonObjectMapper，确保 LocalDateTime 等 Java8 时间类型可序列化/反序列化。
        // 否则 Outbox 发送 EventMessage 时会在 occurredAt 字段处报转换异常。
        return new Jackson2JsonMessageConverter(new JacksonObjectMapper());
    }


    @Autowired
    private RabbitAdmin rabbitAdmin;

    /**
     * 应用启动后主动触发一次声明初始化。
     */
    @EventListener(ApplicationReadyEvent.class)
    public void initializeRabbitDeclarations(ApplicationReadyEvent event) {
        try {
            rabbitAdmin.initialize();
            log.info("RabbitMQ declarations initialized via RabbitAdmin.");
        } catch (Exception ex) {
            log.error("RabbitMQ declarations failed to initialize.", ex);
        }
    }

    @Bean
    public TopicExchange orderEventsExchange(
            @Value("${demo.rabbitmq.exchange.order-events}") String exchangeName
    ) {
        log.info("Declaring RabbitMQ exchange: {}", exchangeName);
        return new TopicExchange(exchangeName, true, false);
    }

    @Bean
    public Queue dlqQueue(
            @Value("${demo.rabbitmq.queue.dlq}") String dlqQueue
    ) {
        log.info("Declaring RabbitMQ queue (DLQ): {}", dlqQueue);
        return QueueBuilder.durable(dlqQueue).build();
    }

    /**
     * 库存更新消费者队列。
     */
    @Bean
    public Queue inventoryUpdateQueue(
            @Value("${demo.rabbitmq.queue.inventory-update}") String queueName,
            @Value("${demo.rabbitmq.exchange.order-events}") String dlx,
            @Value("${demo.rabbitmq.routing-key.dlq}") String dlqRoutingKey
    ) {
        log.info("Declaring RabbitMQ queue: {}", queueName);
        return QueueBuilder.durable(queueName)
                .withArgument(ARG_DLX, dlx)
                .withArgument(ARG_DLX_RK, dlqRoutingKey)
                .build();
    }

    /**
     * 订单履约消费者队列。
     */
    @Bean
    public Queue orderFulfillmentQueue(
            @Value("${demo.rabbitmq.queue.order-fulfillment}") String queueName,
            @Value("${demo.rabbitmq.exchange.order-events}") String dlx,
            @Value("${demo.rabbitmq.routing-key.dlq}") String dlqRoutingKey
    ) {
        log.info("Declaring RabbitMQ queue: {}", queueName);
        return QueueBuilder.durable(queueName)
                .withArgument(ARG_DLX, dlx)
                .withArgument(ARG_DLX_RK, dlqRoutingKey)
                .build();
    }

    /**
     * 订单状态同步消费者队列。
     */
    @Bean
    public Queue orderStatusSyncQueue(
            @Value("${demo.rabbitmq.queue.order-status-sync}") String queueName,
            @Value("${demo.rabbitmq.exchange.order-events}") String dlx,
            @Value("${demo.rabbitmq.routing-key.dlq}") String dlqRoutingKey
    ) {
        log.info("Declaring RabbitMQ queue: {}", queueName);
        return QueueBuilder.durable(queueName)
                .withArgument(ARG_DLX, dlx)
                .withArgument(ARG_DLX_RK, dlqRoutingKey)
                .build();
    }

    /**
     * 订单超时处理队列。
     */
    @Bean
    public Queue orderTimeoutQueue(
            @Value("${demo.rabbitmq.queue.order-timeout}") String queueName,
            @Value("${demo.rabbitmq.exchange.order-events}") String dlx,
            @Value("${demo.rabbitmq.routing-key.dlq}") String dlqRoutingKey
    ) {
        log.info("Declaring RabbitMQ queue: {}", queueName);
        return QueueBuilder.durable(queueName)
                .withArgument(ARG_DLX, dlx)
                .withArgument(ARG_DLX_RK, dlqRoutingKey)
                .build();
    }

    /**
     * Day14 - 延迟队列（TTL + DLX）
     * 订单创建后发送消息到该队列；TTL 到期后自动 dead-letter 到 order.timeout.queue 的 routingKey。
     */
    @Bean
    public Queue orderTimeoutDelayQueue(
            @Value("${demo.rabbitmq.queue.order-timeout-delay}") String queueName,
            @Value("${demo.rabbitmq.exchange.order-events}") String dlx,
            @Value("${demo.rabbitmq.routing-key.order-timeout}") String timeoutRoutingKey,
            @Value("${order.timeout.pending-minutes:15}") int pendingMinutes
    ) {
        long ttlMs = pendingMinutes * 60_000L;
        log.info("Declaring RabbitMQ delay queue: {}, ttlMs={}", queueName, ttlMs);
        return QueueBuilder.durable(queueName)
                .withArgument(ARG_TTL, ttlMs)
                .withArgument(ARG_DLX, dlx)
                .withArgument(ARG_DLX_RK, timeoutRoutingKey)
                .build();
    }

    /**
     * 死信队列绑定。
     */
    @Bean
    public Binding bindDlq(
            @Qualifier("dlqQueue") Queue dlqQueue,
            TopicExchange orderEventsExchange,
            @Value("${demo.rabbitmq.routing-key.dlq}") String dlqRoutingKey
    ) {
        return BindingBuilder.bind(dlqQueue).to(orderEventsExchange).with(dlqRoutingKey);
    }

    @Bean
    public Binding bindInventoryUpdate(
            @Qualifier("inventoryUpdateQueue") Queue inventoryUpdateQueue,
            TopicExchange orderEventsExchange,
            @Value("${demo.rabbitmq.routing-key.order-created}") String routingKey
    ) {
        return BindingBuilder.bind(inventoryUpdateQueue).to(orderEventsExchange).with(routingKey);
    }

    @Bean
    public Binding bindOrderFulfillment(
            @Qualifier("orderFulfillmentQueue") Queue orderFulfillmentQueue,
            TopicExchange orderEventsExchange,
            @Value("${demo.rabbitmq.routing-key.order-paid}") String routingKey
    ) {
        return BindingBuilder.bind(orderFulfillmentQueue).to(orderEventsExchange).with(routingKey);
    }

    @Bean
    public Binding bindOrderStatusSync(
            @Qualifier("orderStatusSyncQueue") Queue orderStatusSyncQueue,
            TopicExchange orderEventsExchange,
            @Value("${demo.rabbitmq.routing-key.order-status-changed}") String routingKey
    ) {
        return BindingBuilder.bind(orderStatusSyncQueue).to(orderEventsExchange).with(routingKey);
    }

    @Bean
    public Binding bindOrderTimeout(
            @Qualifier("orderTimeoutQueue") Queue orderTimeoutQueue,
            TopicExchange orderEventsExchange,
            @Value("${demo.rabbitmq.routing-key.order-timeout}") String routingKey
    ) {
        return BindingBuilder.bind(orderTimeoutQueue).to(orderEventsExchange).with(routingKey);
    }

    @Bean
    public Binding bindOrderTimeoutDelay(
            @Qualifier("orderTimeoutDelayQueue") Queue orderTimeoutDelayQueue,
            TopicExchange orderEventsExchange,
            @Value("${demo.rabbitmq.routing-key.order-timeout-delay}") String routingKey
    ) {
        return BindingBuilder.bind(orderTimeoutDelayQueue).to(orderEventsExchange).with(routingKey);
    }

}
