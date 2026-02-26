# Day18 P4-S2 排障手册 v1.0

- 日期：2026-02-25
- 对应阶段：`Step P4-S2：监控与告警规则收口`
- 目标：把“告警 -> 排障 -> 恢复”流程标准化，确保值班可独立处置核心失败场景。

---

## 1. 适用范围

1. Outbox 发布失败与重试放大。
2. 发货超时任务高重试。
3. 退款失败任务积压。
4. 发货提醒 RUNNING 卡死/失败积压。
5. MQ 消费失败与 PROCESSING 卡死。
6. 登录风控异常增长（`AUTO_RISK`）。

---

## 2. 标准处置流程（统一）

1. 告警确认：确认规则编号、时间窗口、当前值、阈值。
2. 影响评估：识别影响链路（订单、退款、消息、账号安全）。
3. 快速定位：先查运维 API，再查 SQL 明细。
4. 恢复执行：按对应恢复入口执行（trigger/reset/run-once）。
5. 回归验证：确认指标回落、状态机收敛、无新增异常。
6. 记录沉淀：回填执行记录（输入、动作、结果、结论）。

---

## 3. 场景化 Runbook

## 3.1 Outbox 告警（R1/R2/R3）

触发条件：
1. `failCount >= 5` 或 `failRetrySum >= 10`；
2. `NEW` 积压持续上涨且 `publish-once` 无发送成功。

定位步骤：
1. `GET /admin/ops/outbox/metrics`
2. `GET /admin/ops/outbox/event/{eventId}`

恢复步骤：
1. `POST /admin/ops/outbox/event/{eventId}/trigger-now`
2. `POST /admin/ops/outbox/publish-once?limit=50`

恢复判定：
1. 目标事件推进为 `SENT`，或 FAIL 重试计数有预期增长并进入下次调度。
2. `failCount/failRetrySum` 回落或稳定不再增长。

---

## 3.2 发货超时任务高重试（R4）

触发条件：
1. `order_ship_timeout_task(status='PENDING', retry_count>=5) >= 1`。

定位步骤：
1. `GET /admin/ops/tasks/ship-timeout?status=PENDING&page=1&pageSize=50`
2. 重点查看 `retry_count/next_retry_time/last_error`。

恢复步骤：
1. `POST /admin/ops/tasks/ship-timeout/{taskId}/trigger-now`
2. `POST /admin/ops/tasks/ship-timeout/run-once?limit=200`

恢复判定：
1. 任务推进到 `DONE/CANCELLED` 或按重试策略更新 `next_retry_time` 且原因清晰。
2. 无非法状态逆向（如终态回退到可执行态）。

---

## 3.3 退款失败积压（R5）

触发条件：
1. `order_refund_task.status='FAILED' >= 1`。

定位步骤：
1. `GET /admin/ops/tasks/refund?status=FAILED&page=1&pageSize=50`
2. 检查 `fail_reason/retry_count/next_retry_time`。

恢复步骤：
1. 依赖恢复后执行 `POST /admin/ops/tasks/refund/{taskId}/reset`
2. 触发 `POST /admin/ops/tasks/refund/run-once?limit=200`

恢复判定：
1. 成功推进 `SUCCESS`；失败则保留 `FAILED` 且原因更新。
2. `FAILED` 存量下降或不再新增。

---

## 3.4 发货提醒卡死（R6）

触发条件：
1. `RUNNING` 任务超过 5 分钟未收敛。

定位步骤：
1. SQL 查看 `status='RUNNING' and running_at <= now-5m`。
2. `GET /admin/ops/tasks/ship-reminder?status=FAILED&page=1&pageSize=50`

恢复步骤：
1. 等待自动回收（`running-timeout-minutes=5`）或直接手工触发：
2. `POST /admin/ops/tasks/ship-reminder/{taskId}/trigger-now`
3. `POST /admin/ops/tasks/ship-reminder/run-once?limit=200`

恢复判定：
1. 任务推进为 `SUCCESS` 或 `CANCELLED`，失败则进入可解释的 `FAILED`。
2. `stale running` 计数归零。

---

## 3.5 MQ 消费失败/卡死（R7/R8）

触发条件：
1. 30 分钟内 `mq_consume_log(status='FAIL') >= 1`；
2. `PROCESSING` 超过 5 分钟未更新。

定位步骤：
1. SQL 按 `consumer/event_id/status/updated_at` 查询；
2. 结合 Outbox `eventId` 与 DLQ（`order.dlq.queue`）定位消息。

恢复步骤：
1. 先排除依赖故障（MQ、DB、外部通知）；
2. 对应 Outbox 事件执行 `trigger-now + publish-once` 重放；
3. 必要时人工清理卡死记录并复跑消费。

恢复判定：
1. `FAIL` 不再增长；`PROCESSING` 卡死计数归零或显著下降。
2. 同一 `eventId` 不出现重复副作用（依赖 `mq_consume_log` 幂等）。

---

## 3.6 登录风控异常增长（R9）

触发条件：
1. 当日 `AUTO_RISK` 新增数超过阈值。

定位步骤：
1. SQL：`user_bans where source='AUTO_RISK' and date(create_time)=curdate()`。
2. 结合审计日志 `USER_LOGIN result=FAILED` 聚类账号/IP。

处置步骤：
1. 识别攻击源并临时限流；
2. 对误伤账号走人工解封；
3. 评估是否需要调整风控阈值策略。

恢复判定：
1. 新增风控冻结速率回落；
2. 无大规模误伤投诉。

---

## 4. 通用核验 SQL

```sql
SELECT status, COUNT(*), COALESCE(SUM(retry_count),0)
FROM message_outbox
GROUP BY status;

SELECT COUNT(*) FROM order_ship_timeout_task WHERE status='PENDING' AND retry_count>=5;
SELECT COUNT(*) FROM order_refund_task WHERE status='FAILED';
SELECT COUNT(*) FROM order_ship_reminder_task WHERE status='RUNNING' AND running_at<=DATE_SUB(NOW(), INTERVAL 5 MINUTE);
SELECT COUNT(*) FROM mq_consume_log WHERE status='FAIL' AND updated_at>=DATE_SUB(NOW(), INTERVAL 30 MINUTE);
SELECT COUNT(*) FROM mq_consume_log WHERE status='PROCESSING' AND updated_at<DATE_SUB(NOW(), INTERVAL 5 MINUTE);
SELECT COUNT(*) FROM user_bans WHERE source='AUTO_RISK' AND DATE(create_time)=CURDATE();
```

---

## 5. 完成判定

1. 告警对象可定位到具体记录（eventId/taskId/consumer）。
2. 恢复动作接口调用成功并有结果回执。
3. 指标回落或进入可解释重试状态。
4. 执行记录已回填。

---

（文件结束）
