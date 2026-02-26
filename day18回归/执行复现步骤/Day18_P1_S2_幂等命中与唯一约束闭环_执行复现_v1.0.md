# Day18 P1-S2 幂等命中与唯一约束闭环 执行复现 v1.0

- 日期：2026-02-25
- 目标：验证重复请求不会产生重复副作用，并验证幂等命中日志可检索。
- 说明：本复现不要求故障注入，采用“重复调用 + 数据库核验 + 日志核验”。

---

## 1. 前置条件

1. 服务已启动并可访问。  
2. 使用测试库（建议：`secondhand2`）。  
3. 已准备买家/卖家/管理员账号与 token。  
4. 已确认唯一约束存在（见 `Day18_P1_S2_唯一约束核对结果_v1.0.md`）。

---

## 2. 场景 A：重复支付幂等

1. 构造一笔 `pending` 订单。  
2. 连续两次调用 `POST /user/orders/{orderId}/pay`。  
3. 预期：
   - 第一次返回“支付成功”；
   - 第二次返回“订单已支付，无需重复操作”；
   - `order_ship_timeout_task` 对该 `orderId` 仅 1 条记录。

---

## 3. 场景 B：重复支付回调幂等

1. 对同一 `orderNo` 连续两次调用 `POST /payment/callback`（`status=SUCCESS`）。  
2. 预期：
   - 至少一次返回“支付回调处理成功”；
   - 重复调用返回“订单已支付，回调幂等成功”；
   - 订单状态不会异常反复推进。

---

## 4. 场景 C：任务创建幂等

1. 重复触发支付后任务创建链路（支付接口重复 / 回调重复）。  
2. 连续触发管理端任务执行：
   - `POST /admin/ops/tasks/ship-timeout/run-once?limit=100`
   - `POST /admin/ops/tasks/refund/run-once?limit=100`
3. 预期：
   - 不新增重复超时任务、提醒任务、退款任务；
   - 并发命中时进入幂等分流，不抛系统异常。

---

## 5. 场景 D：消费端去重幂等

1. 对同一 `eventId` 重复投递消息（或触发重复消费回放）。  
2. 预期：
   - `mq_consume_log` 唯一键阻止重复抢占；
   - 日志出现 `幂等命中：consumer=...`；
   - 不产生重复业务副作用。

---

## 6. 数据核验 SQL（MySQL）

```sql
-- 1) 发货超时任务唯一键抽检
SELECT order_id, COUNT(*) AS cnt
FROM order_ship_timeout_task
GROUP BY order_id
HAVING cnt > 1;

-- 2) 发货提醒任务唯一键抽检
SELECT order_id, level, COUNT(*) AS cnt
FROM order_ship_reminder_task
GROUP BY order_id, level
HAVING cnt > 1;

-- 3) 退款任务唯一键抽检
SELECT order_id, refund_type, COUNT(*) AS cnt
FROM order_refund_task
GROUP BY order_id, refund_type
HAVING cnt > 1;

SELECT idempotency_key, COUNT(*) AS cnt
FROM order_refund_task
WHERE idempotency_key IS NOT NULL
GROUP BY idempotency_key
HAVING cnt > 1;

-- 4) 消费日志唯一键抽检
SELECT consumer, event_id, COUNT(*) AS cnt
FROM mq_consume_log
GROUP BY consumer, event_id
HAVING cnt > 1;

-- 5) 积分流水唯一键抽检
SELECT user_id, biz_type, biz_id, COUNT(*) AS cnt
FROM points_ledger
GROUP BY user_id, biz_type, biz_id
HAVING cnt > 1;
```

预期：以上查询均返回 0 行。

---

## 7. 日志核验

1. 检索关键字：`幂等命中：`。  
2. 至少覆盖以下动作中的若干项：
   - `payOrder`
   - `paymentCallback`
   - `createShipTimeoutTask`
   - `createShipReminderTask`
   - `createRefundTask`
   - `grantPoints`
   - `consumer=OrderPaidConsumer`（或其他 Consumer）

---

## 8. DoD 验收勾选

- [ ] 重复请求不会写出重复业务记录。  
- [ ] 核心链路幂等命中后返回语义稳定。  
- [ ] 幂等命中日志可按统一关键字检索。  
- [ ] 唯一约束在目标库完整存在并与代码实现一致。  

---

（文件结束）
