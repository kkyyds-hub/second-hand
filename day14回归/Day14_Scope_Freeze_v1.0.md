# Day14 范围冻结：RabbitMQ 集成与事务管理（Scope Freeze）

- 项目：二手交易平台（secondhand2 / com.demo）
- 模块：Day14 异步与事件驱动增强（RabbitMQ + 事务性外箱 + 可靠消息）
- 文档版本：v1.0（首次冻结｜工程口径对齐版）
- 冻结日期：2026-02-04
- 目标：把 Day14 “做什么 / 不做什么 / 规则口径 / 事件契约 / 数据结构 / 验收标准”一次写死，避免实现过程反复改口径导致返工。

---

## 0. 边界决策（已敲定）
本 Day14 的功能边界以以下开关为准（YES/NO）。**除非升级本文档版本号，否则实现不得越界**。

1. 消息中间件：**YES（RabbitMQ）**
2. 事件驱动：**YES（订单/库存/发货相关事件）**
3. 事务性外箱（Outbox Pattern）：**YES（数据库事务提交后发送消息）**
4. 消息投递语义：**YES（至少一次，At-least-once）**
5. 精确一次：**NO（不做 Exactly-once，依赖幂等消费）**
6. 消息确认：**YES（手动 ack/nack）**
7. 失败重试：**YES（重试 + DLQ）**
8. 延迟消息：**YES（TTL + DLX，不引入延迟插件）**
9. 事件幂等：**YES（eventId 去重表）**
10. 监控与可观测性：**YES（管理插件 + 指标/日志）**
11. 跨服务分布式事务：**NO（不做 XA/2PC）**
12. MQ 集群高可用：**NO（开发/回归环境单节点即可）**
13. MQ 消息压缩/加密：**NO（仅内部网络）**
14. 库存模型：**YES（本项目“库存/占用”以商品状态流转为准：`products.status` 在下单时原子 `on_sale -> sold`；取消/超时取消时 `sold -> on_sale`）**

> 说明：本冻结文档以你工程现有口径为准（例如：订单状态枚举 `OrderStatus`、现有订单/支付/发货接口路径等）。若需扩展由文档版本升级驱动。

---

## 1. 术语与对象定义
- **事件（Event）**：系统内发生的业务事实，用于驱动后续异步流程（如订单创建、支付成功、发货状态变化）。
- **消息（Message）**：事件在 MQ 中的传输载体（包含事件类型、唯一 ID、时间戳、payload）。
- **事务性外箱（Outbox）**：业务事务成功后记录的待发送事件表，由投递器异步发送至 MQ。
- **投递器（Dispatcher）**：轮询 Outbox 表并发送 MQ 的后台任务。
- **DLQ（Dead-letter Queue）**：消息多次失败后进入的死信队列。
- **幂等消费**：同一事件重复投递时仅处理一次（通过事件唯一键去重）。
- **可恢复错误**：可通过重试恢复（如短暂网络、锁冲突）。
- **不可恢复错误**：重试无意义（如事件类型不支持、数据缺失）。

---

## 2. 总体架构与生命周期（冻结）
### 2.1 事件生命周期
1. 业务事务内：写主业务数据 + 写 `outbox_events`
2. 事务提交后：投递器扫描 `outbox_events`
3. 发送成功：MQ Ack + Outbox 状态置 `SENT`
4. 发送失败：Outbox 状态置 `RETRY`，等待下一次重试
5. 消费端处理：成功 ack；失败重试或入 DLQ

### 2.2 组件职责
- **业务服务**：只负责写 Outbox，不直接发送 MQ
- **投递器**：可靠投递 + 重试 + 失败告警
- **消费者**：幂等处理 + 业务校验 + ack/nack
- **监控**：对队列积压、失败率、DLQ 进行观测

---

## 3. RabbitMQ 环境配置（开发/回归）
**默认配置口径：**
- host：`localhost`
- port：`5672`
- management：`15672`
- vhost：`/`
- 用户名/密码：`guest/guest`（开发环境）

**Spring 配置冻结（示例口径）：**
- `spring.rabbitmq.publisher-confirm-type=correlated`
- `spring.rabbitmq.publisher-returns=true`
- `spring.rabbitmq.listener.simple.acknowledge-mode=manual`
- `spring.rabbitmq.listener.simple.prefetch=20`
- `spring.rabbitmq.listener.simple.default-requeue-rejected=false`

> 说明：prefetch 默认 20；并发参数可配置但不冻结具体值。

---

## 4. RabbitMQ 拓扑冻结
### 4.1 交换机
- `order.events.exchange`（topic）

### 4.2 队列
- `order.timeout.queue`：订单超时处理
- `order.timeout.delay.queue`：订单超时延迟队列（TTL 到期后路由到 `order.timeout.queue`）
- `inventory.update.queue`：库存异步更新
- `order.fulfillment.queue`：支付成功后发货触发
- `order.status.sync.queue`：订单状态变更后同步处理
- `order.dlq.queue`：死信队列（统一承接）

### 4.3 路由键
- `order.created`
- `order.paid`
- `order.status.changed`
- `order.timeout`
- `order.timeout.delay`

### 4.4 绑定关系
- `inventory.update.queue` 绑定 `order.created`
- `order.fulfillment.queue` 绑定 `order.paid`
- `order.status.sync.queue` 绑定 `order.status.changed`
- `order.timeout.queue` 绑定 `order.timeout`
- `order.timeout.delay.queue` 绑定 `order.timeout.delay`
- 各业务队列 DLX → `order.dlq.queue`

### 4.5 队列参数冻结
**业务队列统一设置：**
- `x-dead-letter-exchange` 指向 `order.events.exchange`
- `x-dead-letter-routing-key` 指向 `order.dlq`

**超时延迟队列（用于“到点触发”）设置：**
- `order.timeout.delay.queue` 固定为“延迟队列”，仅用于承载 TTL：
  - `x-message-ttl`：`order.timeout.pending-minutes * 60 * 1000`（默认 15 分钟，支持配置）
  - `x-dead-letter-exchange = order.events.exchange`
  - `x-dead-letter-routing-key = order.timeout`

**DLQ 绑定：**
- `order.dlq.queue` 绑定路由键 `order.dlq`

---

## 5. 事件体系与触发点（冻结）
### 5.1 事件枚举
1. `ORDER_CREATED`
2. `ORDER_PAID`
3. `ORDER_STATUS_CHANGED`
4. `ORDER_TIMEOUT`

### 5.2 触发点
- `ORDER_CREATED`：订单创建事务提交成功
- `ORDER_PAID`：订单 `pending -> paid` 成功后
- `ORDER_STATUS_CHANGED`：`paid -> shipped` / `shipped -> completed` 成功后
- `ORDER_TIMEOUT`：订单超时（TTL + DLX）

> 事件统一由业务服务写 Outbox，不允许直接在业务事务中发送 MQ。

---

## 5.3 与现有代码口径对齐（基线行为，必须保持）
> 这一节用于把 Day14 的“事件/异步”与当前工程已存在的业务口径对齐，避免你实现 Day14 时误改核心行为。

### 5.3.1 下单成功“扣库存/占用”口径
本项目当前无 `stock` 数量字段；“库存/占用”的唯一口径是 **商品状态**：
- 下单成功：在订单创建事务中原子更新 `products.status`：`on_sale -> sold`
- 取消订单/超时取消：释放商品 `sold -> on_sale`

因此：
- `ORDER_CREATED` 事件 **不是** 用来替代“原子占用商品”的（原子占用仍必须在事务内完成，用来防并发重复购买）
- `ORDER_CREATED` 事件用于触发“异步副作用”（如库存投影/缓存刷新/通知/统计），这些副作用允许最终一致

### 5.3.2 超时关单口径（现有实现）
当前工程已存在超时关单链路（Job 扫描 + 条件更新 + 释放商品），语义如下：
- 查询：批量找出 `pending` 且 `create_time <= deadline` 的订单
- 关闭：条件更新 `orders.status`：`pending -> cancelled`（`cancel_reason='timeout'`）
- 释放：对订单关联商品执行 `sold -> on_sale`
- 影响：超时关单属于“取消”，会触发买家信用统计 recalculation（属于现有口径）

Day14 若引入 MQ 延迟消息（TTL+DLX）来触发超时处理，**必须保持上面这套状态校验与释放语义不变**。

---

## 6. 消息结构（统一契约）
所有事件消息采用以下 JSON 结构（字段冻结）：

```json
{
  "eventId": "uuid",
  "eventType": "ORDER_CREATED",
  "aggregateType": "Order",
  "aggregateId": 900001,
  "occurredAt": "2026-02-04T10:30:00Z",
  "traceId": "trace-xxx",
  "payload": {
    "orderId": 900001,
    "orderNo": "202602041030001234",
    "buyerId": 10001,
    "sellerId": 10002,
    "totalAmount": 88.50
  }
}
```

**约束：**
- `eventId` 必须全局唯一（幂等依据）
- `eventType` 必须在第 5.1 的枚举中
- `aggregateId` 与 payload 内关键 ID 一致
- `payload` 内字段随事件类型扩展，但不得移除通用字段

---

## 7. 事务性外箱（Outbox Pattern）冻结
### 7.1 Outbox 写入规则
- 业务事务中：写订单/库存等核心数据 + 写 `outbox_events`
- outbox 记录必须包含：`eventId、eventType、aggregateType、aggregateId、payload、status`
- 初始 `status=NEW`

### 7.2 投递器规则
- 扫描条件：`status IN (NEW, RETRY)` 且 `next_retry_time <= now`
- 批量大小：默认 200（可配置）
- 扫描间隔：默认 5s（可配置）
- 成功发送：更新 `status=SENT`，记录 `update_time`
- 失败发送：`retry_count+1`，更新 `next_retry_time`
- 超过最大重试次数：`status=FAILED`，记录失败原因并报警

### 7.3 Retry 退避策略（冻结）
- 1 次失败：5s 后
- 2 次失败：30s 后
- 3 次失败：2min 后
- 超过 3 次：FAILED

> 退避策略可配置，但默认值冻结以保证回归一致性。

---

## 8. 生产者可靠投递
- 开启 Publisher Confirm（`correlated`）
- 消息路由失败触发 Return Callback
- Confirm 失败时更新 Outbox 为 `RETRY`
- 发送成功以 MQ Confirm 为准，不以本地发送调用结果为准

---

## 9. 消费幂等与重试
### 9.1 幂等规则
- 消费者必须基于 `eventId` 幂等
- 已消费事件直接 ack，不重复执行业务

### 9.2 重试与 DLQ
- 业务可恢复失败：`basicNack(requeue=true)`
- 超过重试阈值：`basicNack(requeue=false)` → DLQ

### 9.3 不可恢复错误
- 事件类型不支持
- 事件数据缺失或严重不合法
- 订单不存在且不可补偿

> 不可恢复错误必须直接 `nack(false)` 进入 DLQ，并记录日志。

---

## 10. 订单超时（延迟消息方案）
### 10.1 主触发机制（MQ TTL + DLX，为主）
- 采用 TTL + DLX：**订单创建成功后**投递一条“超时触发消息”到 `order.timeout.delay.queue`：
  - Exchange：`order.events.exchange`
  - RoutingKey：`order.timeout.delay`
  - EventType：`ORDER_TIMEOUT`
  - TTL：`order.timeout.pending-minutes`（默认 15 分钟）
- TTL 到期后：消息被 RabbitMQ dead-letter 到 `order.events.exchange`，并以 RoutingKey=`order.timeout` 路由到 `order.timeout.queue`
- `order.timeout.queue` 的消费者收到消息后执行“超时关单”，**必须二次校验**：
  - 仅当订单仍为 `pending` 且 `create_time <= deadline` 才允许关闭
  - 关闭成功后释放商品：`sold -> on_sale`

### 10.2 兜底机制（DB 扫描 Job，保留）
- 保留现有 `OrderTimeoutJob` 作为兜底补偿链路：
  - 周期扫描 `pending` 且超时订单（按 batch 分批）
  - 条件更新 `pending -> cancelled`（幂等/并发安全）
  - 释放商品：`sold -> on_sale`
- 目的：在 MQ 异常（延迟队列不可用、消息堆积、消费者异常）时，防止订单永久停留在 `pending`

### 10.3 口径冻结（必须保持与现有工程一致）
- “超时关单”语义与现有实现一致：`pending -> cancelled` + `cancel_reason='timeout'` + 释放商品
- 任何触发来源（MQ / Job）都必须以“条件更新行数=1”为唯一成功判定，避免误关已支付订单

---

## 11. 事件驱动业务流程冻结
- **订单创建 → 库存更新**：`ORDER_CREATED` → `inventory.update.queue`
- **支付完成 → 发货启动**：`ORDER_PAID` → `order.fulfillment.queue`
- **订单状态变化 → 同步/通知**：`ORDER_STATUS_CHANGED` → `order.status.sync.queue`

> 事件处理器必须严格校验订单状态，避免重复支付/重复发货/重复完成。

---

## 12. 监控与可观测性
- 启用 RabbitMQ Management Plugin
- 监控指标：队列积压、消费速率、ack/nack 比例、DLQ 数量
- 日志统一带：`eventId`、`traceId`、`queue`、`consumer`
- 报警阈值建议：队列积压 > 1000 或 DLQ > 0

---

## 13. 事件契约冻结（建议贴进事件文档）
### 13.1 ORDER_CREATED
- 路由键：`order.created`
- payload：`orderId, orderNo, buyerId, sellerId, productId, quantity, price`

### 13.2 ORDER_PAID
- 路由键：`order.paid`
- payload：`orderId, orderNo, payTime, amount`

### 13.3 ORDER_STATUS_CHANGED
- 路由键：`order.status.changed`
- payload：`orderId, orderNo, oldStatus, newStatus, changedAt`

### 13.4 ORDER_TIMEOUT
- 路由键：`order.timeout`
- payload：`orderId, orderNo, createTime`

---

## 14. 数据结构冻结
### 14.1 MySQL：事务性外箱表
表：`outbox_events`
- `id` BIGINT PK
- `event_id` VARCHAR(64) NOT NULL UNIQUE
- `event_type` VARCHAR(64) NOT NULL
- `aggregate_type` VARCHAR(64) NOT NULL
- `aggregate_id` BIGINT NOT NULL
- `payload` TEXT NOT NULL
- `status` VARCHAR(16) NOT NULL（NEW/SENT/RETRY/FAILED）
- `retry_count` INT NOT NULL DEFAULT 0
- `next_retry_time` DATETIME NULL
- `create_time` DATETIME NOT NULL
- `update_time` DATETIME NOT NULL

索引建议：
- `idx_status_time(status, next_retry_time)`
- `idx_aggregate(aggregate_type, aggregate_id)`

### 14.2 MySQL：消费日志表
表：`event_consume_log`
- `id` BIGINT PK
- `event_id` VARCHAR(64) NOT NULL UNIQUE
- `event_type` VARCHAR(64) NOT NULL
- `consumer` VARCHAR(64) NOT NULL
- `consume_time` DATETIME NOT NULL

---

## 15. 错误码与提示语冻结
MQ / 事件处理：
- 事件重复消费：`"事件已处理，忽略重复消息"`
- 事件类型不支持：`"事件类型不支持"`
- 发送消息失败：`"消息发送失败"`
- 消息消费失败：`"消息消费失败"`

---

## 16. Day14 最小验收标准（全绿定义）
### 16.1 RabbitMQ 集成
1. 本地 RabbitMQ 可用（5672/15672），可创建交换机与队列
2. 订单创建后写入 Outbox 并发送 `ORDER_CREATED` 消息
3. 消息能被库存更新消费者正确处理

### 16.2 事件驱动流程
1. 支付完成事件触发发货流程
2. 订单状态变化事件触发同步/通知逻辑
3. 超时消息触发订单取消（订单状态正确更新）

### 16.3 事务一致性
1. 业务事务失败时不发送消息
2. 事务成功后必有 Outbox 记录
3. Outbox 投递失败可重试，超过次数进入 FAILED

### 16.4 可靠性与幂等
1. 重复消息不重复处理（幂等日志生效）
2. 消息失败可进入 DLQ
3. DLQ 消息可手动重放或报警

---

## 17. 回归数据约定
- 固定订单：创建一笔 pending 订单（用于超时/支付事件）
- 固定支付订单：创建并支付一笔订单（用于发货事件）
- 固定事件：构造一个重复 `eventId` 测试幂等消费

---

## 18. 不做什么（Non-goals）
Day14 **不实现**：
- Kafka/RocketMQ 替代方案
- 精确一次语义或分布式事务（XA/2PC）
- MQ 集群高可用与跨机房容灾
- 自研消息编排/工作流引擎
- 复杂消息调度中心（仅依赖 RabbitMQ + Outbox）

---

## Day14 交付物
- 配置完成的 RabbitMQ 环境
- 实现事件处理程序与消息队列（订单处理 / 库存管理）
- 实现事务性外箱模式，保证 DB 与 MQ 一致性
- 单元测试与集成测试覆盖异步流程
- RabbitMQ 队列状态监控与日志可观测性

---

（文件结束）
