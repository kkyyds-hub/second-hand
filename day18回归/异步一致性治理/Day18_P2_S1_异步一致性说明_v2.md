# Day18 P2-S1 异步一致性说明 v2

- 日期：2026-02-25
- 对应阶段：`Step P2-S1：异步最终一致性收口`
- 目标：固化“主流程成功后异步链路最终可达一致状态”，并证明“重复消息不产生重复副作用”。

---

## 1. 一致性口径（P2-S1 冻结）

1. 生产端：业务事务与 Outbox 入库同事务提交。
2. 发布端：Outbox 按 `NEW/FAIL + next_retry_time` 拉取，发布失败可重试。
3. 回写端：发送成功/失败批量回写在单事务内完成。
4. 消费端：以 `mq_consume_log(consumer,event_id)` 唯一键抢占，重复消息直接 ACK。
5. 运维端：提供 event 查询、trigger-now、publish-once、metrics，支持人工恢复。

---

## 2. 链路复核结论（代码级）

## 2.1 Outbox 拉取、发布、回写、监控

1. `OutboxPublishJob.publishOutboxMessagesOnce`：
   - 拉取：`messageOutboxMapper.listPending(limit)`（仅 `NEW/FAIL` 且到期）
   - 发布：逐条 `rabbitTemplate.convertAndSend(...)`
   - 回写：收集 `sentIds/failIds` 后统一调用 `outboxBatchStatusService.flushPublishResult(...)`
2. `OutboxBatchStatusService.flushPublishResult`：
   - `@Transactional(rollbackFor = Exception.class)` 包裹 `markSentBatch + markFailBatch`
   - 避免“只更新一半状态”的中间态
3. `MessageOutboxMapper.xml`：
   - `listPending`：保语义优化（NEW/FAIL + 到期 + limit）
   - `markSentBatch`：仅允许 `NEW/FAIL -> SENT`
   - `markFailBatch`：仅允许 `NEW/FAIL -> FAIL`，并 `retry_count + 1`
4. `OutboxMonitorJob`：
   - 指标：`new/sent/fail/failRetrySum`
   - 阈值告警：`outbox.monitor.fail-threshold` / `outbox.monitor.fail-retry-threshold`

## 2.2 消费端 mq_consume_log 去重语义

1. 去重模型：
   - 先写 `mq_consume_log(status=PROCESSING)` 抢占
   - 命中 `DuplicateKeyException` 则判定重复消息，直接 ACK 返回
2. 统一实现样式：
   - `OrderPaidConsumer`
   - `OrderTimeoutConsumer`
   - `OrderStatusChangedConsumer`
   - 以及 `InventoryUpdateConsumer`、商品治理三类消费者
3. 唯一键约束：
   - `mq_consume_log.uk_consumer_event(consumer,event_id)`
   - `message_outbox.uk_event_id(event_id)`

---

## 3. “至少一次投递 + 幂等消费”设计说明

1. 至少一次投递：
   - Outbox 失败不丢弃，保留在 `FAIL` 并递增 `retry_count`
   - 到达 `next_retry_time` 后再次拉取发布
   - 人工可通过 `trigger-now + publish-once` 立刻补偿
2. 幂等消费：
   - 同一消费者重复收到同一 `event_id` 时，唯一键冲突短路
   - 不再执行副作用逻辑，直接 ACK
3. 一致性边界：
   - 主链路不等待消费完成，采用最终一致性
   - 异步链路通过“可重试发布 + 幂等消费”保证可收敛

---

## 4. 动态验证摘要（2026-02-25）

演练对象：
1. 订单：`orderId=900039`（`status=paid`）
2. 事件：`eventId=42729be2-95e8-43c6-9adc-78ce82a8df39`（`event_type=ORDER_PAID`）

执行过程（受控演练）：
1. 前置状态：
   - Outbox：`SENT|retry=0`
   - `mq_consume_log`（`OrderPaidConsumer,eventId`）：`count=1`
   - 发货提醒任务数（`order_id=900039`）：`count=3`
2. 人工将该事件置为 `FAIL` 且可立即重试（测试库演练种子）
3. 调用：
   - `POST /admin/ops/outbox/event/{eventId}/trigger-now`（`updatedRows=1`）
   - `POST /admin/ops/outbox/publish-once?limit=50`（`pulled=1,sent=1,failed=0`）
4. 后置状态：
   - Outbox：`SENT|retry=1`
   - `mq_consume_log` 计数仍 `1`（未新增重复消费记录）
   - 发货提醒任务数仍 `3`（无重复副作用）
   - 全局重复组扫描：`mq_consume_log_dup_groups=0`

结论：
1. 可证明“主成功后异步事件可追踪且可恢复，不丢失”。
2. 可证明“重复消息不会产生重复业务副作用”。

---

## 5. DoD 对齐（P2-S1）

- [x] 可证明“主成功、异步不丢”。  
- [x] 重复消息不会产生重复业务副作用。  
- [x] 已输出异步一致性说明 v2。  
- [x] 已输出复现步骤与执行记录。  

---

## 6. 代码证据索引

1. `demo-service/src/main/java/com/demo/job/OutboxPublishJob.java`
2. `demo-service/src/main/java/com/demo/service/serviceimpl/OutboxBatchStatusService.java`
3. `demo-service/src/main/resources/mapper/MessageOutboxMapper.xml`
4. `demo-service/src/main/java/com/demo/job/OutboxMonitorJob.java`
5. `demo-service/src/main/java/com/demo/controller/admin/AdminOutboxOpsController.java`
6. `demo-service/src/main/resources/mapper/MqConsumeLogMapper.xml`
7. `demo-service/src/main/java/com/demo/mq/consumer/OrderPaidConsumer.java`
8. `demo-service/src/main/java/com/demo/mq/consumer/OrderTimeoutConsumer.java`
9. `demo-service/src/main/java/com/demo/mq/consumer/OrderStatusChangedConsumer.java`
10. `demo-service/src/main/resources/application.yml`

---

（文件结束）
