# Day17 P4-S4 异步失败恢复流程 v1.0

- 日期：2026-02-24
- 目标：异步投递异常时，保障“可定位、可重试、可验证”。

---

## 1. 故障识别

### 1.1 指标识别

调用：`GET /admin/ops/outbox/metrics`

重点观察：
1. `fail` 持续上升；  
2. `failRetrySum` 快速增长；  
3. `new` 长时间不下降。

### 1.2 日志识别

关键日志：
1. `Outbox 发送失败`  
2. `Outbox 告警`  
3. `幂等命中：consumer=...`

---

## 2. 单事件恢复（推荐优先）

适用：少量失败、明确 `eventId`。

步骤：
1. 查询状态：`GET /admin/ops/outbox/event/{eventId}`  
2. 触发立即重试：`POST /admin/ops/outbox/event/{eventId}/trigger-now`  
3. 手动发布一轮：`POST /admin/ops/outbox/publish-once?limit=20`  
4. 再查事件状态，确认转为 `SENT`。

预期：
1. `updatedRows > 0` 表示已触发重试；  
2. 发布结果里 `sent` 增加；  
3. 消费侧重复命中仅记录日志，不产生重复业务写入。

---

## 3. 批量恢复（失败堆积）

适用：`FAIL` 堆积或故障恢复后追平积压。

步骤：
1. 临时调大 `outbox.publish.batch-size`（如 200）；  
2. 连续执行 `POST /admin/ops/outbox/publish-once?limit=200`；  
3. 同步观察 `metrics`，直至 `fail` 回落到可控阈值；  
4. 将配置回调到日常值。

注意：
1. 不直接改库把 `FAIL` 改成 `SENT`；  
2. 只通过“重试发送 + 消费幂等”恢复，保证语义正确。

---

## 4. 回归校验

### 4.1 Outbox 状态核验 SQL

```sql
SELECT status, COUNT(*) AS cnt
FROM message_outbox
GROUP BY status;
```

### 4.2 单事件追踪 SQL

```sql
SELECT id, event_id, event_type, status, retry_count, next_retry_time, updated_at
FROM message_outbox
WHERE event_id = '替换为目标eventId'
LIMIT 1;
```

### 4.3 消费幂等核验 SQL

```sql
SELECT consumer, event_id, status, COUNT(*) AS cnt
FROM mq_consume_log
WHERE event_id = '替换为目标eventId'
GROUP BY consumer, event_id, status;
```

预期：同一 `consumer + event_id` 不会出现多条成功副作用记录。

---

## 5. 失败类型与处理策略

1. **短暂网络异常 / MQ 抖动**：保持重试，优先单事件恢复。  
2. **下游消费者业务异常**：先修复业务数据，再重试该事件。  
3. **消息结构问题（反序列化失败）**：先修复生产侧 payload，再重发。  
4. **重复投递**：由消费幂等兜底，仅出现“幂等命中”日志。

---

## 6. 恢复完成判定

- [ ] `FAIL` 不再持续增长  
- [ ] 目标事件状态进入 `SENT`  
- [ ] 消费侧无重复副作用（仅幂等命中）  
- [ ] 主业务链路可正常继续处理新请求

---

（文件结束）
