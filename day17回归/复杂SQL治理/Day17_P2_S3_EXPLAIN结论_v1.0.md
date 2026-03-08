# Day17 Step P2-S3：关键复杂 SQL EXPLAIN 结论

- 文档版本：v1.0
- 执行日期：2026-02-24
- 数据库：`secondhand2`（MySQL 8.x，本地）
- 执行方式：`mysql -h localhost -P 3306 -uroot -p*** -D secondhand2 -e "EXPLAIN ..."`

---

## 1. 校验 SQL 与结论
### 1.1 SQL_ID: `P2S3-PROD-002`（市场全文检索）
- 结论：
  1. 命中 `FULLTEXT` 索引 `ft_title_desc_ngram`。
  2. 仍出现 `Using filesort`（相关性排序 + 时间排序组合导致）。
  3. 当前保留 XML 合理，后续可评估排序策略与结果窗口大小优化。

### 1.2 SQL_ID: `P2S3-ORD-001`（买家订单列表 Join）
- 结论：
  1. `orders` 当前执行计划为 `ALL`（全表扫描）并出现 `Using temporary; Using filesort`。
  2. `order_items` 命中 `fk_items_order`，`products` 命中主键。
  3. 风险点在 `orders` 与 `users` 驱动表选择，建议补/调复合索引（见第2章）。

### 1.3 SQL_ID: `P2S3-WAL-001`（钱包行锁）
- 结论：
  1. 命中 `PRIMARY(user_id)`，类型 `const`。
  2. 扫描行数为 1，符合资金链路预期。
  3. `FOR UPDATE` 行锁语义明确，保留 XML 合理。

---

## 2. 优化建议（不在本步强制实施）
1. 针对 `P2S3-ORD-001`：
   - 建议确认/补充 `orders(buyer_id, create_time)` 或 `orders(buyer_id, status, create_time)` 复合索引。
   - 评估 `users(is_deleted, id)` 或通过驱动顺序优化减少 `users` 全扫。
2. 针对 `P2S3-PROD-002`：
   - 评估“相关性排序优先 + 时间次排序”的窗口限制策略，降低 `filesort` 成本。

---

## 3. DoD 对齐
1. 关键复杂 SQL 已完成至少一次 EXPLAIN 校验：**已满足**。
2. EXPLAIN 结论已形成文档，可用于后续索引治理与回归对照：**已满足**。

---

（文件结束）
