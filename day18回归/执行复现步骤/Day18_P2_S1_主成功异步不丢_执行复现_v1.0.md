# Day18 P2-S1 主成功异步不丢 执行复现 v1.0

- 日期：2026-02-25
- 目标：验证 Outbox 最终一致性与消费幂等。
- 说明：本复现采用“真实 ORDER_PAID 事件 + 受控失败注入 + 恢复重放”。

---

## 1. 前置条件

1. 服务已启动并可访问（`http://localhost:8080`）。
2. 测试库可查询（建议：`secondhand2`）。
3. 已准备管理员 token（用于 `/admin/ops/outbox/**`）。
4. RabbitMQ 与消费者链路可用。

---

## 2. 场景 A：证明“主成功、异步不丢”

1. 选择一条真实 `ORDER_PAID` Outbox 事件：

```sql
SELECT id,event_id,biz_id,status,retry_count
FROM message_outbox
WHERE event_type='ORDER_PAID'
ORDER BY id DESC
LIMIT 1;
```

2. 校验其主业务已成功（订单 `status=paid`）：

```sql
SELECT id,order_no,status,total_amount
FROM orders
WHERE id = <biz_id>;
```

3. 受控注入失败态（演练专用）：

```sql
UPDATE message_outbox
SET status='FAIL',
    next_retry_time=NOW(),
    retry_count=retry_count+1,
    updated_at=NOW()
WHERE id=<outbox_id>
  AND status IN ('SENT','NEW','FAIL');
```

4. 执行恢复：
   - `POST /admin/ops/outbox/event/{eventId}/trigger-now`
   - `POST /admin/ops/outbox/publish-once?limit=50`

5. 预期：
   - 发布返回 `pulled>=1`，且本事件应被发送（`sent` 增加）；
   - 事件状态最终收敛为 `SENT`。

---

## 3. 场景 B：证明“重复消息无重复副作用”

1. 记录重复前指标：

```sql
SELECT COUNT(*) AS consume_count
FROM mq_consume_log
WHERE consumer='OrderPaidConsumer'
  AND event_id='<eventId>';

SELECT COUNT(*) AS reminder_count
FROM order_ship_reminder_task
WHERE order_id=<order_id>;
```

2. 对同一 `eventId` 执行一次重放（同场景 A 的 trigger-now + publish-once）。

3. 记录重复后指标并对比：

```sql
SELECT COUNT(*) AS consume_count
FROM mq_consume_log
WHERE consumer='OrderPaidConsumer'
  AND event_id='<eventId>';

SELECT COUNT(*) AS reminder_count
FROM order_ship_reminder_task
WHERE order_id=<order_id>;
```

4. 预期：
   - `consume_count` 不增加（应保持 1）；
   - `reminder_count` 不增加（副作用不重复）。

---

## 4. 通用核验 SQL

```sql
-- Outbox 状态分布
SELECT status, COUNT(*) AS cnt
FROM message_outbox
GROUP BY status;

-- 消费日志重复组扫描（应为 0）
SELECT COUNT(*) AS dup_groups
FROM (
    SELECT consumer,event_id,COUNT(*) c
    FROM mq_consume_log
    GROUP BY consumer,event_id
    HAVING c>1
) t;

-- Outbox eventId 重复组扫描（应为 0）
SELECT COUNT(*) AS dup_groups
FROM (
    SELECT event_id,COUNT(*) c
    FROM message_outbox
    GROUP BY event_id
    HAVING c>1
) t;
```

---

## 5. DoD 勾选

- [ ] 可证明“主成功、异步不丢”。  
- [ ] 重复消息不会产生重复业务副作用。  
- [ ] 已回填执行记录。  

---

（文件结束）
