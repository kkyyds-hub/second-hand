# Day19 P1-S2 轻量指标与告警阈值表 v1.0

- 日期：`2026-03-04`
- 对应范围：`Day19_Scope_Freeze_v1.0.md -> Phase 1 / Step P1-S2`
- 当前状态：`已落地（文档版）；待执行复现与执行记录回填`
- 目标：形成可直接用于排障的“阈值 -> 处置动作”表，并确保动作可通过现有运维接口实操复现

---

## 0. 使用说明（先看这个）

1. 先看第 2 章阈值总表，确认当前命中的告警项（Warning 或 Error）。
2. 再按第 3 章选择对应处置流程（积压 / 失败重试 / 成功率）。
3. 执行过程中按第 4 章日志关键字和第 5 章 SQL 模板定位问题根因。
4. 最后用第 6 章运维接口清单做动作复核，并按第 7 章 DoD 勾选验收。

---

## 1. 指标口径（冻结版）

## 1.1 Outbox 指标

1. `new/sent/fail/failRetrySum`  
来源：`GET /admin/ops/outbox/metrics` 与 `OutboxMonitorJob`。
2. `outboxBacklog = new + fail`
3. `outboxPublishSuccessRate = sent / (sent + failed)`  
来源：`POST /admin/ops/outbox/publish-once` 返回字段 `sent/failed`。  
当 `sent + failed = 0` 时，本轮成功率记为 `N/A`（无样本）。

## 1.2 任务指标（ship-timeout/refund/ship-reminder）

1. `taskBacklog`（可执行积压）  
- ship-timeout：`status='PENDING' AND deadline_time<=NOW() AND (next_retry_time IS NULL OR next_retry_time<=NOW())`
- refund：`status IN ('PENDING','FAILED') AND (next_retry_time IS NULL OR next_retry_time<=NOW())`
- ship-reminder：`status IN ('PENDING','FAILED') AND remind_time<=NOW()`
2. `taskHighRetryCount`  
定义：`retry_count >= 3` 的任务数。
3. `taskSevereRetryCount`  
定义：`retry_count >= 5` 的任务数。
4. `taskRunSuccessRate = runOnce.success / min(limit, taskBacklogBefore)`  
来源：`POST /admin/ops/tasks/*/run-once` 的 `success`，分母来自运行前 SQL 快照。

---

## 2. 告警阈值总表（轻量分级）

> 说明：表中 `Warning/Error` 是运维分级阈值；Outbox 代码内置单阈值告警仍由 `application.yml` 控制（`fail-threshold=5`、`fail-retry-threshold=10`）。

| 告警ID | 指标 | Warning | Error | 观测窗口 | 处置流程 |
|---|---|---:|---:|---|---|
| BKL-OUTBOX | `outboxBacklog = new + fail` | `>=100` | `>=300` | 连续 3 次快照（间隔 1 分钟） | `FLOW-A` |
| BKL-ST | `ship-timeout taskBacklog` | `>=200` | `>=600` | 连续 3 次快照（间隔 1 分钟） | `FLOW-A` |
| BKL-RF | `refund taskBacklog` | `>=200` | `>=600` | 连续 3 次快照（间隔 1 分钟） | `FLOW-A` |
| BKL-SR | `ship-reminder taskBacklog` | `>=200` | `>=600` | 连续 3 次快照（间隔 1 分钟） | `FLOW-A` |
| RETRY-OUTBOX-CNT | Outbox `failCount` | `>=5` | `>=10` | 连续 3 次快照 | `FLOW-B` |
| RETRY-OUTBOX-SUM | Outbox `failRetrySum` | `>=10` | `>=20` | 连续 3 次快照 | `FLOW-B` |
| RETRY-TASK-HIGH | `taskHighRetryCount` | `>=5` | `>=15` | 连续 2 次快照（间隔 1 分钟） | `FLOW-B` |
| RETRY-TASK-SEV | `taskSevereRetryCount` | `>=1` | `>=5` | 连续 2 次快照（间隔 1 分钟） | `FLOW-B` |
| SR-OUTBOX | `outboxPublishSuccessRate` | `<95%` | `<90%` | 连续 3 轮 `publish-once` | `FLOW-C` |
| SR-TASK | `taskRunSuccessRate` | `<90%` | `<80%` | 连续 3 轮 `run-once` | `FLOW-C` |

---

## 3. 三类处置流程（可实操复现）

## FLOW-A：积压处置（Backlog）

### A1 触发条件

满足任一 `BKL-*` 阈值。

### A2 处置动作（按顺序）

1. Outbox 执行 1 轮排空：
   - `POST /admin/ops/outbox/publish-once?limit=100`
2. 三类任务各执行 1 轮：
   - `POST /admin/ops/tasks/ship-timeout/run-once?limit=200`
   - `POST /admin/ops/tasks/refund/run-once?limit=200`
   - `POST /admin/ops/tasks/ship-reminder/run-once?limit=200`
3. 1 分钟后再次采样（Outbox metrics + SQL backlog 快照）。
4. 若仍高于 Warning，重复步骤 1~3 共 3 轮。
5. 若仍高于 Error，进入单条补偿：
   - Outbox：`POST /admin/ops/outbox/event/{eventId}/trigger-now`
   - ship-timeout：`POST /admin/ops/tasks/ship-timeout/{taskId}/trigger-now`
   - refund：`POST /admin/ops/tasks/refund/{taskId}/reset`
   - ship-reminder：`POST /admin/ops/tasks/ship-reminder/{taskId}/trigger-now`

### A3 通过标准

1. 10 分钟内 `taskBacklog` 或 `outboxBacklog` 下降至少 `30%`，或回落到 Warning 以下。
2. 补偿接口返回 `updatedRows > 0`，后续一轮 `run-once/publish-once` 有正向处理结果。

---

## FLOW-B：失败重试处置（Retry）

### B1 触发条件

满足任一 `RETRY-*` 阈值。

### B2 处置动作（按顺序）

1. 用 SQL 拉取高重试 TOP 样本（见第 5 章 SQL-03 / SQL-07）。
2. 对 TOP 样本执行单条补偿：
   - Outbox -> `event/{eventId}/trigger-now`
   - ship-timeout -> `{taskId}/trigger-now`
   - refund -> `{taskId}/reset`
   - ship-reminder -> `{taskId}/trigger-now`
3. 立即执行 1 轮批处理：
   - Outbox：`publish-once`
   - 三类任务：`run-once`
4. 5 分钟后复核 `retry_count` 与 `failRetrySum` 是否停止放大。
5. 若 Error 仍持续 2 轮，按日志关键字定位根因（MQ 连通性、业务状态分流、数据脏样本）并继续补偿。

### B3 通过标准

1. `failRetrySum` 不再持续增长。
2. `retry_count>=5` 的任务样本数量下降。
3. 本轮补偿动作对应接口均返回成功（`code=1` 且 `updatedRows>0` 或 run 成功数提升）。

---

## FLOW-C：处理成功率处置（Success Rate）

### C1 触发条件

满足 `SR-OUTBOX` 或 `SR-TASK`。

### C2 处置动作（按顺序）

1. 采样前快照：
   - Outbox：记录 `metrics`
   - Task：记录运行前 `taskBacklogBefore`（SQL）
2. 执行处理：
   - Outbox：`POST /admin/ops/outbox/publish-once?limit=100`
   - Task：`POST /admin/ops/tasks/*/run-once?limit=200`
3. 计算成功率：
   - Outbox：`sent/(sent+failed)`
   - Task：`success/min(limit, taskBacklogBefore)`
4. 若低于 Warning：执行 1 轮单条补偿 + 再跑 1 轮批处理。
5. 若低于 Error：连续跑 3 轮，结合日志关键字定位失败类型后按对应接口补偿。

### C3 通过标准

1. Outbox 连续 3 轮成功率回升到 `>=95%`。
2. 任务连续 3 轮成功率回升到 `>=90%`。
3. 失败日志量明显下降（同关键字、同时间窗对比）。

---

## 4. 日志关键字与 SQL 对照表

| 场景 | 日志检索关键字 | SQL 对照 | 运维动作 |
|---|---|---|---|
| Outbox 指标快照 | `Outbox 监控指标` | `SQL-01/SQL-02` | `GET /admin/ops/outbox/metrics` |
| Outbox 告警触发 | `Outbox 告警` | `SQL-03` | `publish-once` + `event trigger-now` |
| Outbox 发送失败 | `Outbox 发送失败` | `SQL-03` | 单事件补偿后再 `publish-once` |
| Outbox 回写结果 | `Outbox 回写完成` | `SQL-01` | 验证 sent/failed 回写趋势 |
| 任务批处理结果 | `ship-timeout task job finish` / `refund-task job finish` / `ship-reminder job finish` | `SQL-04/05/06` | 对应 `run-once` |
| 任务人工执行结果 | `admin run ship-timeout once` / `admin run refund once` / `admin run ship-reminder once` | `SQL-04/05/06` | 连续 3 轮 run-once |
| 任务单条补偿 | `admin trigger ship-timeout now` / `admin reset refund task` / `admin trigger ship-reminder now` | `SQL-07/08` | 对应 `trigger-now/reset` |

---

## 5. SQL 模板（排障快照）

## SQL-01 Outbox 状态快照（排除测试 exchange）

```sql
SELECT status, COUNT(*) AS cnt
FROM message_outbox
WHERE exchange_name NOT IN ('bad.exchange')
GROUP BY status;
```

## SQL-02 Outbox 积压快照

```sql
SELECT
  SUM(CASE WHEN status = 'NEW' THEN 1 ELSE 0 END) AS new_cnt,
  SUM(CASE WHEN status = 'FAIL' THEN 1 ELSE 0 END) AS fail_cnt,
  SUM(CASE WHEN status IN ('NEW','FAIL') THEN 1 ELSE 0 END) AS backlog_cnt
FROM message_outbox
WHERE exchange_name NOT IN ('bad.exchange');
```

## SQL-03 Outbox 高重试样本

```sql
SELECT id, event_id, exchange_name, status, retry_count, next_retry_time, updated_at
FROM message_outbox
WHERE status = 'FAIL'
  AND exchange_name NOT IN ('bad.exchange')
ORDER BY retry_count DESC, updated_at DESC
LIMIT 20;
```

## SQL-04 ship-timeout 可执行积压

```sql
SELECT COUNT(1) AS runnable_cnt
FROM order_ship_timeout_task
WHERE status = 'PENDING'
  AND deadline_time <= NOW()
  AND (next_retry_time IS NULL OR next_retry_time <= NOW());
```

## SQL-05 refund 可执行积压

```sql
SELECT COUNT(1) AS runnable_cnt
FROM order_refund_task
WHERE status IN ('PENDING', 'FAILED')
  AND (next_retry_time IS NULL OR next_retry_time <= NOW());
```

## SQL-06 ship-reminder 可执行积压

```sql
SELECT COUNT(1) AS runnable_cnt
FROM order_ship_reminder_task
WHERE status IN ('PENDING', 'FAILED')
  AND remind_time <= NOW();
```

## SQL-07 三类任务高重试样本（retry_count >= 3）

```sql
SELECT 'ship-timeout' AS task_type, id, order_id, retry_count, next_retry_time, last_error, update_time
FROM order_ship_timeout_task
WHERE retry_count >= 3
UNION ALL
SELECT 'refund' AS task_type, id, order_id, retry_count, next_retry_time, fail_reason, update_time
FROM order_refund_task
WHERE retry_count >= 3
UNION ALL
SELECT 'ship-reminder' AS task_type, id, order_id, retry_count, remind_time, last_error, update_time
FROM order_ship_reminder_task
WHERE retry_count >= 3
ORDER BY retry_count DESC, update_time DESC
LIMIT 30;
```

## SQL-08 ship-reminder 超时 RUNNING 样本（默认超时 5 分钟）

```sql
SELECT id, order_id, status, retry_count, running_at, remind_time, last_error, update_time
FROM order_ship_reminder_task
WHERE status = 'RUNNING'
  AND running_at <= DATE_SUB(NOW(), INTERVAL 5 MINUTE)
ORDER BY running_at ASC
LIMIT 30;
```

---

## 6. 运维接口实操清单（复现入口）

| 类别 | 接口 | 用途 | 通过信号 |
|---|---|---|---|
| Outbox | `GET /admin/ops/outbox/metrics` | 指标快照 | 返回 `new/sent/fail/failRetrySum` |
| Outbox | `POST /admin/ops/outbox/publish-once?limit={n}` | 批量发布 | 返回 `pulled/sent/failed` |
| Outbox | `GET /admin/ops/outbox/event/{eventId}` | 单事件定位 | 返回事件记录 |
| Outbox | `POST /admin/ops/outbox/event/{eventId}/trigger-now` | 单事件补偿 | 返回 `updatedRows > 0` |
| Task | `GET /admin/ops/tasks/ship-timeout` | ship-timeout 列表定位 | 分页结构返回 |
| Task | `GET /admin/ops/tasks/refund` | refund 列表定位 | 分页结构返回 |
| Task | `GET /admin/ops/tasks/ship-reminder` | ship-reminder 列表定位 | 分页结构返回 |
| Task | `POST /admin/ops/tasks/ship-timeout/run-once?limit={n}` | ship-timeout 批处理 | 返回 `success` |
| Task | `POST /admin/ops/tasks/refund/run-once?limit={n}` | refund 批处理 | 返回 `success` |
| Task | `POST /admin/ops/tasks/ship-reminder/run-once?limit={n}` | ship-reminder 批处理 | 返回 `success` |
| Task | `POST /admin/ops/tasks/ship-timeout/{taskId}/trigger-now` | ship-timeout 单条补偿 | `updatedRows > 0` |
| Task | `POST /admin/ops/tasks/refund/{taskId}/reset` | refund 单条补偿 | `updatedRows > 0` |
| Task | `POST /admin/ops/tasks/ship-reminder/{taskId}/trigger-now` | ship-reminder 单条补偿 | `updatedRows > 0` |

---

## 7. DoD 验收映射（本步骤）

| DoD | 验收动作 | 判定标准 |
|---|---|---|
| 每个阈值都有具体处置步骤 | 检查第 2 章每一行是否映射到 `FLOW-A/B/C` | 全部阈值均可映射到明确动作链路 |
| 处置步骤可通过运维接口实操复现 | 按第 6 章接口逐条执行 | 至少 1 条 Warning 与 1 条 Error 场景可复跑 |

---

## 8. 代码与配置锚点

1. `demo-service/src/main/java/com/demo/job/OutboxMonitorJob.java`
2. `demo-service/src/main/java/com/demo/job/OutboxPublishJob.java`
3. `demo-service/src/main/java/com/demo/controller/admin/AdminOutboxOpsController.java`
4. `demo-service/src/main/java/com/demo/controller/admin/AdminTaskOpsController.java`
5. `demo-service/src/main/resources/application.yml`
6. `demo-service/src/main/resources/mapper/MessageOutboxMapper.xml`
7. `demo-service/src/main/resources/mapper/OrderShipTimeoutTaskMapper.xml`
8. `demo-service/src/main/resources/mapper/OrderRefundTaskMapper.xml`
9. `demo-service/src/main/resources/mapper/OrderShipReminderTaskMapper.xml`

---

（文件结束）
