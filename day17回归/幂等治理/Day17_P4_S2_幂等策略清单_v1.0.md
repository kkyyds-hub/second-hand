# Day17 P4-S2 幂等策略清单 v1.0

- 日期：2026-02-24
- 目标：统一“重复请求不产生重复副作用”的实现与验收口径。
- 范围：订单主链路、任务链路、通知链路、积分链路、MQ 消费链路。

---

## 1. 统一幂等口径（本阶段冻结）

1. **先定义业务幂等键**：每条关键写链路必须有可落库的幂等键（如 `order_no`、`event_id`、`idempotency_key`）。  
2. **数据库层兜底防重**：优先使用唯一索引 + `INSERT IGNORE` / 捕获唯一键冲突。  
3. **状态机型写操作**：使用条件更新（期望状态）实现幂等，`rows=0` 后回查分流。  
4. **重复请求返回语义**：统一返回“成功但已处理”（不抛系统异常）。  
5. **日志可追踪**：幂等命中日志统一模板：  
   `幂等命中：action={}, idemKey={}, detail={}`。

---

## 2. 核心链路幂等键与防重策略

| 链路 | 幂等键 | 防重实现 | 命中后语义 |
|---|---|---|---|
| 用户支付 `payOrder` | `orderId` + 订单状态 | `pending -> paid` 条件更新，`rows=0` 回查状态 | 返回“订单已支付，无需重复操作” |
| 支付回调 `handlePaymentCallback` | `orderNo` + 订单状态 | `pending -> paid` 条件更新，重复回调回查分流 | 返回“订单已支付，回调幂等成功” |
| 取消订单 `cancelOrder` | `orderId` + 订单状态 | `pending -> cancelled` 条件更新，`rows=0` 回查状态 | 返回“订单已取消，无需重复操作” |
| 发货 `shipOrder` | `orderId` + 订单状态 | `paid -> shipped` 条件更新，`rows=0` 回查状态 | 返回“订单已发货，无需重复操作” |
| 确认收货 `confirmOrder` | `orderId` + 订单状态 | `shipped -> completed` 条件更新，`rows=0` 回查状态 | 返回“订单已确认收货，无需重复操作” |
| 发货超时任务创建 | `order_id` | `order_ship_timeout_task.uk_ship_timeout_order_id` + `insertIgnore` | 命中仅记录日志，不影响主流程 |
| 发货提醒任务创建 | `order_id + level` | `order_ship_reminder_task.uk_order_level` + `insertIgnore` | 命中仅记录日志，不重复建任务 |
| 退款任务创建 | `order_id + refund_type` / `idempotency_key` | `order_refund_task` 双唯一键 + `insertIgnore` | 命中仅记录日志，不重复建任务 |
| 积分发放 | `user_id + biz_type + biz_id` | `points_ledger.uniq_points_biz` + 捕获 `DuplicateKeyException` | 命中按成功处理，不重复发放 |
| Outbox 事件落库 | `event_id` | `message_outbox.uk_event_id` | 命中按重复事件处理 |
| MQ 消费抢占 | `consumer + event_id` | `mq_consume_log.uk_consumer_event` | 命中直接跳过重复消费 |
| 站内消息发送 | `orderId + fromUserId + clientMsgId` | Mongo `order_messages` 复合唯一索引 `uniq_order_clientMsg` | 命中返回已存在消息 |

---

## 3. 重复请求返回语义（统一规则）

1. **用户可见接口**：重复提交返回业务成功语义（“已处理/无需重复操作”）。  
2. **内部任务链路**：命中幂等键时不抛错，状态推进保持稳定。  
3. **异步消费链路**：命中去重键直接跳过，不重复执行副作用。  
4. **唯一键冲突**：仅作为幂等命中处理，不视为系统异常。

---

## 4. 幂等命中日志规范（已落地首批）

### 4.1 日志模板

```text
幂等命中：action=<业务动作>, idemKey=<幂等键>, detail=<命中上下文>
```

### 4.2 本次已统一的关键位置

1. `demo-service/src/main/java/com/demo/service/serviceimpl/OrderServiceImpl.java`  
   - 支付 / 支付回调 / 发货 / 取消 / 确认收货幂等命中分支。  
2. `demo-service/src/main/java/com/demo/service/serviceimpl/MessageServiceImpl.java`  
   - `clientMsgId` 重复写入命中分支。  
3. `demo-service/src/main/java/com/demo/service/serviceimpl/SystemNoticeServiceImpl.java`  
   - 系统通知重复发送命中分支。  
4. `demo-service/src/main/java/com/demo/service/serviceimpl/OrderNoticeServiceImpl.java`  
   - 订单通知重复写入命中分支。  
5. `demo-service/src/main/java/com/demo/service/serviceimpl/PointsServiceImpl.java`  
   - 买家/卖家积分重复发放命中分支。  
6. `demo-service/src/main/java/com/demo/service/serviceimpl/OrderShipReminderTaskServiceImpl.java`  
   - 提醒任务 `insertIgnore` 命中分支。  
7. `demo-service/src/main/java/com/demo/service/serviceimpl/OrderShipTimeoutTaskProcessor.java`  
   - 退款任务创建 `insertIgnore` 命中分支。  
8. `demo-service/src/main/java/com/demo/service/serviceimpl/OrderRefundTaskProcessor.java`  
   - 退款任务并发成功（`rows=0 + latest=SUCCESS`）命中分支。

---

## 5. 约束脚本与环境约束

1. MySQL 唯一约束脚本：  
   `day17回归/幂等治理/Day17_P4_S2_唯一约束脚本_v1.0.sql`
2. Mongo 幂等索引：  
   通过 `Message` 实体上的 `@CompoundIndex(name = "uniq_order_clientMsg", unique = true)` 约束。  
3. 执行建议：  
   - 先在测试库执行唯一约束脚本；  
   - 再按执行复现文档做重复提交回归；  
   - 最后抽查日志关键字 `幂等命中：`。

---

## 6. DoD 对齐结果（P4-S2）

- [x] 核心接口重复提交不会产生重复数据  
- [x] 核心链路幂等键已形成清单并可追踪  
- [x] 数据库层唯一约束脚本已提供  
- [x] 幂等命中日志口径已统一到 `幂等命中` 模板

---

（文件结束）
