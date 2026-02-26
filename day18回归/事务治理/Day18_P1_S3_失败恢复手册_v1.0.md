# Day18 P1-S3 失败恢复手册 v1.0

- 日期：2026-02-25
- 对应阶段：`Step P1-S3：重试机制与失败处理标准化`
- 目标：让运维/值班同学可在不改代码的前提下，完成一次标准失败恢复。

---

## 1. 适用范围

1. 发货超时任务：`order_ship_timeout_task`
2. 退款任务：`order_refund_task`
3. 发货提醒任务：`order_ship_reminder_task`
4. Outbox 发布：`message_outbox`

---

## 2. 恢复前检查

1. 应用服务已启动且健康检查通过。
2. 具备管理员 token（可调用 `/admin/ops/tasks/**`、`/admin/ops/outbox/**`）。
3. 可访问测试库并具备只读查询能力（必要时使用运维审批执行更新）。
4. 先确认“依赖是否恢复”：
   - MQ 连通性
   - 数据库可写
   - 相关外部接口（若有）

---

## 3. 快速分诊口径

| 现象 | 判定优先级 | 处置入口 |
|---|---|---|
| `message_outbox` FAIL 堆积 | 高 | 先看 `/admin/ops/outbox/metrics`，再单事件 trigger + publish-once |
| 发货超时任务长期 `PENDING` 且 `next_retry_time` 未来时间 | 中 | `ship-timeout/{taskId}/trigger-now` + `run-once` |
| 退款任务长期 `FAILED` | 高 | 先排依赖，再 `refund/{taskId}/reset` + `refund/run-once` |
| 发货提醒任务长期 `FAILED/RUNNING` | 中 | 自动回收 + `ship-reminder/{taskId}/trigger-now` + `run-once` |
| 任务已 `CANCELLED/DONE/SUCCESS` 仍想重试 | 低（通常不应重试） | 先按“不可重试”规则复核，不直接重放 |

---

## 4. 标准恢复流程

## 4.1 Outbox 失败恢复

1. 观察指标：
   - `GET /admin/ops/outbox/metrics`
2. 精确定位事件：
   - `GET /admin/ops/outbox/event/{eventId}`
3. 触发单事件立即重试：
   - `POST /admin/ops/outbox/event/{eventId}/trigger-now`
4. 手动执行一轮发布：
   - `POST /admin/ops/outbox/publish-once?limit=50`
5. 验证：
   - 目标事件 `status` 应从 `NEW/FAIL` 推进到 `SENT` 或保留 `FAIL` 且 `retry_count` 增加。

## 4.2 发货超时任务恢复

1. 拉取任务：
   - `GET /admin/ops/tasks/ship-timeout?status=PENDING&page=1&pageSize=50`
2. 选择目标任务并检查：
   - `retry_count`、`next_retry_time`、`last_error`
3. 人工立即触发：
   - `POST /admin/ops/tasks/ship-timeout/{taskId}/trigger-now`
4. 手动跑一轮：
   - `POST /admin/ops/tasks/ship-timeout/run-once?limit=200`
5. 验证：
   - 正常推进到 `DONE` 或根据订单终态推进到 `CANCELLED`。

## 4.3 退款任务恢复

1. 拉取失败任务：
   - `GET /admin/ops/tasks/refund?status=FAILED&page=1&pageSize=50`
2. 先复核失败原因：
   - 若依赖未恢复，不执行 reset。
3. 重置任务：
   - `POST /admin/ops/tasks/refund/{taskId}/reset`
4. 手动跑一轮：
   - `POST /admin/ops/tasks/refund/run-once?limit=200`
5. 验证：
   - 成功时 `status=SUCCESS`；失败时 `status=FAILED` 且 `retry_count` 增加、`fail_reason` 更新。

## 4.4 发货提醒任务恢复

1. 拉取任务：
   - `GET /admin/ops/tasks/ship-reminder?status=FAILED&page=1&pageSize=50`
   - 必要时检查 `RUNNING` 任务（等待自动回收或人工触发）。
2. 触发立即重试：
   - `POST /admin/ops/tasks/ship-reminder/{taskId}/trigger-now`
3. 手动跑一轮：
   - `POST /admin/ops/tasks/ship-reminder/run-once?limit=200`
4. 验证：
   - 推进到 `SUCCESS`（或因终态进入 `CANCELLED`）。

---

## 5. 不可重试场景处置规则

1. 已终态任务（`DONE/SUCCESS/CANCELLED/SENT`）默认不做强制重放。
2. 命中幂等成功（如退款任务并发回查 `latestStatus=SUCCESS`）视为已收敛。
3. 业务语义已变化（订单终态）时，不通过“改状态”绕过状态机；如需补偿必须走业务审批流程。

---

## 6. 演练 SQL（只读核验）

```sql
-- Outbox 指标
SELECT status, COUNT(*) AS cnt, COALESCE(SUM(retry_count),0) AS retry_sum
FROM message_outbox
GROUP BY status;

-- 发货超时任务
SELECT id, order_id, status, retry_count, next_retry_time, last_error, update_time
FROM order_ship_timeout_task
ORDER BY id DESC
LIMIT 50;

-- 退款任务
SELECT id, order_id, refund_type, status, retry_count, next_retry_time, fail_reason, update_time
FROM order_refund_task
ORDER BY id DESC
LIMIT 50;

-- 发货提醒任务
SELECT id, order_id, level, status, retry_count, remind_time, running_at, last_error, update_time
FROM order_ship_reminder_task
ORDER BY id DESC
LIMIT 50;
```

---

## 7. 演练完成判定

1. 至少完成 1 个 Outbox 恢复案例（trigger-now + publish-once）。
2. 至少完成 1 个任务链路恢复案例（ship-timeout/refund/ship-reminder 任一）。
3. 执行后状态推进符合状态机，不出现“非法逆向跃迁”。
4. 已回填 `day18回归/执行记录/Day18_P1_S3_重试与失败恢复执行记录_v1.0.md`。

---

## 8. 代码证据索引

1. `demo-service/src/main/java/com/demo/controller/admin/AdminTaskOpsController.java`
2. `demo-service/src/main/java/com/demo/controller/admin/AdminOutboxOpsController.java`
3. `demo-service/src/main/java/com/demo/job/OutboxPublishJob.java`
4. `demo-service/src/main/java/com/demo/service/serviceimpl/OrderShipTimeoutTaskProcessor.java`
5. `demo-service/src/main/java/com/demo/service/serviceimpl/OrderRefundTaskProcessor.java`
6. `demo-service/src/main/java/com/demo/service/serviceimpl/OrderShipReminderTaskProcessor.java`
7. `demo-service/src/main/resources/mapper/OrderShipTimeoutTaskMapper.xml`
8. `demo-service/src/main/resources/mapper/OrderRefundTaskMapper.xml`
9. `demo-service/src/main/resources/mapper/OrderShipReminderTaskMapper.xml`
10. `demo-service/src/main/resources/mapper/MessageOutboxMapper.xml`

---

（文件结束）
