# Day17 Step P4-S2：幂等机制标准化执行复现步骤

- 文档版本：v1.0
- 日期：2026-02-24
- 目标：验证“重复请求不产生重复副作用”，并验证幂等命中日志可追踪。

---

## 1. 前置条件

1. 服务已启动，数据库连接正常。  
2. 已准备：买家 token、卖家 token、管理员 token。  
3. 测试数据满足：
   - 至少 1 笔可支付订单（`pending`）；
   - 至少 1 笔可做支付回调的订单号；
   - Mongo `order_messages` 集合可写。  
4. 已在目标库执行：
   - `day17回归/幂等治理/Day17_P4_S2_唯一约束脚本_v1.0.sql`

---

## 2. 关键验证场景

### 2.1 重复支付（用户接口）

1. 第一次调用：`POST /user/orders/{orderId}/pay`（买家）。  
2. 第二次调用：同一个 `orderId` 再调一次。  
3. 预期：
   - 第一次返回“支付成功”；  
   - 第二次返回“订单已支付，无需重复操作”；  
   - `order_ship_timeout_task` 对该 `orderId` 只有 1 条记录。

### 2.2 重复支付回调

1. 连续两次调用：`POST /payment/callback`，`orderNo` 相同、`status=SUCCESS`。  
2. 预期：
   - 至少一次返回“支付回调处理成功”；  
   - 重复回调返回“订单已支付，回调幂等成功”；  
   - 订单状态保持 `paid/shipped/completed`，不会重复推进异常状态。

### 2.3 重复发送会话消息（clientMsgId）

1. 调用：`POST /user/messages/orders/{orderId}`，请求体固定 `clientMsgId`。  
2. 用同一用户、同一 `orderId`、同一 `clientMsgId` 重复调用一次。  
3. 预期：
   - 两次都返回成功；  
   - 第二次命中幂等，返回已存在消息（不新增新文档）；  
   - Mongo 中 `orderId + fromUserId + clientMsgId` 唯一。

### 2.4 任务链路幂等（管理端触发）

1. 连续触发：`POST /admin/ops/tasks/ship-timeout/run-once?limit=200`。  
2. 连续触发：`POST /admin/ops/tasks/refund/run-once?limit=200`。  
3. 预期：
   - 不出现重复退款任务/重复提醒任务；  
   - 幂等命中时任务保持可预期状态（不抛系统异常）。

---

## 3. 数据库核验 SQL（MySQL）

```sql
-- 1) 发货超时任务不重复
SELECT order_id, COUNT(*) AS cnt
FROM order_ship_timeout_task
GROUP BY order_id
HAVING cnt > 1;

-- 2) 退款任务业务幂等键不重复
SELECT idempotency_key, COUNT(*) AS cnt
FROM order_refund_task
GROUP BY idempotency_key
HAVING cnt > 1;

-- 3) 积分流水业务键不重复
SELECT user_id, biz_type, biz_id, COUNT(*) AS cnt
FROM points_ledger
GROUP BY user_id, biz_type, biz_id
HAVING cnt > 1;

-- 4) MQ 消费日志不重复
SELECT consumer, event_id, COUNT(*) AS cnt
FROM mq_consume_log
GROUP BY consumer, event_id
HAVING cnt > 1;
```

预期：以上查询都返回 0 行。

---

## 4. 日志核验（幂等命中可追踪）

1. 在日志中搜索关键字：`幂等命中：`。  
2. 至少应看到以下 action 中的若干条：
   - `payOrder`
   - `paymentCallback`
   - `sendMessage`
   - `createShipReminderTask`
   - `createRefundTask`
   - `grantPoints`
3. 每条日志应包含：
   - `action`
   - `idemKey`
   - `detail`

---

## 5. DoD 验收勾选

- [ ] 重复提交核心接口不产生重复数据  
- [ ] 关键任务链路幂等命中后行为稳定  
- [ ] 幂等命中日志可按 `幂等命中：` 统一检索  
- [ ] 唯一约束脚本在目标库可重复执行（存在即跳过）

---

（文件结束）
