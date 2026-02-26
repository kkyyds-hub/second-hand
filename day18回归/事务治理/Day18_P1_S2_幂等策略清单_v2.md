# Day18 P1-S2 幂等策略清单 v2

- 日期：2026-02-25
- 对应阶段：`Step P1-S2：幂等策略与唯一约束闭环`
- 目标：核心操作重复触发不产生重复副作用，且命中日志可追踪。

---

## 1. 统一幂等口径（P1-S2 冻结）

1. 先定义幂等键：每条关键写链路必须有可定位键（`orderId/orderNo/eventId/idempotencyKey/clientMsgId`）。
2. 先数据库防重：优先唯一约束 + `INSERT IGNORE`，并捕获 `DuplicateKeyException` 做幂等分流。
3. 状态机型操作统一 CAS：`expectedStatus` 条件更新，`rows=0` 后必须回查最新状态。
4. 命中幂等返回稳定语义：对外接口返回“已处理/无需重复操作”，不抛系统异常。
5. 统一日志模板：`幂等命中：action={}, idemKey={}, detail={}`。

---

## 2. 核心链路幂等矩阵（v2）

| 链路 | 幂等键 | 防重实现 | 命中后语义 | 证据 |
|---|---|---|---|---|
| 支付 `payOrder` | `orderId` + 订单状态 | `pending->paid` 条件更新 + `rows=0` 回查 | 返回“订单已支付，无需重复操作” | `OrderServiceImpl` |
| 支付回调 `handlePaymentCallback` | `orderNo` + 订单状态 | `pending->paid` 条件更新 + 回查 | 返回“订单已支付，回调幂等成功” | `OrderServiceImpl` |
| 卖家发货 `shipOrder` | `orderId` + 订单状态 | `paid->shipped` 条件更新 + 回查 | 返回“订单已发货，无需重复操作” | `OrderServiceImpl` |
| 买家确认收货 `confirmOrder` | `orderId` + 订单状态 | `shipped->completed` 条件更新 + 回查 | 返回“订单已确认收货，无需重复操作” | `OrderServiceImpl` |
| 取消订单 `cancelOrder` | `orderId` + 订单状态 | `pending->cancelled` 条件更新 + 回查 | 返回“订单已取消，无需重复操作” | `OrderServiceImpl` |
| 发货超时任务创建 | `order_id` | `uk_ship_timeout_order_id` + `insertIgnore` | 不重复建任务，仅记录命中日志 | `OrderShipTimeoutTaskMapper.xml` |
| 发货提醒任务创建 | `order_id + level` | `uk_order_level` + `insertIgnore` | 不重复建任务，仅记录命中日志 | `OrderShipReminderTaskMapper.xml` |
| 退款任务创建 | `order_id + refund_type` / `idempotency_key` | `uk_refund_order_type` / `uk_refund_idempotency` + `insertIgnore` | 不重复建任务，仅记录命中日志 | `OrderRefundTaskMapper.xml` |
| 退款任务推进 | `taskId + expectedStatus` | `markSuccess/markFail` CAS 更新 | `rows=0` 回查 `SUCCESS` 视为幂等命中 | `OrderRefundTaskProcessor` |
| 积分发放 | `user_id + biz_type + biz_id` | `uniq_points_biz` + `DuplicateKeyException` | 命中按成功处理，不重复发放 | `PointsServiceImpl` |
| MQ 消费去重 | `consumer + event_id` | `mq_consume_log.uk_consumer_event` + 抢占插入 | 命中直接 ACK 跳过重复消费 | `OrderPaidConsumer` 等 |
| 站内消息发送 | `orderId + fromUserId + clientMsgId` | Mongo 复合唯一索引 `uniq_order_clientMsg` | 重复发送不新增文档 | `Message` 实体 `@CompoundIndex` |
| 违规处罚记录 | `user_id + violation_type + biz_id` | `uk_user_violation_biz` + `insertIgnore` | 命中不重复处罚 | `OrderShipTimeoutPenaltyServiceImpl` |
| 收藏操作 | `user_id + product_id` | `uk_favorites_user_product` + 冲突转幂等成功 | 重复收藏不新增记录 | `FavoriteServiceImpl` |

---

## 3. 幂等命中日志规范

### 3.1 模板

```text
幂等命中：action=<业务动作>, idemKey=<幂等键>, detail=<命中上下文>
```

### 3.2 本次复核已命中的关键实现点

1. `OrderServiceImpl.logIdempotencyHit(...)`（支付、回调、发货、取消、确认收货）。
2. `OrderShipReminderTaskServiceImpl`（`createShipReminderTask` 命中日志）。
3. `OrderShipTimeoutTaskProcessor`（`createRefundTask` 命中日志）。
4. `OrderRefundTaskProcessor`（`markRefundSuccess` 并发命中日志）。
5. `PointsServiceImpl`（积分发放重复命中日志）。
6. MQ Consumers（`consumer + eventId` 重复消费命中日志）。

---

## 4. 口径收口结论

1. 核心写链路已形成“幂等键 + 唯一约束/CAS + 稳定返回语义”的闭环模式。
2. 任务链路已统一 `insertIgnore`/CAS 组合，避免重复建任务和重复推进状态。
3. 消费链路已统一 `mq_consume_log` 去重模型，重复消息不会产生重复副作用。
4. 命中日志口径已统一为 `幂等命中` 模板，可用于检索与审计。

---

## 5. DoD 对齐（P1-S2）

- [x] 重复请求不会写出重复业务记录（唯一约束 + CAS + `insertIgnore` 已闭环）。
- [x] 幂等命中日志可检索、可追踪（`幂等命中` 模板已在关键链路落地）。
- [x] 产出幂等策略清单 v2。
- [x] 产出唯一约束核对结果（见关联文档）。

---

（文件结束）
