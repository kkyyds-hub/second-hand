# Day17 P4-S4 与异步链路一致性说明 v1.0

- 日期：2026-02-24
- 目标：保证“主流程成功后事件可追踪、异步失败可补偿、重复消费无重复副作用”。

---

## 1. 一致性口径（冻结）

1. **主事务与 Outbox 同事务提交**  
   业务写库与 `message_outbox` 入库在同一事务中完成，事务回滚则两者同时回滚。
2. **提交后发送，避免脏发送**  
   MQ 发送通过 `afterCommit` 执行，避免“业务事务失败但消息已发”的不一致。
3. **发送失败不影响主链路**  
   发送异常仅记录日志并回写 Outbox `FAIL`，由调度任务按 `next_retry_time` 重试。
4. **消费侧幂等防重**  
   以 `mq_consume_log(consumer,event_id)` 唯一键抢占，重复消息直接 ACK。
5. **最终一致性可观测**  
   提供事件追踪、手动补偿、指标查询接口，支持线上排障与恢复演练。

---

## 2. 本次落地改造

### 2.1 主流程提交后发送（防“主成功/主失败与消息错位”）

- 文件：`demo-service/src/main/java/com/demo/service/serviceimpl/OrderServiceImpl.java`
- 改造点：`safePublish(...)` 新增事务感知逻辑。  
  - 有事务：注册 `afterCommit` 回调后发送 MQ；  
  - 无事务：保持立即发送（兼容非事务场景）。

结论：主事务未提交前不发送消息，避免脏消息进入异步链路。

### 2.2 Outbox 发送链路补齐“批量回写 + 可配置重试”

- 文件：`demo-service/src/main/java/com/demo/job/OutboxPublishJob.java`
- 文件：`demo-service/src/main/java/com/demo/service/serviceimpl/OutboxBatchStatusService.java`
- 文件：`demo-service/src/main/resources/application.yml`

改造点：
1. 发布任务支持 `publishOutboxMessagesOnce(limit)`，可被运维接口手动触发。  
2. 批量回写 `SENT/FAIL`（单事务），降低 DB 往返并保证状态一致。  
3. 重试延迟与批量上限配置化：  
   - `outbox.publish.batch-size`  
   - `outbox.publish.retry-delay-seconds`

### 2.3 失败观测与人工补偿能力

- 文件：`demo-service/src/main/java/com/demo/controller/admin/AdminOutboxOpsController.java`
- 文件：`demo-service/src/main/java/com/demo/mapper/MessageOutboxMapper.java`
- 文件：`demo-service/src/main/resources/mapper/MessageOutboxMapper.xml`
- 文件：`demo-service/src/main/java/com/demo/job/OutboxMonitorJob.java`

新增能力：
1. `GET /admin/ops/outbox/event/{eventId}`：按事件追踪 Outbox 状态。  
2. `POST /admin/ops/outbox/event/{eventId}/trigger-now`：指定事件立即重试。  
3. `POST /admin/ops/outbox/publish-once`：手动执行一轮发布。  
4. `GET /admin/ops/outbox/metrics`：查看 `NEW/SENT/FAIL` 与失败重试总量。  
5. 监控阈值配置化：  
   - `outbox.monitor.fail-threshold`  
   - `outbox.monitor.fail-retry-threshold`

### 2.4 消费侧幂等日志与语义统一（中文）

- 文件：`demo-service/src/main/java/com/demo/mq/consumer/InventoryUpdateConsumer.java`
- 文件：`demo-service/src/main/java/com/demo/mq/consumer/ProductReviewedNoticeConsumer.java`
- 文件：`demo-service/src/main/java/com/demo/mq/consumer/ProductForceOffShelfNoticeConsumer.java`
- 文件：`demo-service/src/main/java/com/demo/mq/consumer/ProductReportResolvedNoticeConsumer.java`

改造点：
1. 异常、丢弃、幂等命中日志统一为中文；  
2. 重复消费命中时明确输出 `幂等命中`；  
3. 系统异常统一 `NACK -> DLQ`，业务异常 `ACK 丢弃`，语义可追踪。

---

## 3. 失败恢复流程（摘要）

1. 通过 `metrics` 或监控日志发现 `FAIL` 堆积。  
2. 按 `eventId` 查询单事件状态。  
3. 对单事件执行 `trigger-now` 或执行 `publish-once` 批量恢复。  
4. 观察 `SENT` 增长与消费者幂等日志，确认副作用未重复。  

详细操作见：`day17回归/异步一致性治理/Day17_P4_S4_失败恢复流程_v1.0.md`

---

## 4. DoD 对齐（P4-S4）

- [x] 主链路成功后事件可追踪（eventId 查询 + metrics）  
- [x] 异步失败不影响主事务成功（Outbox 补偿重试）  
- [x] 消费重复不产生重复副作用（唯一键幂等 + 重复 ACK）  
- [x] 输出一致性说明与失败恢复流程文档  

---

（文件结束）
