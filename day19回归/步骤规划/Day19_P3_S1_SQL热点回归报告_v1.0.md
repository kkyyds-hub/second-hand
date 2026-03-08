# Day19 P3-S1 SQL 热点回归报告 v1.0

- 日期：`2026-03-06`
- 对应范围：`Day19_Scope_Freeze_v1.0.md -> Phase 3 / Step P3-S1`
- 当前状态：`已执行完成（2026-03-06 10:41:39）`
- 动态证据：`day19回归/执行记录/Day19_P3_S1_EXPLAIN证据_2026-03-06_10-41-39.json`
- 执行脚本：`day19回归/执行复现步骤/Day19_P3_S1_SQL热点回归_执行复现_v1.0.ps1`
- 参考库快照：`c:\Users\kk\Desktop\_localhost__3_-2026_03_06_10_33_49-dump.sql`

---

## 1. 目标与范围

本步骤目标：继续压缩 DB 热点查询耗时，避免“缓存掩盖 SQL 问题”。

本次范围：

1. Outbox 管理与发布链路 SQL（`listPending`、`metrics`、`event` 查询/触发）。
2. 任务管理与调度链路 SQL（发货超时 / 退款 / 发货提醒）。
3. 订单管理分页 SQL（管理端订单列表排序与分页）。
4. 分页与行数控制复核（控制器页大小、分页插件上限、分页查询执行计划）。

代码锚点：

1. `demo-service/src/main/java/com/demo/controller/admin/AdminTaskOpsController.java`
2. `demo-service/src/main/java/com/demo/controller/admin/AdminOutboxOpsController.java`
3. `demo-service/src/main/java/com/demo/config/MybatisPlusConfig.java`

---

## 2. 执行方法与口径

执行方法：

1. 采集索引现状：`SHOW INDEX`（Outbox、任务表、orders）。
2. 采集执行计划：`EXPLAIN`（高频 SQL 全覆盖）。
3. 采集运行态样本：`EXPLAIN ANALYZE`（关键链路抽样）。
4. 结合表规模与分页参数，评估是否存在可规避的全表扫描风险。

判定口径：

1. `高风险`：基础业务表出现明显 `type=ALL` 且可通过索引/改写规避。
2. `中风险`：未全扫但存在可预见退化点（filesort、低选择性扫描、排序与过滤索引不匹配）。
3. `低风险`：索引命中稳定，无明显热点退化迹象。

---

## 3. 本次样本与规模

采样时刻：`2026-03-06 10:41:39`

表规模（`COUNT(*)`）：

| 表 | 行数 |
|---|---:|
| `message_outbox` | 81 |
| `order_ship_timeout_task` | 27 |
| `order_refund_task` | 15 |
| `order_ship_reminder_task` | 39 |
| `orders` | 66 |

SQL 样本参数：

1. `eventId=8f99c54b-1d9b-4062-b4a6-fdd6e2f68196`
2. `orderId(timeout)=900068`
3. `orderId(refund)=900056`
4. `orderId(reminder)=900068`
5. `orderNo=2026030417571315383`

---

## 4. EXPLAIN 覆盖清单（高频 SQL）

本次共回归 `21` 条高频 SQL，全部已落证据 JSON：

1. Outbox：`5` 条（拉取、计数、重试统计、event 查询、trigger-now update）。
2. 发货超时任务：`4` 条（到期拉取、管理分页按 status/orderId、count）。
3. 退款任务：`4` 条（可执行拉取、管理分页按 status/orderId、count）。
4. 发货提醒任务：`4` 条（批量抢占 update、卡死扫描、管理分页、count）。
5. 订单管理：`4` 条（按 createTime/payTime 列表、orderNo 查询、pay 条件更新）。

对应证据：`day19回归/执行记录/Day19_P3_S1_EXPLAIN证据_2026-03-06_10-41-39.json`

---

## 5. 核心结果

### 5.1 全表扫描风险结论

1. 未发现基础业务表的明显 `type=ALL` 全表扫描热点。
2. `outbox_listPending` 中 `PRIMARY <derived2> type=ALL` 属于派生结果集扫描，不是基础表全扫；内层两支路均命中 `message_outbox` 状态索引范围扫描。

### 5.2 索引命中结论（摘要）

1. Outbox 计数/重试统计：命中 `idx_status_time`。
2. 发货超时到期拉取：命中 `idx_ship_timeout_status_deadline`（`range`）。
3. 退款可执行拉取：命中 `idx_refund_status_time`（`range`，带 filesort）。
4. 发货提醒卡死扫描：命中 `idx_status_running_at`（`range`）。
5. 管理端订单 `status + create_time`：命中 `idx_orders_status_create_time`（逆序扫描）。

---

## 6. 高风险 SQL -> 处置建议

| 风险级别 | SQL | 观察到的问题 | 处置建议 |
|---|---|---|---|
| 中 | `outbox_listPending` | 内层与外层均出现 `Using filesort`（受 `2*limit` 上界约束） | 保持当前“分支限流 + 外层截断”策略；若数据量继续增长，可评估按状态分片拉取后在应用层归并 |
| 中 | `shipTimeout_listForAdmin_byStatus` | `status` 过滤后 `ORDER BY id DESC` 触发 filesort | 增补索引 `idx_ship_timeout_status_id(status,id)`，降低管理分页排序成本 |
| 中 | `refund_listRunnable` | 命中状态索引但 `ORDER BY id ASC` 出现 filesort | 评估索引 `idx_refund_status_nextretry_id(status,next_retry_time,id)`，统一过滤与排序路径 |
| 中 | `refund_listForAdmin_byStatus` | 走 `PRIMARY` 反向扫描 + `Using where`，对 status 选择性依赖高 | 增补 `idx_refund_status_id(status,id)`，避免状态过滤下主键全索引扫描放大 |
| 中 | `reminder_markRunningBatch_update` | `Using filesort`（`ORDER BY remind_time,id LIMIT`） | 若任务量明显上升，考虑补 `idx_status_remind_id(status,remind_time,id)` |
| 中 | `reminder_listForAdmin_byStatus` | `ORDER BY id DESC` 下 filesort | 增补 `idx_reminder_status_id(status,id)` |
| 中 | `adminOrders_list_byPayTime` | `status + pay_time` 排序出现 filesort | 增补 `idx_orders_status_pay_time(status,pay_time)`，和 create_time 路径并行 |

说明：

1. 当前表规模较小，上述风险多数为“增长型风险”，非立即阻塞。
2. 本次结论重点是“先证据化，再按风险分批治理”，避免过度建索引。

---

## 7. 分页与行数控制复核

1. `AdminTaskOpsController`：`pageSize` 上限为 `100`，并有默认值与兜底。
2. `AdminOrderController`：`pageSize` 上限为 `100`，排序字段白名单已收口。
3. `MybatisPlusConfig`：`PaginationInnerInterceptor.setMaxLimit(100)` 已生效；`overflow=false`。
4. 分页混用守卫（`PaginationMixGuardInterceptor`）存在，可阻断 PageHelper 与 MP 同链路混用。

结论：分页“行数控制”口径符合预期。

---

## 8. DoD 验收

1. 高频 SQL 均有 EXPLAIN 证据：`通过`
2. 不存在明显可规避的全表扫描热点：`通过`

---

## 9. 产物清单

1. `day19回归/步骤规划/Day19_P3_S1_SQL热点回归报告_v1.0.md`
2. `day19回归/执行记录/Day19_P3_S1_EXPLAIN证据_2026-03-06_10-41-39.json`
3. `day19回归/执行复现步骤/Day19_P3_S1_SQL热点回归_执行复现_v1.0.ps1`

---

（文件结束）
