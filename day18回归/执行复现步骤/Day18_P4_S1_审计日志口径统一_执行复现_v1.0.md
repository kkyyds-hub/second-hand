# Day18 P4-S1 审计日志口径统一 执行复现 v1.0

- 日期：2026-02-25  
- 目标：验证关键敏感操作均输出统一 `AUDIT` 字段，且可通过 `auditId` 串联排障链路。

---

## 1. 前置条件

1. 服务已按最新代码重启完成（必须）。  
2. 准备测试账号：管理员、普通用户。  
3. 可访问应用日志输出（IDEA 控制台或日志采集平台）。  
4. 建议先清空/截断本轮测试前日志，避免检索干扰。

---

## 2. 场景 A：用户登录审计（成功 + 失败）

1. 请求：`POST /user/auth/login/password`（错误密码一次，正确密码一次）。  
2. 预期：  
   - 失败日志：`action=USER_LOGIN,result=FAILED,error=PASSWORD_MISMATCH`；  
   - 成功日志：`action=USER_LOGIN,result=SUCCESS`；  
   - 两条日志都含 `auditId/actorId/targetId/ip/detail`。

---

## 3. 场景 B：管理员登录审计（成功 + 失败）

1. 请求：`POST /admin/employee/login`（错误密码一次，正确密码一次）。  
2. 预期：  
   - 失败日志：`action=ADMIN_LOGIN,result=FAILED,error=PASSWORD_MISMATCH`（或对应失败编码）；  
   - 成功日志：`action=ADMIN_LOGIN,result=SUCCESS`；  
   - 字段完整性与场景 A 一致。

---

## 4. 场景 C：订单支付/取消审计

1. 请求：  
   - `POST /user/orders/{orderId}/pay`  
   - `POST /user/orders/{orderId}/cancel`（可使用已支付订单验证失败分支）  
2. 预期：  
   - 支付成功：`action=ORDER_PAY,result=SUCCESS`；  
   - 重复支付：`action=ORDER_PAY,result=IDEMPOTENT`；  
   - 取消失败：`action=ORDER_CANCEL,result=FAILED,error=STATUS_NOT_ALLOW`。

---

## 5. 场景 D：支付回调审计

1. 请求：`POST /payment/callback`（覆盖 `SIGN_EMPTY`、时间戳过期、成功回调、重复回调）。  
2. 预期：  
   - 非法回调：`action=PAYMENT_CALLBACK,result=FAILED,error=...`；  
   - 重复回调：`action=PAYMENT_CALLBACK,result=IDEMPOTENT`；  
   - 非 SUCCESS 状态：`action=PAYMENT_CALLBACK,result=IGNORED`。

---

## 6. 场景 E：封禁/解封审计

1. 请求：  
   - `PUT /admin/user/{userId}/ban`  
   - `PUT /admin/user/{userId}/unban`  
2. 预期：  
   - 成功分支：`result=SUCCESS`；  
   - 幂等分支：`result=IDEMPOTENT`；  
   - 并发冲突或非法状态：`result=FAILED,error=CAS_CONFLICT/STATUS_NOT_BANNED/...`。

---

## 7. 日志检索示例

```powershell
# 示例 1：筛选全部审计日志（日志文件模式）
Get-Content .\app.log | Select-String "AUDIT "

# 示例 2：按动作检索
Get-Content .\app.log | Select-String "action=ORDER_PAY"

# 示例 3：按失败编码聚合定位
Get-Content .\app.log | Select-String "result=FAILED"
```

若使用 IDEA 控制台运行服务，直接在 Run 窗口搜索 `AUDIT`、`auditId=`、`action=`。

---

## 8. DoD 勾选

- [ ] 核心敏感动作具备统一日志字段。  
- [ ] 可通过日志串联关键链路完成一次排障定位。  
- [ ] 执行记录已回填。  

---

（文件结束）
