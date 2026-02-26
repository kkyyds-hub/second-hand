# Day18 P5-S3 异常交易规则清单 v1.0

- 日期：2026-02-26
- 对应阶段：`Step P5-S3：交易异常监控规则定义`
- 目标：形成“可执行、可落库、可人工审核”的最小可用异常交易规则集。

---

## 1. 结论先行（本版冻结）

1. 本版冻结 3 条可执行异常交易规则：`大额交易`、`频繁退款`、`异常取消`。
2. 风险事件统一先落到 `order_flags`，作为人工审核链路入口。
3. 自动标记采用“规则命中 -> 幂等打标（`order_id + type`）-> 人工审核处置”闭环。
4. 人工审核沿用现有管理端接口，不新增 Day18 强依赖组件。

---

## 2. 风险事件落库模型（MVP）

## 2.1 事件主键与幂等

1. 事件锚点：`order_id`（订单级风险事件）。
2. 事件类型：`type`（规则编码映射）。
3. 幂等约束：`order_flags.uniq_order_type(order_id, type)`，同订单同类型风险不重复落库。

## 2.2 类型映射（冻结）

| 规则编码 | `order_flags.type` | 说明 |
|---|---|---|
| `R1_HIGH_AMOUNT` | `risk_high_amount` | 大额交易 |
| `R2_FREQUENT_REFUND` | `risk_frequent_refund` | 频繁退款 |
| `R3_ABNORMAL_CANCEL` | `risk_abnormal_cancel` | 异常取消 |

`remark` 统一建议写入结构化文本（JSON 字符串）：`ruleId/window/metric/threshold/riskLevel/detail`。

---

## 3. 规则矩阵（至少 3 条可执行）

| 规则 | 检测对象 | 窗口 | 命中阈值（MVP） | 风险等级建议 | 自动动作 | 人工动作 |
|---|---|---|---|---|---|---|
| R1 大额交易 | 已支付/已发货/已完成订单 | 实时（近24h巡检） | `total_amount >= 5000` | `P2`；若 `>=10000` 升 `P1` | 写 `risk_high_amount` 标记 | 审核订单与账号，必要时封禁卖家 |
| R2 频繁退款 | 卖家维度退款任务 | 24h | 同一卖家 `refund_cnt >= 3` | `P2`；若 `>=5` 升 `P1` | 对最新退款订单写 `risk_frequent_refund` 标记 | 核查退款原因，必要时限制账号/人工复核 |
| R3 异常取消 | 买家维度取消行为 | 24h | `total_cnt>=5` 且 `cancel_cnt>=3` 且 `cancel_rate>=0.60` | `P3`；若 `cancel_cnt>=5 且 rate>=0.70` 升 `P2` | 对最新取消订单写 `risk_abnormal_cancel` 标记 | 核查恶意下单取消，必要时账号处置 |

---

## 4. 规则 SQL 口径（可直接实施）

## 4.1 R1 大额交易

```sql
SELECT
  o.id AS order_id,
  o.buyer_id,
  o.seller_id,
  o.total_amount,
  o.pay_time
FROM orders o
WHERE o.status IN ('paid','shipped','completed')
  AND o.pay_time >= DATE_SUB(NOW(), INTERVAL 24 HOUR)
  AND o.total_amount >= 5000
ORDER BY o.pay_time DESC;
```

## 4.2 R2 频繁退款（卖家维度）

```sql
SELECT
  o.seller_id,
  COUNT(*) AS refund_cnt,
  MAX(t.order_id) AS latest_order_id
FROM order_refund_task t
JOIN orders o ON o.id = t.order_id
WHERE t.create_time >= DATE_SUB(NOW(), INTERVAL 24 HOUR)
  AND t.status IN ('SUCCESS','FAILED')
GROUP BY o.seller_id
HAVING COUNT(*) >= 3;
```

## 4.3 R3 异常取消（买家维度）

```sql
SELECT
  x.buyer_id,
  x.total_cnt,
  x.cancel_cnt,
  ROUND(x.cancel_cnt / x.total_cnt, 4) AS cancel_rate,
  x.latest_cancel_order_id
FROM (
  SELECT
    o.buyer_id,
    COUNT(*) AS total_cnt,
    SUM(CASE WHEN o.status='cancelled' AND o.cancel_reason='buyer_cancel' THEN 1 ELSE 0 END) AS cancel_cnt,
    MAX(CASE WHEN o.status='cancelled' AND o.cancel_reason='buyer_cancel' THEN o.id ELSE NULL END) AS latest_cancel_order_id
  FROM orders o
  WHERE o.create_time >= DATE_SUB(NOW(), INTERVAL 24 HOUR)
  GROUP BY o.buyer_id
) x
WHERE x.total_cnt >= 5
  AND x.cancel_cnt >= 3
  AND x.cancel_cnt / x.total_cnt >= 0.60;
```

---

## 5. 自动标记实施方案（冻结）

## 5.1 方案 A（Day18 即可执行，无需改代码）

1. 通过 SQL 巡检脚本定时识别命中订单（建议 5 分钟一次）。
2. 巡检命中后调用管理端打标接口：
   - `POST /admin/orders/{orderId}/flags`
   - `body.type` 使用本文件定义的标准类型。
3. 依赖 `order_flags` 唯一键实现幂等，重复命中只会保留一条标记。

## 5.2 方案 B（后续推荐，应用内定时任务）

1. 新增风险巡检 Job（如 `RiskOrderDetectJob`），在服务内执行规则 SQL。
2. 直接通过 `OrderFlagMapper.insertOrderFlag` 落库，`created_by` 固定系统账号。
3. 统一输出审计日志：`action=ORDER_RISK_FLAG`，便于告警联动。

---

## 6. 人工审核接口与处置流程

## 6.1 可复用接口（当前工程已具备）

| 目的 | 接口 | 用途 |
|---|---|---|
| 查询待审订单 | `GET /admin/orders` | 查看订单状态、金额、取消原因 |
| 风险打标 | `POST /admin/orders/{orderId}/flags` | 写入风险事件（幂等） |
| 查询退款任务 | `GET /admin/ops/tasks/refund` | 观察退款任务失败/重试状态 |
| 退款任务恢复 | `POST /admin/ops/tasks/refund/{taskId}/reset` | 人工恢复退款任务 |
| 风险账号处置 | `PUT /admin/user/{userId}/ban` / `PUT /admin/user/{userId}/unban` | 临时或人工处置账号 |

## 6.2 风险分级处置（冻结）

| 等级 | 响应时限 | 处置动作 |
|---|---|---|
| P1（紧急） | 15 分钟 | 立即人工介入，必要时先封禁再复核 |
| P2（高） | 2 小时 | 完成订单/退款链路核查并给出处置结论 |
| P3（中） | 24 小时 | 进入观察池，二次命中升级 |

标准流程：`规则命中 -> 自动打标 -> 人工审核 -> 处置/恢复 -> 结果留痕`。

---

## 7. 已知边界与后续建议

1. 当前无独立 `risk_events` 表，MVP 以 `order_flags` 承载风险事件。
2. 当前“自动标记”默认采用巡检脚本方式，尚未内置统一风险检测 Job。
3. 建议 Day19 起新增：
   - 风险事件表（支持状态流转与审核人字段）；
   - 风险规则配置中心（阈值可配置）；
   - 风险命中审计动作标准化（`ORDER_RISK_FLAG`）。

---

## 8. DoD 对齐（P5-S3 文档阶段）

- [x] 至少 3 条可执行规则具备实施方案。  
- [x] 风险事件可进入人工审核链路（通过 `order_flags` + 管理端接口）。  
- [ ] 待补动态验证执行记录（规则命中与人工处置演练）。  

---

## 9. 代码证据索引

1. `demo-service/src/main/java/com/demo/controller/admin/AdminOrderController.java`
2. `demo-service/src/main/java/com/demo/mapper/OrderFlagMapper.java`
3. `demo-service/src/main/resources/mapper/OrderFlagMapper.xml`
4. `demo-service/src/main/java/com/demo/mapper/OrderMapper.java`
5. `demo-service/src/main/resources/mapper/OrderMapper.xml`
6. `demo-service/src/main/java/com/demo/controller/admin/AdminTaskOpsController.java`
7. `demo-service/src/main/resources/mapper/OrderRefundTaskMapper.xml`
8. `demo-service/src/main/java/com/demo/controller/admin/UserController.java`
9. `demo-service/src/main/java/com/demo/controller/admin/ViolationController.java`
10. `day17回归/幂等治理/Day17_P4_S2_唯一约束脚本_v1.0.sql`

---

（文件结束）
