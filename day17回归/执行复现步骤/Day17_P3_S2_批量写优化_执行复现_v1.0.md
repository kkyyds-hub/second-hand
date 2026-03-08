# Day17 Step P3-S2：批量写优化执行复现步骤

- 文档版本：v1.0
- 日期：2026-02-24
- 目标：验证批量写链路已生效，数据库交互次数下降且失败行为可预期

---

## 1. 前置条件

1. 服务可正常启动。
2. 已准备测试数据：
   - `message_outbox` 中存在多条 `NEW/FAIL` 记录（建议 20+）。
   - `order_ship_reminder_task` 中存在多条 stale RUNNING 记录（建议 20+）。
3. 开启 dev SQL 日志，便于统计 SQL 次数。

---

## 2. Outbox 批量写复现

### 2.1 执行
1. 触发 `OutboxPublishJob` 一次（或等待调度执行）。
2. 观察本轮 outbox 处理日志：
   - `Outbox sent success ...`
   - `Outbox send failed ...`
   - `Outbox flush result done, sent=?, failed=?`

### 2.2 校验点
1. 状态回写 SQL 由“每条一条 update”变为“批量 update（IN ...）”。
2. 同一轮中，状态回写固定 1~2 次（取决于 sent/fail 是否都非空）。
3. `message_outbox` 结果符合预期：
   - 成功集 -> `SENT`
   - 失败集 -> `FAIL` 且 `retry_count` 增加、`next_retry_time` 刷新

---

## 3. ShipReminder 回收批量写复现

### 3.1 执行
1. 触发提醒任务调度一次（调用 `processDueTasks` 链路）。
2. 确保命中 stale RUNNING 回收逻辑。

### 3.2 校验点
1. 回收 SQL 由“逐条 `markFail`”变为“按延时档位分组批量 `markFailBatch`”。
2. 本轮更新次数应小于等于 4（2/5/15/30 分钟档位）。
3. 任务状态由 RUNNING -> FAILED，`retry_count` 增加，`remind_time` 按档位后移。

---

## 4. 失败行为与可预期性

1. Outbox 回写失败时：
   - 事务回滚，不出现“本轮回写一半成功一半失败”的 DB 中间态。
   - 下次调度可重试，行为符合至少一次投递模型。
2. ShipReminder 批量失败时：
   - 当前分组失败不影响其他逻辑分组；
   - 下次调度可继续回收未处理项。

---

## 5. DoD 勾选

- [ ] 典型批量场景数据库交互次数下降  
- [ ] 批量失败可回滚且行为可预期  
- [ ] 编译通过（建议执行 IDEA Maven：`-pl demo-service -am -DskipTests compile`）

---

（文件结束）
