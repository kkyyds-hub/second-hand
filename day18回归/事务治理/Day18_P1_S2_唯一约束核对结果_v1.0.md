# Day18 P1-S2 唯一约束核对结果 v1.0

- 日期：2026-02-25
- 目标：核对“幂等键 -> 唯一约束”是否已在测试库闭环落地。
- 测试库：`secondhand2`（MySQL）
- 执行方式：代码静态复核 + 信息架构查询 + 重复数据扫描。

---

## 1. 核对命令（已执行）

```sql
SELECT table_name,index_name,GROUP_CONCAT(column_name ORDER BY seq_in_index) cols
FROM information_schema.statistics
WHERE table_schema='secondhand2'
  AND (
    (table_name='orders' AND index_name='uk_orders_order_no')
    OR (table_name='message_outbox' AND index_name='uk_event_id')
    OR (table_name='mq_consume_log' AND index_name='uk_consumer_event')
    OR (table_name='order_ship_timeout_task' AND index_name='uk_ship_timeout_order_id')
    OR (table_name='order_ship_reminder_task' AND index_name='uk_order_level')
    OR (table_name='order_refund_task' AND index_name IN ('uk_refund_order_type','uk_refund_idempotency'))
    OR (table_name='points_ledger' AND index_name='uniq_points_biz')
    OR (table_name='reviews' AND index_name='uniq_order_role')
    OR (table_name='user_violations' AND index_name='uk_user_violation_biz')
    OR (table_name='wallet_transactions' AND index_name='uk_wallet_tx_biz_type_biz_id')
    OR (table_name='favorites' AND index_name='uk_favorites_user_product')
    OR (table_name='after_sales' AND index_name='uniq_order')
  )
GROUP BY table_name,index_name
ORDER BY table_name,index_name;
```

```sql
SELECT 'order_ship_timeout_task' AS t, COUNT(*) c
FROM (SELECT order_id FROM order_ship_timeout_task GROUP BY order_id HAVING COUNT(*)>1) x
UNION ALL
SELECT 'order_ship_reminder_task', COUNT(*)
FROM (SELECT order_id, level FROM order_ship_reminder_task GROUP BY order_id, level HAVING COUNT(*)>1) y
UNION ALL
SELECT 'order_refund_task(order_id,refund_type)', COUNT(*)
FROM (SELECT order_id,refund_type FROM order_refund_task GROUP BY order_id,refund_type HAVING COUNT(*)>1) z
UNION ALL
SELECT 'order_refund_task(idempotency_key)', COUNT(*)
FROM (SELECT idempotency_key FROM order_refund_task WHERE idempotency_key IS NOT NULL GROUP BY idempotency_key HAVING COUNT(*)>1) a
UNION ALL
SELECT 'mq_consume_log(consumer,event_id)', COUNT(*)
FROM (SELECT consumer,event_id FROM mq_consume_log GROUP BY consumer,event_id HAVING COUNT(*)>1) b
UNION ALL
SELECT 'points_ledger(user,biz_type,biz_id)', COUNT(*)
FROM (SELECT user_id,biz_type,biz_id FROM points_ledger GROUP BY user_id,biz_type,biz_id HAVING COUNT(*)>1) c;
```

---

## 2. 唯一约束核对结果

| 表 | 唯一索引 | 列 |
|---|---|---|
| `orders` | `uk_orders_order_no` | `order_no` |
| `message_outbox` | `uk_event_id` | `event_id` |
| `mq_consume_log` | `uk_consumer_event` | `consumer,event_id` |
| `order_ship_timeout_task` | `uk_ship_timeout_order_id` | `order_id` |
| `order_ship_reminder_task` | `uk_order_level` | `order_id,level` |
| `order_refund_task` | `uk_refund_order_type` | `order_id,refund_type` |
| `order_refund_task` | `uk_refund_idempotency` | `idempotency_key` |
| `points_ledger` | `uniq_points_biz` | `user_id,biz_type,biz_id` |
| `reviews` | `uniq_order_role` | `order_id,role` |
| `user_violations` | `uk_user_violation_biz` | `user_id,violation_type,biz_id` |
| `wallet_transactions` | `uk_wallet_tx_biz_type_biz_id` | `biz_type,biz_id` |
| `favorites` | `uk_favorites_user_product` | `user_id,product_id` |
| `after_sales` | `uniq_order` | `order_id` |

结论：P1-S2 核心唯一约束均已存在。

---

## 3. 重复数据扫描结果

| 检查项 | 重复组数 |
|---|---|
| `order_ship_timeout_task(order_id)` | 0 |
| `order_ship_reminder_task(order_id,level)` | 0 |
| `order_refund_task(order_id,refund_type)` | 0 |
| `order_refund_task(idempotency_key)` | 0 |
| `mq_consume_log(consumer,event_id)` | 0 |
| `points_ledger(user_id,biz_type,biz_id)` | 0 |

结论：抽检范围内未发现违反幂等唯一键的数据。

---

## 4. 代码实现一致性核对

1. `OrderShipTimeoutTaskMapper.xml`：`INSERT IGNORE` + `uk_ship_timeout_order_id`。
2. `OrderShipReminderTaskMapper.xml`：`INSERT IGNORE` + `uk_order_level`。
3. `OrderRefundTaskMapper.xml`：`INSERT IGNORE` + `uk_refund_order_type/uk_refund_idempotency`。
4. `MqConsumeLogMapper.xml`：消费者 + 事件键模型与 `uk_consumer_event` 一致。
5. `PointsServiceImpl`：命中 `DuplicateKeyException` 走幂等成功分支。
6. `Message` 实体：Mongo `uniq_order_clientMsg` 复合唯一索引声明（`orderId,fromUserId,clientMsgId`）。

---

## 5. 结论

1. 幂等策略与唯一约束在测试库形成闭环：约束存在、实现一致、抽检无重复。
2. 可直接进入 P1-S2 的动态复现与日志检索验收（见执行复现文档）。

---

（文件结束）
