# Day18 P6-S1 慢 SQL 回归执行记录 v1.0

- 日期：2026-02-26
- 关联复现文档：`day18回归/执行复现步骤/Day18_P6_S1_索引与慢SQL回归_执行复现_v1.0.md`
- 当前状态：已执行（索引抽检 + EXPLAIN + EXPLAIN ANALYZE 已完成）。

---

## 1. 环境信息

1. 数据库：`secondhand2`
2. 执行人：`Codex`
3. 执行时间：`2026-02-26 00:55:31`
4. 原始证据：`day18回归/执行记录/Day18_P6_S1_EXPLAIN回归证据_2026-02-26_00-55-31.json`

---

## 2. 回归摘要

1. Day17 基线索引在当前库均可见，未发现缺失。
2. Outbox 与三类任务表关键 SQL 均走索引路径（`range/ref/index`），未见基础表明显全表扫描风险。
3. Outbox 拉取与提醒任务批量抢占仍存在 filesort，但属于已知可控项（有索引、有限制条件与 LIMIT）。

---

## 3. 关键执行结果

## 3.1 表规模

| 表 | 行数 |
|---|---:|
| `message_outbox` | 44 |
| `order_ship_timeout_task` | 13 |
| `order_refund_task` | 4 |
| `order_ship_reminder_task` | 18 |

## 3.2 EXPLAIN 结果回填

| 查询 | 访问类型 | 索引 | 结论 |
|---|---|---|---|
| Outbox `listPending` | 内层 `range`，外层派生表 `ALL` | `idx_status_time` | 基础表命中索引；外层 `ALL` 仅针对派生小结果集 |
| Outbox `count FAIL` | `ref` | `idx_status_time` | 覆盖索引计数 |
| 超时任务 `listDuePending` | `range` | `idx_ship_timeout_status_deadline` | 命中状态+截止时间索引 |
| 退款任务 `listRunnable` | `index`（EXPLAIN） | `PRIMARY`（EXPLAIN） / `idx_refund_next_retry`（ANALYZE） | 小表可控，具备索引范围路径 |
| 提醒任务 `listStaleRunning` | `range` | `idx_status_running_at` | 命中状态+运行时间索引 |
| 提醒任务 `markRunningBatch` | `range` | `idx_status_remind_time` | 命中索引，存在可控 filesort |

## 3.3 EXPLAIN ANALYZE 抽样

1. `message_outbox(status='FAIL')`：显示 `Covering index lookup ... idx_status_time`。
2. `order_ship_timeout_task listDuePending`：显示 `Index range scan ... idx_ship_timeout_status_deadline`。
3. `order_refund_task listRunnable`：显示 `Covering index range scan ... idx_refund_next_retry`（当前样本结果行为 0）。

---

## 4. 对比结论（Day17 -> Day18）

1. Day17 索引治理成果保持稳定，未发生“索引漂移/丢失”。
2. Day17 已标注的 Outbox filesort 问题在 Day18 仍为已知项，但仍处于“有限排序集合”口径。
3. 本次回归未发现关键高频链路出现新的全表扫描退化。

---

## 5. DoD 勾选

- [x] 关键链路查询无明显全表扫描风险。  
- [x] 优化结论有数据支撑。  

---

（文件结束）
