# Day18 P4-S2 告警排障恢复 执行复现 v1.0

- 日期：2026-02-25
- 目标：验证告警规则可检测、运维入口可执行、恢复流程可落地。

---

## 1. 前置条件

1. 服务已启动且可访问 `http://localhost:8080`。
2. 测试库可访问（`secondhand2`）。
3. 管理员账号可登录，具备 `/admin/ops/outbox/**`、`/admin/ops/tasks/**` 权限。

---

## 2. 场景 A：Outbox 监控与人工发布

1. 查询当前指标：
   - `GET /admin/ops/outbox/metrics`
2. 人工执行一轮发布：
   - `POST /admin/ops/outbox/publish-once?limit=20`
3. 再次查询指标：
   - `GET /admin/ops/outbox/metrics`

预期：
1. 指标接口稳定返回 `new/sent/fail/failRetrySum`。
2. `publish-once` 返回 `pulled/sent/failed`，便于判定积压与发送结果。

---

## 3. 场景 B：任务链路监控与手工运行

1. 拉取发货超时任务：
   - `GET /admin/ops/tasks/ship-timeout?status=PENDING&page=1&pageSize=20`
2. 拉取退款失败任务：
   - `GET /admin/ops/tasks/refund?status=FAILED&page=1&pageSize=20`
3. 拉取发货提醒失败任务：
   - `GET /admin/ops/tasks/ship-reminder?status=FAILED&page=1&pageSize=20`
4. 手工执行三类任务：
   - `POST /admin/ops/tasks/ship-timeout/run-once?limit=20`
   - `POST /admin/ops/tasks/refund/run-once?limit=20`
   - `POST /admin/ops/tasks/ship-reminder/run-once?limit=20`

预期：
1. 三类任务接口均可返回分页与状态字段。
2. `run-once` 均返回 `taskType/batchSize/success`。

---

## 4. 场景 C：阈值 SQL 巡检

```sql
-- Outbox
SELECT status, COUNT(*), COALESCE(SUM(retry_count),0) FROM message_outbox GROUP BY status;
SELECT COUNT(*) FROM message_outbox WHERE status='FAIL';
SELECT COALESCE(SUM(retry_count),0) FROM message_outbox WHERE status='FAIL';

-- 任务重试
SELECT COUNT(*) FROM order_ship_timeout_task WHERE status='PENDING' AND retry_count>=5;
SELECT COUNT(*) FROM order_refund_task WHERE status='FAILED';
SELECT COUNT(*) FROM order_ship_reminder_task WHERE status='RUNNING' AND running_at<=DATE_SUB(NOW(), INTERVAL 5 MINUTE);

-- 消费失败
SELECT COUNT(*) FROM mq_consume_log WHERE status='FAIL' AND updated_at>=DATE_SUB(NOW(), INTERVAL 30 MINUTE);
SELECT COUNT(*) FROM mq_consume_log WHERE status='PROCESSING' AND updated_at<DATE_SUB(NOW(), INTERVAL 5 MINUTE);

-- 业务异常（登录风控）
SELECT COUNT(*) FROM user_bans WHERE source='AUTO_RISK' AND DATE(create_time)=CURDATE();
```

预期：
1. SQL 能输出对应规则指标值。
2. 若命中阈值，能对应到 `taskId/eventId/consumer` 明细排障。

---

## 5. 告警阈值对照（复现时使用）

1. Outbox FAIL：WARN `>=5`，CRITICAL `>=10`
2. Outbox FAIL retry_sum：WARN `>=10`，CRITICAL `>=30`
3. 发货超时高重试（`retry_count>=5`）：WARN `>=1`，CRITICAL `>=3`
4. 退款 FAILED：WARN `>=1`，CRITICAL `>=5`
5. 发货提醒 stale RUNNING：WARN `>=1`，CRITICAL `>=5`
6. MQ FAIL（30m）：WARN `>=1`，CRITICAL `>=5`
7. MQ PROCESSING 卡死（>5m）：WARN `>=1`，CRITICAL `>=3`
8. AUTO_RISK 当日新增：WARN `>=5`，CRITICAL `>=20`

---

## 6. DoD 勾选

- [ ] 核心失败场景均有阈值与告警。  
- [ ] 运维可按手册完成标准处置。  
- [ ] 执行记录已回填。  

---

（文件结束）
