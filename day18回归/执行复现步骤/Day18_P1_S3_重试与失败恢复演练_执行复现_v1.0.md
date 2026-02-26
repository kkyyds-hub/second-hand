# Day18 P1-S3 重试与失败恢复演练 执行复现 v1.0

- 日期：2026-02-25
- 目标：验证“重试行为与状态机一致”，并完成一次可复现的人工恢复演练。
- 说明：本复现使用管理端接口 + SQL 核验，不要求修改代码。

---

## 1. 前置条件

1. 服务已启动并可访问。
2. 使用测试库（建议：`secondhand2`）。
3. 已准备管理员 token。
4. 已具备 `message_outbox/order_ship_timeout_task/order_refund_task/order_ship_reminder_task` 查询权限。

---

## 2. 场景 A：Outbox 失败恢复演练

1. 查询指标：
   - `GET /admin/ops/outbox/metrics`
2. 选择一条 `NEW/FAIL` 事件（记录 `eventId`）。
3. 触发立即重试：
   - `POST /admin/ops/outbox/event/{eventId}/trigger-now`
4. 立即发布一轮：
   - `POST /admin/ops/outbox/publish-once?limit=50`
5. 预期：
   - 返回包含 `pulled/sent/failed`；
   - 目标事件状态推进（理想为 `SENT`，失败则 `FAIL + retry_count` 增加）。

---

## 3. 场景 B：发货超时任务立即重试

1. 查询任务：
   - `GET /admin/ops/tasks/ship-timeout?status=PENDING&page=1&pageSize=50`
2. 选取 `taskId`，记录 `retry_count/next_retry_time/last_error`。
3. 触发立即重试：
   - `POST /admin/ops/tasks/ship-timeout/{taskId}/trigger-now`
4. 手动执行一轮：
   - `POST /admin/ops/tasks/ship-timeout/run-once?limit=200`
5. 预期：
   - 任务推进到 `DONE` 或 `CANCELLED`；
   - 若仍 `PENDING`，应出现新的 `next_retry_time` 与错误原因。

---

## 4. 场景 C：退款失败任务恢复

1. 查询失败任务：
   - `GET /admin/ops/tasks/refund?status=FAILED&page=1&pageSize=50`
2. 选取 `taskId`，记录 `fail_reason/retry_count`。
3. 重置为待执行：
   - `POST /admin/ops/tasks/refund/{taskId}/reset`
4. 手动执行一轮：
   - `POST /admin/ops/tasks/refund/run-once?limit=200`
5. 预期：
   - 成功推进到 `SUCCESS` 或按重试策略回到 `FAILED`（并更新 `next_retry_time`）。

---

## 5. 场景 D：发货提醒任务恢复

1. 查询任务：
   - `GET /admin/ops/tasks/ship-reminder?status=FAILED&page=1&pageSize=50`
2. 选取 `taskId`。
3. 触发立即重试：
   - `POST /admin/ops/tasks/ship-reminder/{taskId}/trigger-now`
4. 手动执行一轮：
   - `POST /admin/ops/tasks/ship-reminder/run-once?limit=200`
5. 预期：
   - 任务推进到 `SUCCESS` 或业务终态分流为 `CANCELLED`。

---

## 6. SQL 核验

```sql
-- A) Outbox 状态与重试次数
SELECT id, event_id, status, retry_count, next_retry_time, updated_at
FROM message_outbox
ORDER BY id DESC
LIMIT 50;

-- B) 发货超时任务
SELECT id, order_id, status, retry_count, next_retry_time, last_error, update_time
FROM order_ship_timeout_task
ORDER BY id DESC
LIMIT 50;

-- C) 退款任务
SELECT id, order_id, status, retry_count, next_retry_time, fail_reason, update_time
FROM order_refund_task
ORDER BY id DESC
LIMIT 50;

-- D) 发货提醒任务
SELECT id, order_id, level, status, retry_count, remind_time, running_at, last_error, update_time
FROM order_ship_reminder_task
ORDER BY id DESC
LIMIT 50;
```

---

## 7. DoD 勾选

- [ ] 重试行为与状态机一致（状态迁移符合 Mapper 条件约束）。
- [ ] 至少完成一次 Outbox 人工恢复演练。
- [ ] 至少完成一次任务链路人工恢复演练。
- [ ] 关键步骤有执行记录与 SQL 核验结果。

---

（文件结束）
