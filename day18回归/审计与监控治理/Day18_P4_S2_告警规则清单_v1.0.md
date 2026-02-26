# Day18 P4-S2 告警规则清单 v1.0

- 日期：2026-02-25
- 对应阶段：`Step P4-S2：监控与告警规则收口`
- 目标：固化 Outbox、任务重试、消费失败与业务异常的告警阈值，形成统一检测口径。

---

## 1. 统一口径（P4-S2 冻结）

1. 告警统一分级：`P1(紧急)`、`P2(高)`、`P3(中)`。
2. 告警统一结构：`对象 + 指标 + 时间窗口 + 阈值 + 处置入口`。
3. 告警触发来源统一三类：
   - 应用日志告警（如 `OutboxMonitorJob` ERROR）；
   - 运维 API 指标（`/admin/ops/outbox/**`、`/admin/ops/tasks/**`）；
   - 数据库巡检 SQL（任务表、`mq_consume_log`、风控表）。
4. 告警恢复判定统一要求：
   - 关键指标回落到阈值以下；
   - 恢复动作接口返回成功；
   - 状态机无非法逆向迁移。

---

## 2. 规则矩阵（核心失败场景）

| 编号 | 监控对象 | 指标定义 | 阈值（WARN/CRITICAL） | 窗口 | 检测方式 | 告警级别 |
|---|---|---|---|---|---|---|
| R1 | Outbox 失败堆积 | `message_outbox.status='FAIL'` 数量 | `>=5 / >=10` | 实时 | `outbox.monitor.fail-threshold` + `/admin/ops/outbox/metrics` | P2/P1 |
| R2 | Outbox 失败重试放大 | `SUM(retry_count)` for FAIL | `>=10 / >=30` | 实时 | `outbox.monitor.fail-retry-threshold` + `/admin/ops/outbox/metrics` | P2/P1 |
| R3 | Outbox 待发积压 | `status='NEW'` 数量 | `>=50 / >=200` | 5 分钟 | `/admin/ops/outbox/metrics` 或 SQL | P3/P2 |
| R4 | 发货超时任务异常重试 | `order_ship_timeout_task(status=PENDING,retry_count>=5)` | `>=1 / >=3` | 5 分钟 | SQL 巡检 + `/admin/ops/tasks/ship-timeout` | P2/P1 |
| R5 | 退款任务失败积压 | `order_refund_task.status='FAILED'` | `>=1 / >=5` | 5 分钟 | `/admin/ops/tasks/refund` + SQL | P2/P1 |
| R6 | 发货提醒卡死 | `order_ship_reminder_task(status=RUNNING,running_at<=now-5m)` | `>=1 / >=5` | 5 分钟 | SQL 巡检 | P2/P1 |
| R7 | MQ 消费失败 | `mq_consume_log(status='FAIL')` | `30m 内 >=1 / >=5` | 30 分钟 | SQL（按 `updated_at`） | P2/P1 |
| R8 | MQ 消费处理卡死 | `mq_consume_log(status='PROCESSING',updated_at<now-5m)` | `>=1 / >=3` | 5 分钟 | SQL 巡检 | P2/P1 |
| R9 | 登录风控异常增长 | `user_bans(source='AUTO_RISK', 当日新增)` | `>=5 / >=20` | 当日累计 | SQL + 管理巡检 | P3/P2 |

说明：
1. R1/R2 为代码内已落地阈值（`OutboxMonitorJob`），其余为 P4-S2 收口后冻结的运维阈值。
2. R4/R5/R6/R7/R8 由“阈值 + 运行手册动作”组成闭环，避免仅统计无处置。

---

## 3. 业务异常统计口径（P4-S2 新增）

1. 交易链路异常：
   - Outbox 失败与重试总量（R1/R2）；
   - 退款失败任务存量（R5）；
   - 发货超时任务高重试样本（R4）。
2. 消费链路异常：
   - 消费失败次数（R7）；
   - 长时间 PROCESSING 样本（R8）。
3. 账号安全异常：
   - 自动风控冻结当日新增（R9）。
4. 审计链路异常（与 P4-S1 联动）：
   - 通过 `AUDIT result=FAILED` 统计动作失败分布（`USER_LOGIN/ORDER_PAY/PAYMENT_CALLBACK/USER_BAN`）。

---

## 4. 标准检测 SQL（冻结）

```sql
-- Outbox 状态与重试总量
SELECT status, COUNT(*) AS cnt, COALESCE(SUM(retry_count),0) AS retry_sum
FROM message_outbox
GROUP BY status;

-- 发货超时任务：高重试
SELECT COUNT(*) AS high_retry_cnt
FROM order_ship_timeout_task
WHERE status='PENDING' AND retry_count>=5;

-- 退款失败任务
SELECT COUNT(*) AS refund_failed_cnt
FROM order_refund_task
WHERE status='FAILED';

-- 发货提醒卡死 RUNNING（阈值口径 5 分钟）
SELECT COUNT(*) AS stale_running_cnt
FROM order_ship_reminder_task
WHERE status='RUNNING'
  AND running_at <= DATE_SUB(NOW(), INTERVAL 5 MINUTE);

-- MQ 消费失败（30 分钟窗口）
SELECT COUNT(*) AS mq_fail_30m
FROM mq_consume_log
WHERE status='FAIL'
  AND updated_at >= DATE_SUB(NOW(), INTERVAL 30 MINUTE);

-- MQ PROCESSING 卡死（5 分钟）
SELECT COUNT(*) AS mq_processing_stuck
FROM mq_consume_log
WHERE status='PROCESSING'
  AND updated_at < DATE_SUB(NOW(), INTERVAL 5 MINUTE);

-- 登录风控当日新增
SELECT COUNT(*) AS auto_risk_today
FROM user_bans
WHERE source='AUTO_RISK'
  AND DATE(create_time)=CURDATE();
```

---

## 5. 告警到处置映射（摘要）

| 告警规则 | 首选处置入口 | 标准恢复动作 |
|---|---|---|
| R1/R2/R3 Outbox | `/admin/ops/outbox/metrics` | `event/{eventId}/trigger-now` + `publish-once` |
| R4 发货超时高重试 | `/admin/ops/tasks/ship-timeout` | `ship-timeout/{taskId}/trigger-now` + `run-once` |
| R5 退款失败积压 | `/admin/ops/tasks/refund` | `refund/{taskId}/reset` + `run-once` |
| R6 发货提醒卡死 | `/admin/ops/tasks/ship-reminder` | 自动回收 + `trigger-now` + `run-once` |
| R7/R8 MQ 消费失败/卡死 | `mq_consume_log` + MQ 控制台 | 查 eventId 重放链路 + 清理卡死记录 |
| R9 自动风控异常增长 | `user_bans` + 审计日志 | 核查来源 IP/账号聚类，必要时临时限流 |

---

## 6. 代码证据索引

1. `demo-service/src/main/java/com/demo/job/OutboxMonitorJob.java`
2. `demo-service/src/main/java/com/demo/job/OutboxPublishJob.java`
3. `demo-service/src/main/java/com/demo/controller/admin/AdminOutboxOpsController.java`
4. `demo-service/src/main/java/com/demo/controller/admin/AdminTaskOpsController.java`
5. `demo-service/src/main/resources/mapper/MessageOutboxMapper.xml`
6. `demo-service/src/main/resources/mapper/OrderShipTimeoutTaskMapper.xml`
7. `demo-service/src/main/resources/mapper/OrderRefundTaskMapper.xml`
8. `demo-service/src/main/resources/mapper/OrderShipReminderTaskMapper.xml`
9. `demo-service/src/main/resources/mapper/MqConsumeLogMapper.xml`
10. `demo-service/src/main/java/com/demo/mq/consumer/OrderPaidConsumer.java`
11. `demo-service/src/main/java/com/demo/mq/consumer/OrderStatusChangedConsumer.java`
12. `demo-service/src/main/java/com/demo/mq/consumer/OrderTimeoutConsumer.java`
13. `demo-service/src/main/resources/application.yml`

---

## 7. DoD 对齐（P4-S2 当前阶段）

- [x] 核心失败场景均已定义阈值与告警口径。  
- [x] 告警规则已映射到具体排障与恢复入口。  
- [x] 已具备运行态验证与执行记录回填路径。  

---

（文件结束）
