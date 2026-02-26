# Day18 P5-S3 异常交易监控与人工审核 执行复现 v1.0

- 日期：2026-02-26
- 目标：复现“规则识别 -> 自动打标 -> 人工审核 -> 处置闭环”。

---

## 1. 前置条件

1. 服务已启动并可访问：`http://localhost:8080`。
2. 测试库可访问（示例：`secondhand2`）。
3. 已准备管理员账号（用于打标与处置）。
4. 已有可用于演练的订单数据（或可插入测试订单）。

---

## 2. 场景 A：大额交易规则（R1）命中

1. 执行 R1 检测 SQL，获取命中订单：

```sql
SELECT o.id AS order_id, o.total_amount, o.buyer_id, o.seller_id
FROM orders o
WHERE o.status IN ('paid','shipped','completed')
  AND o.pay_time >= DATE_SUB(NOW(), INTERVAL 24 HOUR)
  AND o.total_amount >= 5000
ORDER BY o.pay_time DESC;
```

2. 对命中订单调用打标接口：
   - `POST /admin/orders/{orderId}/flags`
   - body：

```json
{
  "type": "risk_high_amount",
  "remark": "{\"ruleId\":\"R1_HIGH_AMOUNT\",\"threshold\":5000,\"riskLevel\":\"P2\"}"
}
```

3. 预期：
   - 首次打标返回“标记成功”；
   - 重复打标返回“订单已存在该类型标记”（幂等）。

---

## 3. 场景 B：频繁退款规则（R2）命中

1. 执行 R2 检测 SQL：

```sql
SELECT o.seller_id, COUNT(*) AS refund_cnt, MAX(t.order_id) AS latest_order_id
FROM order_refund_task t
JOIN orders o ON o.id = t.order_id
WHERE t.create_time >= DATE_SUB(NOW(), INTERVAL 24 HOUR)
  AND t.status IN ('SUCCESS','FAILED')
GROUP BY o.seller_id
HAVING COUNT(*) >= 3;
```

2. 取 `latest_order_id` 作为打标订单，调用：
   - `POST /admin/orders/{latestOrderId}/flags`
   - `type = risk_frequent_refund`

3. 人工审核辅助接口：
   - `GET /admin/ops/tasks/refund?orderId={latestOrderId}&page=1&pageSize=20`

4. 预期：
   - 可定位退款任务状态与失败原因；
   - 风险订单已进入标记池。

---

## 4. 场景 C：异常取消规则（R3）命中

1. 执行 R3 检测 SQL：

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

2. 对 `latest_cancel_order_id` 打标：
   - `POST /admin/orders/{orderId}/flags`
   - `type = risk_abnormal_cancel`

3. 预期：
   - 可观测到取消行为异常样本；
   - 异常订单可进入人工审核队列。

---

## 5. 场景 D：人工审核与处置闭环

1. 管理员查询订单：
   - `GET /admin/orders?page=1&pageSize=20`
2. 根据风险等级执行处置：
   - P1：必要时 `PUT /admin/user/{userId}/ban`；
   - P2/P3：继续观察或补充证据。
3. 处置后记录结果（工单/执行记录）并可按需解封：
   - `PUT /admin/user/{userId}/unban`。

预期：
1. 命中风险事件都有明确处置入口；
2. 人工审核动作具备可执行接口与固定流程。

---

## 6. SQL 核验（示例）

```sql
-- 风险标记核验（按类型）
SELECT id, order_id, type, remark, created_by, create_time
FROM order_flags
WHERE type IN ('risk_high_amount', 'risk_frequent_refund', 'risk_abnormal_cancel')
ORDER BY id DESC
LIMIT 20;

-- 唯一键幂等核验（同订单同类型不得重复）
SELECT order_id, type, COUNT(*) AS cnt
FROM order_flags
GROUP BY order_id, type
HAVING COUNT(*) > 1;
```

---

## 7. DoD 勾选

- [ ] 至少 3 条规则可执行并有命中样本。  
- [ ] 风险事件可进入人工审核链路。  
- [ ] 执行记录已回填（含规则命中、打标幂等、人工处置）。  

---

（文件结束）
