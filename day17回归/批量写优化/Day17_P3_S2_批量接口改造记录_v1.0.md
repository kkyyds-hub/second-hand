# Day17 P3-S2 批量接口改造记录 v1.0

- 日期：2026-02-24
- 范围：Outbox 发布状态回写、ShipReminder 卡死回收写入优化
- 目标：降低数据库往返次数，补齐批量写事务边界

---

## 1. 改造点总览

| 场景 | 改造前 | 改造后 | 改造类型 |
|---|---|---|---|
| Outbox 发布结果回写 | `N` 次 `markSent/markFail` 单条更新 | 按成功/失败 ID 集合批量更新 | XML 批量 SQL |
| ShipReminder 卡死 RUNNING 回收 | `N` 次 `markFail` 单条更新 | 按重试延时分组批量 `markFailBatch` | XML 批量 SQL |
| 复杂批量插入（保留） | `AfterSaleMapper.batchInsertEvidences` | 保持现状 | XML 保留策略 |

---

## 2. 具体实现

### 2.1 Outbox 批量状态回写

- 新增 mapper 批量方法：
  - `MessageOutboxMapper.markSentBatch(ids)`
  - `MessageOutboxMapper.markFailBatch(ids, nextRetryTime)`
- SQL 统一加状态约束：`WHERE status IN ('NEW','FAIL')`，避免误更新。
- `OutboxPublishJob` 改为：
  1. 循环只负责发送并收集 `sentIds/failIds`
  2. 循环结束后一次调用批量回写
- 新增 `OutboxBatchStatusService.flushPublishResult(...)`：
  - `@Transactional(rollbackFor = Exception.class)`
  - 保证“成功集 + 失败集”状态回写同事务提交/回滚

### 2.2 ShipReminder 回收批量写

- 新增 mapper 方法：
  - `OrderShipReminderTaskMapper.markFailBatch(ids, nextRemindTime, lastError)`
- `OrderShipReminderTaskServiceImpl.recycleStaleRunningTasks(...)` 改为：
  1. 先按 `nextDelayMinutes` 分组收集任务 ID
  2. 每个延时组执行一次批量更新
- 兼容原语义：
  - 仍按“重试次数 -> 延时档位”决定下次提醒时间
  - 仍保留 `status='RUNNING'` 保护条件

---

## 3. 事务边界与失败策略

### 3.1 Outbox（外部发送 + DB 回写）

- 发送与状态回写解耦：先发送，再批量回写。
- 回写使用单事务；若回写失败：
  - 本批次状态不落库（回滚）
  - 下次调度会再次处理（符合至少一次投递模型）
- 行为可预期：可能重复投递，但由消费端幂等键兜底。

### 3.2 ShipReminder 回收

- 每个延时分组单 SQL 批量更新，失败影响当前分组，不会出现“同组部分成功部分失败”的逐条漂移。
- 回收逻辑可重入：下次调度可再次拉起未成功回收任务。

---

## 4. 变更文件清单

- `demo-service/src/main/java/com/demo/mapper/MessageOutboxMapper.java`
- `demo-service/src/main/resources/mapper/MessageOutboxMapper.xml`
- `demo-service/src/main/java/com/demo/service/serviceimpl/OutboxBatchStatusService.java`
- `demo-service/src/main/java/com/demo/job/OutboxPublishJob.java`
- `demo-service/src/main/java/com/demo/mapper/OrderShipReminderTaskMapper.java`
- `demo-service/src/main/resources/mapper/OrderShipReminderTaskMapper.xml`
- `demo-service/src/main/java/com/demo/service/serviceimpl/OrderShipReminderTaskServiceImpl.java`

---

（文件结束）
