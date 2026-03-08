# Day15 MQ 两次消息转换失败复盘 v1.0

- 复盘日期：2026-02-12
- 关联阶段：Day15（发货与物流 + 通知 + 超时补偿）
- 关联组件：`OutboxPublishJob`、RabbitMQ `MessageConverter`、`OrderPaidConsumer`

---

## 1. 先说结论

这两次“启动后马上报错”本质上都不是业务代码逻辑错，而是 **MQ 消息的 JSON 转换规则不一致**：

1. 第一次报错（17:21:25）是 **生产端序列化失败**：
   - Rabbit 的 converter 没有正确支持 `LocalDateTime`。
2. 第二次报错（17:24:05）是 **消费端反序列化失败**：
   - 消费端只接受一种时间格式（`yyyy-MM-dd HH:mm`），但消息里出现了 ISO 时间（`2026-02-08T17:27:58.8573385`）。

因此，修复方向是两步：

1. 统一 Rabbit 使用项目 `JacksonObjectMapper`。
2. `LocalDateTime` 反序列化改为多格式兼容，兜住历史消息和异构格式。

---

## 2. 故障一：Outbox 发送失败（生产端序列化失败）

### 2.1 现象（日志）

时间：`2026-02-12 17:21:25`

关键报错：

- `Outbox send failed`
- `MessageConversionException: Failed to convert Message content`
- `InvalidDefinitionException: Java 8 date/time type java.time.LocalDateTime not supported by default`
- 指向字段：`EventMessage["occurredAt"]`

### 2.2 触发链路

1. `OutboxPublishJob` 扫描 `message_outbox`。
2. 读取 `payload_json` 转 `EventMessage`。
3. 执行 `rabbitTemplate.convertAndSend(...)`。
4. Rabbit converter 序列化 `EventMessage` 时遇到 `LocalDateTime occurredAt`。
5. 因 converter 没有可用的 Java Time 支持，发送失败并进入重试。

对应代码位置：

- `demo-service/src/main/java/com/demo/job/OutboxPublishJob.java`
- `demo-pojo/src/main/java/com/demo/dto/mq/EventMessage.java`

### 2.3 根因

`RabbitMqConfiguration` 中 `rabbitMessageConverter()` 使用了默认 `Jackson2JsonMessageConverter`，没有明确绑定项目自定义 mapper，导致 `LocalDateTime` 在某些链路下无法序列化。

### 2.4 修复

已修改为显式使用项目 mapper：

- 文件：`demo-service/src/main/java/com/demo/config/RabbitMqConfiguration.java`
- 核心修复：
  - `new Jackson2JsonMessageConverter(new JacksonObjectMapper())`

---

## 3. 故障二：监听器消费失败（消费端反序列化失败）

### 3.1 现象（日志）

时间：`2026-02-12 17:24:05`

关键报错：

- `SimpleMessageListenerContainer: Execution of Rabbit message listener failed`
- `ListenerExecutionFailedException: Failed to convert message`
- `InvalidFormatException: Cannot deserialize LocalDateTime from String "2026-02-08T17:27:58.8573385"`
- `DateTimeParseException: ... could not be parsed at index 10`
- 报错链路：`EventMessage["payload"] -> OrderPaidPayload["payTime"]`

### 3.2 为什么是“index 10”？

因为你的旧格式是 `yyyy-MM-dd HH:mm`，第 10 位应该是空格；但消息里第 10 位是 `T`（ISO-8601 格式），所以解析器在这里直接失败。

### 3.3 触发链路（很关键）

1. Rabbit 收到消息后，**先**尝试把 JSON 转成监听方法参数类型。
2. 你的监听方法签名是：
   - `onMessage(EventMessage<OrderPaidPayload> message, ...)`
3. 也就是说在进入 `onMessage` 之前，框架已经要把 `payTime` 反序列化成 `LocalDateTime`。
4. 一旦反序列化失败，业务代码根本不执行，直接进入监听容器错误处理器。

对应代码位置：

- `demo-service/src/main/java/com/demo/mq/consumer/OrderPaidConsumer.java`
- `demo-pojo/src/main/java/com/demo/dto/mq/OrderPaidPayload.java`

### 3.4 根因

项目原本 `JacksonObjectMapper` 对 `LocalDateTime` 反序列化只配置了单一格式：

- `yyyy-MM-dd HH:mm`

但消息里实际出现了 ISO 时间（带 `T` 和小数秒），导致反序列化不兼容。

### 3.5 修复

新增多格式反序列化器并注册：

1. 新增文件：
   - `demo-common/src/main/java/com/demo/json/MultiFormatLocalDateTimeDeserializer.java`
2. 注册到 mapper：
   - `demo-common/src/main/java/com/demo/json/JacksonObjectMapper.java`

当前支持格式：

1. `yyyy-MM-dd HH:mm`
2. `yyyy-MM-dd HH:mm:ss`
3. ISO-8601（如 `2026-02-08T17:27:58.8573385`）
4. 带时区偏移和 UTC instant（兼容兜底）

---

## 4. 为什么会连续出现两次？

这是典型的“分层修复逐步暴露问题”：

1. 第一层（发送端）先被修好后，消息成功进入队列。
2. 第二层（消费端）才有机会暴露格式不兼容问题。

换句话说，第二次不是新问题，而是第一层修复后显露出来的下一层问题。

---

## 5. 涉及到的核心知识点（必须掌握）

## 5.1 Spring AMQP 的消息转换时机

监听器方法参数不是原始字符串时，框架会先做反序列化。如果这里失败，业务方法不会执行。

你这次就是卡在“方法入参转换”阶段。

## 5.2 ObjectMapper 一致性

HTTP、MQ、Outbox 如果用不同 mapper，时间/枚举/空值策略很容易漂移，导致“能发不能收”或“本地行线上挂”。

最佳实践：统一 mapper 或最少统一关键序列化策略。

## 5.3 LocalDateTime 的格式契约

`LocalDateTime` 没有时区信息，本身不是时间戳。跨系统传输时要约定格式，不然双方解析口径会冲突。

## 5.4 向后兼容思想

已有历史消息可能是旧格式。只支持新格式会导致线上消费异常。消费端通常要比生产端“更宽容”。

## 5.5 错误处理语义

`ConditionalRejectingErrorHandler` 把这类转换异常判定为 fatal，会 `RejectAndDontRequeue`，防止无限重试打爆系统，但会造成消息堆积到 DLQ 或直接丢弃（取决于队列策略）。

---

## 6. 本次修复清单（落地）

1. `demo-service/src/main/java/com/demo/config/RabbitMqConfiguration.java`
   - Rabbit converter 显式绑定 `JacksonObjectMapper`。
2. `demo-common/src/main/java/com/demo/json/MultiFormatLocalDateTimeDeserializer.java`
   - 新增多格式 LocalDateTime 反序列化器。
3. `demo-common/src/main/java/com/demo/json/JacksonObjectMapper.java`
   - LocalDateTime 反序列化改为多格式反序列化器。

---

## 7. 验证方式（你可重复执行）

1. 重启服务。
2. 触发一条支付事件（或让 Outbox 重发一条 `ORDER_PAID`）。
3. 观察日志：
   - 不再出现 `Failed to convert Message content`
   - `OrderPaidConsumer` 正常处理并 ACK
4. 检查业务结果：
   - 卖家收到支付提醒（若开关开启）
   - 发货提醒任务（H24/H6/H1）被创建

---

## 8. 防再发建议

1. 在 Day15 回归中增加“MQ 时间格式兼容”用例：
   - 同时喂 `yyyy-MM-dd HH:mm` 与 ISO 时间。
2. 明确消息契约版本：
   - 例如 `EventMessage.version=2` 后统一 ISO 输出。
3. 消费端保持兼容窗口：
   - 在迁移期支持多格式，稳定后再收敛。
4. 增加 DLQ 监控：
   - 发现 `MessageConversionException` 及时报警。

---

## 9. 一句话复盘

这两次报错的本质是：**MQ 两端对 `LocalDateTime` 的 JSON 口径不一致**。  
修复关键不是“改业务逻辑”，而是“统一并兼容消息序列化/反序列化策略”。

