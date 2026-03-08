# Day17 Step P4-S3：并发更新安全执行复现步骤

- 文档版本：v1.0
- 日期：2026-02-24
- 目标：验证关键状态流转在并发下无覆盖写、无错乱状态，且失败分支语义明确。

---

## 1. 前置条件

1. 服务已启动，数据库与 Mongo 可连接。  
2. 已准备：买家 token、卖家 token、管理员 token。  
3. 已有可用订单数据（至少包含 `pending`、`paid` 两类）。  
4. 日志级别可查看 `info`（用于观察 CAS 未命中 / 幂等命中）。

---

## 2. 订单主链路并发回归

### 场景 A：同一订单并发支付（双击支付）

1. 对同一 `orderId` 并发发送 2 次：`POST /user/orders/{orderId}/pay`。  
2. 预期：
   - 仅 1 次真实推进 `pending -> paid`；  
   - 另一请求返回“订单已支付，无需重复操作”；  
   - `order_ship_timeout_task` 对该订单最多 1 条。

### 场景 B：支付与取消并发竞争

1. 线程 1：`POST /user/orders/{orderId}/pay`。  
2. 线程 2：`POST /user/orders/{orderId}/cancel`。  
3. 预期：
   - 仅一个状态迁移成功；  
   - 另一方返回明确业务语义（如“订单已支付，当前不允许取消”）；  
   - 最终状态不出现“paid 与 cancelled 来回覆盖”。

### 场景 C：卖家发货并发双提交

1. 对同一 `orderId` 并发 2 次：`POST /user/orders/{orderId}/ship`。  
2. 预期：
   - 仅一次真实推进 `paid -> shipped`；  
   - 另一请求返回“订单已发货，无需重复操作”。

---

## 3. 任务链路并发回归

### 场景 D：超时任务被多线程同时扫描

1. 连续快速触发 2 次：`POST /admin/ops/tasks/ship-timeout/run-once?limit=200`。  
2. 预期：
   - 同一订单不会重复关单；  
   - 任务状态最终稳定在 `DONE/CANCELLED`；  
   - 日志可见 CAS 分流语义（如“发货超时任务标记完成 CAS 未命中”）。

### 场景 E：提醒任务并发处理与回收竞争

1. 触发：`POST /admin/ops/tasks/ship-reminder/run-once?limit=200`。  
2. 触发“立即重跑/卡死回收”场景后再触发一次。  
3. 预期：
   - 无重复成功写入导致的状态错乱；  
   - `markSuccess/markFail/markCancelled` 出现 `rows=0` 时被回查分流并记录日志。

### 场景 F：退款任务并发处理

1. 连续快速触发 2 次：`POST /admin/ops/tasks/refund/run-once?limit=200`。  
2. 预期：
   - 同一退款任务最多一次真实推进到 `SUCCESS`；  
   - 其余并发线程进入幂等或 CAS 未命中分流，不重复副作用。

---

## 4. 数据一致性核验 SQL

```sql
-- 1) 同一订单最多一个发货超时任务
SELECT order_id, COUNT(*) AS cnt
FROM order_ship_timeout_task
GROUP BY order_id
HAVING cnt > 1;

-- 2) 同一订单+档位最多一个提醒任务
SELECT order_id, level, COUNT(*) AS cnt
FROM order_ship_reminder_task
GROUP BY order_id, level
HAVING cnt > 1;

-- 3) 同一退款业务键最多一条退款任务
SELECT idempotency_key, COUNT(*) AS cnt
FROM order_refund_task
GROUP BY idempotency_key
HAVING cnt > 1;

-- 4) 检查订单状态是否出现非法组合（按业务补充条件）
SELECT id, order_no, status, pay_time, ship_time, complete_time, cancel_time
FROM orders
WHERE (status = 'paid' AND cancel_time IS NOT NULL)
   OR (status = 'cancelled' AND complete_time IS NOT NULL);
```

预期：前 3 条查询均返回 0 行；第 4 条无明显非法状态组合。

---

## 5. 日志核验关键字

1. 幂等命中：`幂等命中：`  
2. 并发分流（CAS 未命中）：
   - `退款任务 CAS 未命中`
   - `发货提醒任务标记成功 CAS 未命中`
   - `发货超时任务标记完成 CAS 未命中`
3. 预期：出现 CAS 未命中日志时，系统行为仍可继续，不抛出不可恢复异常。

---

## 6. DoD 验收勾选

- [ ] 关键状态流转在并发下无覆盖写  
- [ ] `rows=0` 分支都有明确业务语义/日志语义  
- [ ] 并发冲突不导致系统状态错乱  
- [ ] 并发回归用例可重复执行并得到稳定结果

---

（文件结束）
