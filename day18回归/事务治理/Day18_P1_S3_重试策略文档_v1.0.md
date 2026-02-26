# Day18 P1-S3 重试策略文档 v1.0

- 日期：2026-02-25
- 对应阶段：`Step P1-S3：重试机制与失败处理标准化`
- 目标：统一任务链路与 Outbox 的重试口径，确保“重试可控、失败可追踪、人工可介入”。

---

## 1. 统一口径（P1-S3 冻结）

1. 重试控制字段统一：`retry_count + next_retry_time + error_reason(last_error/fail_reason)`。
2. 批处理统一：`batch-size` 表示单轮处理上限，`fixed-delay-ms` 表示扫描间隔。
3. 固定延迟重试统一：`retry-delay-seconds`（Outbox/任务同语义，不同默认值）。
4. 人工介入统一：优先使用 `/admin/ops/tasks/**` 与 `/admin/ops/outbox/**` 运维接口。
5. 状态机优先：所有重试、取消、成功推进均由 CAS/状态条件更新约束。

---

## 2. 参数命名与阈值基线（当前代码事实）

| 维度 | 配置键 | 当前值 | 语义 |
|---|---|---:|---|
| 发货超时任务批量 | `order.ship-timeout.batch-size` | 200 | 单轮拉取上限 |
| 发货超时任务扫描间隔 | `order.ship-timeout.fixed-delay-ms` | 60000 | Job 调度间隔 |
| 发货超时任务重试延迟 | `order.ship-timeout.retry-delay-seconds` | 120 | `markRetry` 下次执行延迟 |
| 退款任务批量 | `order.refund.batch-size` | 200 | 单轮拉取上限 |
| 退款任务扫描间隔 | `order.refund.fixed-delay-ms` | 60000 | Job 调度间隔 |
| 退款任务重试延迟 | `order.refund.retry-delay-seconds` | 120 | `markFail` 下次执行延迟 |
| 发货提醒批量 | `order.ship-reminder.batch-size` | 200 | 单轮抢占上限 |
| 发货提醒扫描间隔 | `order.ship-reminder.fixed-delay-ms` | 60000 | Job 调度间隔 |
| 发货提醒 RUNNING 回收阈值 | `order.ship-reminder.running-timeout-minutes` | 5 | 卡死任务回收阈值 |
| Outbox 发布批量 | `outbox.publish.batch-size` | 50 | 单轮发布上限 |
| Outbox 重试延迟 | `outbox.publish.retry-delay-seconds` | 30 | `markFailBatch` 下次重试延迟 |
| Outbox 失败条数告警阈值 | `outbox.monitor.fail-threshold` | 5 | FAIL 数量告警 |
| Outbox 失败重试总次数阈值 | `outbox.monitor.fail-retry-threshold` | 10 | FAIL 重试累计告警 |

补充说明：
1. 发货提醒链路采用分档退避（2m/5m/15m/30m），当前由代码策略计算，不走配置项。
2. 当前未设置统一 `max-retry-count`，通过状态机分流 + 运维介入控制重试上界。

---

## 3. 重试状态机总览

| 组件 | 可执行筛选 | 状态迁移 | 重试动作 | 人工入口 |
|---|---|---|---|---|
| `order_ship_timeout_task` | `PENDING` 且到期（`deadline_time`/`next_retry_time`） | `PENDING -> DONE/CANCELLED`；失败保持 `PENDING` | `retry_count+1` + `next_retry_time` + `last_error` | `POST /admin/ops/tasks/ship-timeout/{taskId}/trigger-now` |
| `order_refund_task` | `PENDING/FAILED` 且 `next_retry_time<=now` | `PENDING/FAILED -> SUCCESS`；失败到 `FAILED` | `retry_count+1` + `next_retry_time` + `fail_reason` | `POST /admin/ops/tasks/refund/{taskId}/reset` |
| `order_ship_reminder_task` | `PENDING/FAILED` 且 `remind_time<=now` | `PENDING/FAILED -> RUNNING -> SUCCESS/FAILED/CANCELLED` | 失败转 `FAILED`，按退避更新 `remind_time` | `POST /admin/ops/tasks/ship-reminder/{taskId}/trigger-now` |
| `message_outbox` | `NEW/FAIL` 且 `next_retry_time<=now` | `NEW/FAIL -> SENT/FAIL` | 失败 `retry_count+1` + `next_retry_time` | `POST /admin/ops/outbox/event/{eventId}/trigger-now` + `POST /admin/ops/outbox/publish-once` |

---

## 4. 可重试失败 vs 不可重试失败判定

## 4.1 可重试失败（Retryable）

判定标准：
1. 外部依赖临时异常（MQ/通知/网络波动）。
2. 并发漂移导致本次未命中，但业务状态仍可推进。
3. 任务数据仍处于可执行状态（非终态）。

| 链路 | 典型场景（代码） | 当前处理 |
|---|---|---|
| 发货超时任务 | `close_rows_0_status_*` / `unexpected_status` / `invalid_order_status` | `markRetry`（仍为 `PENDING`） |
| 退款任务 | `mock_refund_error:*` / `order_not_found` | `markFail`（转 `FAILED`，待重试） |
| 发货提醒任务 | `send_error:*` / `invalid_order_status` / `running_timeout_recycle` | `markFail`（转 `FAILED`，退避重试） |
| Outbox 发布 | MQ 发送异常、序列化异常 | `markFailBatch`（转 `FAIL`，延迟重试） |

## 4.2 不可重试失败（Non-Retryable / 收敛）

判定标准：
1. 业务进入终态，继续重试只会产生噪声。
2. 幂等命中已达目标状态，无需重复执行。
3. 管理端判断为数据问题且需人工业务处置，不应继续自动重试。

| 链路 | 典型场景（代码） | 当前处理 |
|---|---|---|
| 发货超时任务 | `order_terminal:*` / `order_not_found` | `markCancelled` |
| 发货提醒任务 | `order_terminal_or_not_paid:*` / `order_not_found` | `markCancelled` |
| 退款任务 | `latestStatus=SUCCESS` 并发回查命中 | 幂等命中，直接返回 |
| Outbox | `status=SENT` | 终态，不再重试 |

---

## 5. 失败追踪字段与日志口径

1. 任务追踪最小字段：`taskId/orderId/status/retry_count/next_retry_time/last_error(or fail_reason)/update_time`。
2. Outbox 追踪最小字段：`eventId/status/retry_count/next_retry_time/updated_at`。
3. 运维操作追踪最小字段：`operator(admin)/action/target(updatedRows)/processedAt`。

建议日志模板（P1-S3 起新增链路按此执行）：
1. `重试推进：component={}, key={}, retryCount={}, nextRetryTime={}, reason={}`
2. `不可重试收敛：component={}, key={}, scene={}, finalStatus={}`
3. `人工恢复执行：component={}, action={}, key={}, updatedRows={}`

---

## 6. 当前差距与约束

1. 当前任务链路没有统一 `max-retry-count`，需要依赖运维手工收敛异常长尾任务。
2. `order_refund_task` 的 `order_not_found` 当前仍走可重试分支（`FAILED`），属于保守策略，需结合业务场景人工判定是否长期保留。
3. 发货提醒退避策略在代码中固定分档，若后续要灰度调参，建议增加配置化参数。

---

## 7. 代码证据索引

1. `demo-service/src/main/resources/application.yml`
2. `demo-service/src/main/java/com/demo/job/OrderShipTimeoutTaskJob.java`
3. `demo-service/src/main/java/com/demo/job/OrderRefundTaskJob.java`
4. `demo-service/src/main/java/com/demo/job/OutboxPublishJob.java`
5. `demo-service/src/main/java/com/demo/job/OutboxMonitorJob.java`
6. `demo-service/src/main/java/com/demo/service/serviceimpl/OrderShipTimeoutTaskProcessor.java`
7. `demo-service/src/main/java/com/demo/service/serviceimpl/OrderRefundTaskProcessor.java`
8. `demo-service/src/main/java/com/demo/service/serviceimpl/OrderShipReminderTaskProcessor.java`
9. `demo-service/src/main/resources/mapper/OrderShipTimeoutTaskMapper.xml`
10. `demo-service/src/main/resources/mapper/OrderRefundTaskMapper.xml`
11. `demo-service/src/main/resources/mapper/OrderShipReminderTaskMapper.xml`
12. `demo-service/src/main/resources/mapper/MessageOutboxMapper.xml`
13. `demo-service/src/main/java/com/demo/controller/admin/AdminTaskOpsController.java`
14. `demo-service/src/main/java/com/demo/controller/admin/AdminOutboxOpsController.java`

---

## 8. DoD 对齐（P1-S3 当前阶段）

- [x] 已形成任务与 Outbox 重试参数命名与阈值基线。  
- [x] 已形成“可重试失败 vs 不可重试失败”统一判定口径。  
- [x] 已形成状态机与人工介入入口映射。  
- [x] 运维失败恢复演练已完成并回填执行记录。  

---

（文件结束）
