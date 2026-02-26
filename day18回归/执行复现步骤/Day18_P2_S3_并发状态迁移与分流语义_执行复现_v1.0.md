# Day18 P2-S3 并发状态迁移与分流语义 执行复现 v1.0

- 日期：2026-02-25
- 目标：验证关键状态迁移在并发场景下行为可预测，`rows=0` 分流语义稳定。

---

## 1. 前置条件

1. 服务已按最新代码重启完成（必须）。
2. 测试库可访问，且具备管理员与普通用户测试账号。
3. 准备并发压测工具（PowerShell 并发请求或 Postman Runner/Newman）。

---

## 2. 场景 A：管理员并发封禁同一用户（CAS）

1. 目标接口：`PUT /admin/user/{userId}/ban`
2. 并发方式：两条并发请求同时打到同一 `userId`。
3. 预期：
   - 一条返回“用户封禁成功”；
   - 另一条返回“用户已处于封禁状态”或“用户状态已变化，请刷新后重试”；
   - 最终 `users.status='banned'`。

---

## 3. 场景 B：管理员并发解封同一用户（CAS）

1. 先将目标用户置为 `banned`。
2. 目标接口：`PUT /admin/user/{userId}/unban`
3. 并发方式：两条并发请求同时解封。
4. 预期：
   - 一条返回“用户解封成功”；
   - 另一条返回“用户已处于正常状态”或“用户状态已变化，请刷新后重试”；
   - 最终 `users.status='active'`。

---

## 4. 场景 C：订单并发支付/取消（状态机分流）

1. 目标接口：
   - `POST /user/orders/{orderId}/pay`
   - `POST /user/orders/{orderId}/cancel`
2. 并发方式：对同一 `orderId` 并发发起支付与取消。
3. 预期：
   - 只能有一条状态迁移成功；
   - 失败方返回稳定语义（如“订单已支付，无需重复操作”或“订单已取消，无法支付”）；
   - 最终订单状态只会落在单一终态，不出现来回覆盖。

---

## 5. 场景 D：登录失败风控冻结并发（active -> frozen）

1. 对同一账号快速连续发起错误密码登录，触发冻结阈值。
2. 预期：
   - 冻结动作只生效一次（CAS 命中一次）；
   - 不会把非 `active` 账号覆盖写成 `frozen`；
   - 日志中可见幂等命中或跳过记录。

---

## 6. 数据核验 SQL（示例）

```sql
-- 用户状态
SELECT id, status, update_time FROM users WHERE id = ?;

-- 订单状态
SELECT id, status, pay_time, cancel_time, cancel_reason, update_time FROM orders WHERE id = ?;

-- 发货超时/退款/提醒任务状态
SELECT id, status, retry_count, next_retry_time, update_time FROM order_ship_timeout_task WHERE order_id = ?;
SELECT id, status, retry_count, next_retry_time, update_time FROM order_refund_task WHERE order_id = ?;
SELECT id, status, retry_count, remind_time, running_at, update_time FROM order_ship_reminder_task WHERE order_id = ?;
```

---

## 7. PowerShell 并发示例

```powershell
# 场景 A：并发封禁
$headers = @{ Authorization = "Bearer <admin_token>" }
1..2 | ForEach-Object {
  Start-Job -ScriptBlock {
    param($h)
    Invoke-RestMethod -Method Put -Uri "http://localhost:8080/admin/user/2/ban" -Headers $h
  } -ArgumentList $headers
} | Receive-Job -Wait -AutoRemoveJob
```

---

## 8. DoD 勾选

- [ ] 关键状态流转在并发场景行为可预测。  
- [ ] 并发失败分支返回语义稳定。  
- [ ] 执行记录已回填。  

---

（文件结束）
