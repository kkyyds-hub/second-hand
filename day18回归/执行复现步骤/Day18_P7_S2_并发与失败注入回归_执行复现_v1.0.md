# Day18 P7-S2 并发与失败注入回归 执行复现 v1.0

- 日期：2026-02-26
- 目标：复现并发分流与失败恢复路径，验证状态机与幂等语义。

---

## 1. 前置条件

1. 服务已启动：`http://localhost:8080`。
2. 管理员账号可用（默认：`13900000001/admin123`）。
3. MySQL 可连接（用于注入失败样本）：
   - host: `localhost`
   - db: `secondhand2`
4. 执行工具：PowerShell + MySQL CLI。

---

## 2. 场景 A：并发封禁/解封分流

1. 获取管理员 token。
2. 并发两次调用：`PUT /admin/user/{userId}/ban`。
3. 并发两次调用：`PUT /admin/user/{userId}/unban`。

预期：
1. 封禁结果为“成功 + 幂等命中”组合。
2. 解封结果为“成功 + 幂等命中”组合。

---

## 3. 场景 B：任务并发 run-once

1. 并发两次调用：`POST /admin/ops/tasks/ship-reminder/run-once?limit=50`。
2. 记录两次响应中的 `taskType/batchSize/success`。

预期：
1. 请求均返回成功语义。
2. 不出现重复副作用（同一轮无异常写放大）。

---

## 4. 场景 C：MQ 失败注入（Outbox）

1. 手工插入 `message_outbox`：`exchange_name='bad.exchange'`。
2. 调用：`POST /admin/ops/outbox/publish-once?limit=50`。
3. 调用：`GET /admin/ops/outbox/event/{eventId}` 查询状态。

预期：
1. 状态变为 `FAIL`。
2. `retry_count` 增加。
3. 失败事件可通过 `eventId` 追踪。

---

## 5. 场景 D：失败恢复演练（退款任务）

1. 手工插入 `order_refund_task` 失败记录（`status=FAILED`）。
2. 调用：`POST /admin/ops/tasks/refund/{taskId}/reset`。
3. 调用：`POST /admin/ops/tasks/refund/run-once?limit=200`。
4. 调用：`GET /admin/ops/tasks/refund?orderId={orderId}&page=1&pageSize=50`。

预期：
1. reset 返回 `updatedRows=1`。
2. 任务最终状态为 `SUCCESS`。

---

## 6. 执行命令（示例）

```powershell
Set-Location 'c:\Users\kk\Desktop\demo'
# 执行并发 + Outbox 失败注入，生成结果 JSON
# 输出：day18回归/执行记录/Day18_P7_S2_动态验证结果_*.json

# 执行退款失败恢复演练，生成结果 JSON
# 输出：day18回归/执行记录/Day18_P7_S2_退款失败恢复动态结果_*.json
```

---

## 7. 产物路径

1. `day18回归/执行记录/Day18_P7_S2_动态验证结果_2026-02-26_10-41-48.json`
2. `day18回归/执行记录/Day18_P7_S2_退款失败恢复动态结果_2026-02-26_10-44-25.json`
3. `day18回归/执行记录/Day18_P7_S2_并发与失败注入执行记录_v1.0.md`

---

## 8. DoD 勾选

- [ ] 并发下无重复副作用。  
- [ ] 失败后可恢复且路径可追溯。  

---

（文件结束）
