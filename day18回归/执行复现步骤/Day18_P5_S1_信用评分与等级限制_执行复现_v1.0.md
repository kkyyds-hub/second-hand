# Day18 P5-S1 信用评分与等级限制 执行复现 v1.0

- 日期：2026-02-25
- 目标：验证“信用分来源可追溯 + 等级限制行为一致”两个 DoD。

---

## 1. 前置条件

1. 服务已启动并可访问：`http://localhost:8080`。
2. 测试库可访问（示例：`secondhand2`）。
3. 已准备管理员账号与 1 个卖家测试账号。
4. 卖家账号 `is_seller=1`。

---

## 2. 场景 A：信用分来源字段可追溯

1. 管理员触发重算：`POST /admin/credit/recalc?userId={sellerId}`。
2. 查询信用快照：`GET /admin/credit?userId={sellerId}`。
3. 执行 SQL 核验来源字段：

```sql
-- 订单完成（买家/卖家）
SELECT COUNT(*) FROM orders WHERE buyer_id = ? AND status = 'completed';
SELECT COUNT(*) FROM orders WHERE seller_id = ? AND status = 'completed';

-- 买家取消
SELECT COUNT(*) FROM orders WHERE buyer_id = ? AND status = 'cancelled';

-- 违规扣分累计
SELECT COALESCE(SUM(credit), 0) FROM user_violations WHERE user_id = ?;

-- 有效封禁
SELECT COUNT(*)
FROM user_bans
WHERE user_id = ?
  AND start_time <= NOW()
  AND (end_time IS NULL OR end_time > NOW());

-- 管理员手工调分累计
SELECT COALESCE(SUM(delta), 0)
FROM user_credit_logs
WHERE user_id = ?
  AND reason_type = 'admin_adjust';
```

4. 预期：SQL 统计项与服务重算结果可对齐解释。

---

## 3. 场景 B：LV1 卖家创建商品被阻断

1. 把卖家分数调到 LV1 区间（如 20）：
   - `POST /admin/credit/adjust`
2. 再次触发重算并确认等级为 `lv1`。
3. 卖家调用：`POST /user/products`。
4. 预期：返回失败，错误信息包含 `信用等级过低（LV1），暂不可发布商品`。

---

## 4. 场景 C：LV2 卖家活跃商品上限生效

1. 把卖家分数调到 LV2 区间（如 65），重算后确认等级 `lv2`。
2. 保证卖家当前活跃商品（`under_review + on_sale`）小于 3。
3. 连续创建商品 4 次：
   - 前 3 次预期成功；
   - 第 4 次预期失败。
4. 预期：第 4 次返回 `信用等级为 LV2，活跃商品数量已达上限：3`。

---

## 5. 场景 D：LV3+ 创建商品放行

1. 把卖家分数调到 LV3/LV4/LV5 任一等级区间。
2. 卖家调用 `POST /user/products`。
3. 预期：接口成功创建商品（不触发信用等级限制错误）。

---

## 6. 核验 SQL（卖家限制）

```sql
-- 查询当前信用快照
SELECT id, credit_score, credit_level, credit_updated_at
FROM users
WHERE id = ?;

-- 查询活跃商品数（LV2 限额判断口径）
SELECT COUNT(*)
FROM products
WHERE owner_id = ?
  AND is_deleted = 0
  AND status IN ('under_review', 'on_sale');
```

---

## 7. DoD 勾选

- [ ] 信用分来源字段可追溯。
- [ ] 限制策略与等级映射一致。
- [ ] 执行记录已回填。

---

（文件结束）
