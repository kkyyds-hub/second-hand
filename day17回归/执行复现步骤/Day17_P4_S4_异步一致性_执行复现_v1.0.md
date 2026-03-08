# Day17 Step P4-S4：异步一致性执行复现步骤

- 文档版本：v1.0
- 日期：2026-02-24
- 目标：验证“主成功后可追踪、异步失败可补偿、重复消费无副作用”。

---

## 1. 前置条件

1. 服务已启动，数据库与 RabbitMQ 可连接。  
2. `message_outbox`、`mq_consume_log` 表可正常读写。  
3. 已有管理员 token（可访问 `/admin/ops/outbox/**`）。  
4. 日志级别包含 `info`，便于观察“幂等命中/发送失败/告警”。

---

## 2. 场景 A：主链路成功后事件可追踪

1. 执行一次会写 Outbox 的主链路操作（如订单支付/状态变更）。  
2. 从日志或业务返回拿到 `eventId`。  
3. 调用：`GET /admin/ops/outbox/event/{eventId}`。

预期：
1. 能查到对应 Outbox 记录；  
2. 状态为 `NEW` 或很快转为 `SENT`；  
3. 证明“主链路成功后事件可追踪”。

---

## 3. 场景 B：异步失败补偿恢复

1. 制造一次可恢复失败（如临时停消费者或模拟路由不可达）。  
2. 观察日志出现 `Outbox 发送失败`，并在 `metrics` 中看到 `fail` 增长。  
3. 调用：`POST /admin/ops/outbox/event/{eventId}/trigger-now`。  
4. 调用：`POST /admin/ops/outbox/publish-once?limit=20`。  
5. 恢复下游后再次查看：`GET /admin/ops/outbox/event/{eventId}`。

预期：
1. `trigger-now` 返回 `updatedRows > 0`；  
2. 发布结果 `sent` 增加；  
3. 该事件最终状态变为 `SENT`。

---

## 4. 场景 C：重复消费幂等验证

1. 对同一 `eventId` 重复触发消息消费（或重放消息）。  
2. 观察消费日志关键字：`幂等命中：consumer=...`。  
3. 查询消费日志表验证不重复执行业务副作用。

核验 SQL：

```sql
SELECT consumer, event_id, COUNT(*) AS cnt
FROM mq_consume_log
WHERE event_id = '替换为目标eventId'
GROUP BY consumer, event_id;
```

预期：每个消费者对同一 `eventId` 只保留单次有效处理语义。

---

## 5. 指标核验

调用：`GET /admin/ops/outbox/metrics`

预期：
1. 恢复后 `fail` 回落；  
2. `sent` 持续增长；  
3. 无长期堆积的 `new`。

---

## 6. DoD 验收勾选

- [ ] 主链路成功后事件可追踪  
- [ ] 异步失败可通过补偿恢复  
- [ ] 重复消费不产生重复副作用  
- [ ] 指标与日志可支撑故障排查

---

（文件结束）
