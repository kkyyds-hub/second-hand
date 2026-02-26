# Day18 P5-S3 异常交易规则执行记录 v1.0

- 日期：2026-02-26
- 关联复现文档：`day18回归/执行复现步骤/Day18_P5_S3_异常交易监控与人工审核_执行复现_v1.0.md`
- 当前状态：已执行（A/B/C/D 已完成回填）。

---

## 1. 环境信息

1. 服务地址：`http://localhost:8080`
2. 数据库：`secondhand2`
3. 执行人：`Codex`
4. 执行时间：`2026-02-26 00:42:49 ~ 00:42:50`
5. 原始证据：`day18回归/执行记录/Day18_P5_S3_动态验证结果_2026-02-26_00-42-49.json`
6. 说明：本次为满足 24h 窗口阈值，在测试库插入最小化演练数据（订单与退款任务），仅用于 P5-S3 规则验证。

---

## 2. 静态核验结论（已完成）

1. 已冻结 3 条最小可用规则：R1/R2/R3。
2. 已定义标准打标类型：`risk_high_amount`、`risk_frequent_refund`、`risk_abnormal_cancel`。
3. 已明确自动标记与人工审核接口，以及 P1/P2/P3 分级处置时限。

---

## 3. 动态执行回填表

| 场景 | 输入 | 预期 | 实际结果 | 是否通过 |
|---|---|---|---|---|
| A 大额交易命中 | R1 SQL + `POST /admin/orders/{orderId}/flags(type=risk_high_amount)` | 命中样本可打标；重复打标幂等 | R1 命中 `orderId=900046(total_amount=6000)`；首次打标“标记成功”，二次打标“订单已存在该类型标记” | `[x]` |
| B 频繁退款命中 | R2 SQL + `type=risk_frequent_refund` | 命中卖家可定位；风险订单可打标 | R2 命中 `sellerId=2, refund_cnt=3, latest_order_id=900049`；首次打标成功，二次打标幂等命中 | `[x]` |
| C 异常取消命中 | R3 SQL + `type=risk_abnormal_cancel` | 命中买家可定位；风险订单可打标 | R3 命中 `buyerId=6,total=5,cancel=3,rate=0.6000,latest_cancel_order_id=900052`；首次打标成功，二次打标幂等命中 | `[x]` |
| D 人工审核闭环 | 管理端查询 + 处置接口（ban/unban） | 风险事件进入人工审核并可处置 | `GET /admin/orders` 成功返回；`PUT /admin/user/6/ban` 成功；`PUT /admin/user/6/unban` 成功；用户状态恢复 `active` | `[x]` |

---

## 4. 关键结果摘录

1. 规则命中统计：`r1_hits_now=1`、`r2_hits_now=1`、`r3_hits_now=1`。  
2. 风险打标落库：`order_flags` 新增 3 条（`risk_high_amount/risk_frequent_refund/risk_abnormal_cancel`）。  
3. 幂等校验：重复打标均返回“订单已存在该类型标记”；SQL 抽检 `dup_flags=0`。  
4. 人工处置闭环：管理员完成“查询 -> 封禁 -> 解封”，`users.id=6` 最终状态 `active`。  

---

## 5. DoD 勾选（回填区）

- [x] 至少 3 条可执行规则具备实施证据。  
- [x] 风险事件可进入人工审核链路并完成处置演练。  

---

（文件结束）
