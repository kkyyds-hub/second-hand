# Day18 P6-S1 索引与慢 SQL 回归 执行复现 v1.0

- 日期：2026-02-26
- 目标：复现 Day17 索引基线在 Day18 当前库中的命中与执行计划状态。

---

## 1. 前置条件

1. MySQL 可访问（示例库：`secondhand2`）。
2. 已具备 SQL 执行权限（`SHOW INDEX`、`EXPLAIN`、`EXPLAIN ANALYZE`）。
3. 代码版本与 Mapper 文件已同步到当前工作区。

---

## 2. 步骤 A：索引存在性回归

执行：

```sql
SHOW INDEX FROM message_outbox;
SHOW INDEX FROM order_ship_timeout_task;
SHOW INDEX FROM order_refund_task;
SHOW INDEX FROM order_ship_reminder_task;
```

预期：
1. `message_outbox` 包含 `idx_status_time`、`idx_outbox_status_retry_id`。
2. `order_ship_timeout_task` 包含 `idx_ship_timeout_status_deadline`、`idx_ship_timeout_next_retry`。
3. `order_refund_task` 包含 `idx_refund_status_time`、`idx_refund_next_retry`。
4. `order_ship_reminder_task` 包含 `idx_status_remind_time`、`idx_status_running_at`。

---

## 3. 步骤 B：高频 SQL EXPLAIN 回归

## 3.1 Outbox 拉取

```sql
EXPLAIN
SELECT t.id,t.status,t.next_retry_time
FROM (
  (SELECT id,status,next_retry_time
   FROM message_outbox
   WHERE status='NEW'
     AND (next_retry_time IS NULL OR next_retry_time <= NOW())
   ORDER BY id ASC
   LIMIT 200)
  UNION ALL
  (SELECT id,status,next_retry_time
   FROM message_outbox
   WHERE status='FAIL'
     AND (next_retry_time IS NULL OR next_retry_time <= NOW())
   ORDER BY id ASC
   LIMIT 200)
) t
ORDER BY t.id ASC
LIMIT 200;
```

## 3.2 发货超时任务拉取

```sql
EXPLAIN
SELECT id,order_id,deadline_time,status,retry_count,next_retry_time
FROM order_ship_timeout_task
WHERE status='PENDING'
  AND deadline_time <= NOW()
  AND (next_retry_time IS NULL OR next_retry_time <= NOW())
ORDER BY deadline_time ASC, id ASC
LIMIT 200;
```

## 3.3 退款任务拉取

```sql
EXPLAIN
SELECT id,order_id,refund_type,status,retry_count,next_retry_time
FROM order_refund_task
WHERE status IN ('PENDING','FAILED')
  AND (next_retry_time IS NULL OR next_retry_time <= NOW())
ORDER BY id ASC
LIMIT 200;
```

## 3.4 发货提醒卡死扫描

```sql
EXPLAIN
SELECT id,order_id,status,running_at,remind_time,retry_count
FROM order_ship_reminder_task
WHERE status='RUNNING'
  AND running_at <= DATE_SUB(NOW(), INTERVAL 5 MINUTE)
ORDER BY running_at ASC, id ASC
LIMIT 200;
```

预期：
1. 关键基础表查询应为 `range/ref/index` 等索引访问类型。
2. 不应出现基础表 `type=ALL` 的明显全表扫描风险。

---

## 4. 步骤 C：EXPLAIN ANALYZE 抽样

```sql
EXPLAIN ANALYZE
SELECT COUNT(*)
FROM message_outbox
WHERE status='FAIL';

EXPLAIN ANALYZE
SELECT id
FROM order_ship_timeout_task
WHERE status='PENDING'
  AND deadline_time <= NOW()
  AND (next_retry_time IS NULL OR next_retry_time <= NOW())
ORDER BY deadline_time ASC, id ASC
LIMIT 200;
```

预期：
1. 可看到覆盖索引查找或索引范围扫描路径。
2. 与 EXPLAIN 结果不存在明显冲突。

---

## 5. DoD 勾选

- [ ] 已完成 Day17 索引存在性回归。  
- [ ] 已完成 Outbox/任务表高频 SQL 的 EXPLAIN 回归。  
- [ ] 已形成“无明显全表扫描风险 + 有数据支撑”的执行记录。  

---

（文件结束）
