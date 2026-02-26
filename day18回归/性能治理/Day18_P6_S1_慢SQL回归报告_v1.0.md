# Day18 P6-S1 慢 SQL 回归报告 v1.0

- 日期：2026-02-26
- 对应阶段：`Step P6-S1：索引与慢 SQL 回归`
- 目标：基于 Day17 索引治理基线，验证 Day18 当前关键高频 SQL 仍可控。

---

## 1. 回归基线

## 1.1 Day17 基线文档（对照来源）

1. `day17回归/慢SQL与索引治理/Day17_P3_S3_SQL优化清单_v1.0.md`
2. `day17回归/慢SQL与索引治理/Day17_P3_S3_EXPLAIN结论_v1.0.md`
3. `day17回归/慢SQL与索引治理/Day17_P3_S4_Outbox_filesort收口_v1.0.md`

## 1.2 本次回归范围（P6-S1）

1. Outbox 高频链路：`listPending`、失败计数。
2. 任务表高频链路：发货超时任务、退款任务、发货提醒任务。
3. 关键判定口径：
   - 是否出现基础表 `type=ALL` 全表扫描风险；
   - 是否命中 Day17 已冻结索引；
   - filesort 是否为“已知可控”而非无界放大。

---

## 2. 当前数据规模与索引存在性

数据源：`secondhand2`（采样时刻：2026-02-26 00:55:31）

| 表 | 当前行数 |
|---|---:|
| `message_outbox` | 44 |
| `order_ship_timeout_task` | 13 |
| `order_refund_task` | 4 |
| `order_ship_reminder_task` | 18 |

关键索引抽检结论：

1. `message_outbox`：存在 `idx_status_time`、`idx_outbox_status_retry_id`。
2. `order_ship_timeout_task`：存在 `idx_ship_timeout_status_deadline`、`idx_ship_timeout_next_retry`。
3. `order_refund_task`：存在 `idx_refund_status_time`、`idx_refund_next_retry`。
4. `order_ship_reminder_task`：存在 `idx_status_remind_time`、`idx_status_running_at`。

---

## 3. EXPLAIN 回归结果（关键 SQL）

| SQL | 访问类型 | 命中索引 | 关键 Extra | 结论 |
|---|---|---|---|---|
| Outbox 拉取 `listPending`（UNION 双分支） | `DERIVED: range`（内层）；外层派生表 `ALL` | `idx_status_time` | `Using where; Using index; Using filesort` | 基础表未全扫；filesort 仍存在但排序集合受 `2*limit` 上界约束 |
| Outbox 失败计数 | `ref` | `idx_status_time` | `Using index` | 计数链路稳定命中索引 |
| 发货超时任务拉取 | `range` | `idx_ship_timeout_status_deadline` | `Using index condition; Using where` | 命中状态+截止时间索引，无全扫风险 |
| 退款任务拉取 | `index`（EXPLAIN）；`idx_refund_next_retry` 覆盖扫描（ANALYZE） | `PRIMARY` / `idx_refund_next_retry` | `Using where` + 小集合排序 | 当前小表下可控；核心过滤条件具备索引路径 |
| 发货提醒卡死扫描 | `range` | `idx_status_running_at` | `Using index condition` | 命中状态+运行时间索引，无全扫风险 |
| 发货提醒批量抢占更新 | `range` | `idx_status_remind_time` | `Using where; Using filesort` | 已命中索引，filesort 为已知排序成本点 |

---

## 4. Day17 -> Day18 对比结论

## 4.1 保持稳定项

1. Day17 治理后新增/强化的任务与 Outbox 索引，在 Day18 回归中仍全部存在并被使用。
2. Outbox 与任务高频链路均未出现基础表 `type=ALL` 的明显全表扫描风险。
3. 发货超时、发货提醒卡死扫描均保持 `range` 访问类型，符合高频任务查询预期。

## 4.2 已知残留项（可控）

1. Outbox `listPending` 仍可见 filesort（与 Day17 P3-S4 结论一致），但因双分支限流后排序集合上界受控。
2. 发货提醒 `markRunningBatch` 仍可见 filesort，当前表规模较小且命中 `idx_status_remind_time`，短期可接受。

---

## 5. 优化前后对比（数据支撑）

1. Day17 已完成“Outbox filesort 无界放大 -> 有界排序集合”收口（P3-S4）。
2. Day18 本次回归确认该 SQL 形态仍在（UNION 双分支 + 外层 LIMIT），并继续使用 `idx_status_time`。
3. `EXPLAIN ANALYZE` 抽样显示：
   - Outbox 失败计数为覆盖索引查找；
   - 发货超时任务为 `idx_ship_timeout_status_deadline` 索引范围扫描；
   - 退款任务具备 `idx_refund_next_retry` 索引范围路径。

---

## 6. DoD 对齐结论（P6-S1）

- [x] 关键链路查询无明显全表扫描风险。  
- [x] 优化结论有数据支撑（EXPLAIN + EXPLAIN ANALYZE + 索引抽检 + 表规模统计）。  

---

## 7. 证据索引

1. `day18回归/执行记录/Day18_P6_S1_EXPLAIN回归证据_2026-02-26_00-55-31.json`
2. `day17回归/慢SQL与索引治理/Day17_P3_S3_SQL优化清单_v1.0.md`
3. `day17回归/慢SQL与索引治理/Day17_P3_S3_EXPLAIN结论_v1.0.md`
4. `day17回归/慢SQL与索引治理/Day17_P3_S4_Outbox_filesort收口_v1.0.md`
5. `demo-service/src/main/resources/mapper/MessageOutboxMapper.xml`
6. `demo-service/src/main/resources/mapper/OrderShipTimeoutTaskMapper.xml`
7. `demo-service/src/main/resources/mapper/OrderRefundTaskMapper.xml`
8. `demo-service/src/main/resources/mapper/OrderShipReminderTaskMapper.xml`

---

（文件结束）
