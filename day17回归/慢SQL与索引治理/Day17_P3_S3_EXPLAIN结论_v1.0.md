# Day17 P3-S3 EXPLAIN 结论 v1.0

- 日期：2026-02-24
- 数据库：`secondhand2`（MySQL 8.4.6）
- 说明：对关键 SQL 记录“优化前/优化后”EXPLAIN 指标，作为可审计依据

---

## 1. 采样 SQL

1. 地址分页：
```sql
SELECT id,user_id,is_default,updated_at
FROM addresses
WHERE user_id = 1
ORDER BY is_default DESC, updated_at DESC
LIMIT 0,20;
```

2. 默认地址查询：
```sql
SELECT id,user_id,is_default
FROM addresses
WHERE user_id = 1 AND is_default = 1
LIMIT 1;
```

3. 商品违规分页：
```sql
SELECT id,product_id,status
FROM product_violations
WHERE product_id = 920005 AND status = 'active'
ORDER BY id DESC
LIMIT 0,20;
```

4. Outbox 待发送拉取：
```sql
SELECT id,status,next_retry_time
FROM message_outbox
WHERE status IN ('NEW','FAIL')
  AND (next_retry_time IS NULL OR next_retry_time <= NOW())
ORDER BY id ASC
LIMIT 200;
```

---

## 2. EXPLAIN 对照

| SQL | 优化前（key / rows / Extra） | 优化后（key / rows / Extra） | 结论 |
|---|---|---|---|
| 地址分页 | `fk_addresses_user / 2 / Using filesort` | `idx_addr_user_default_updated / 2 / Backward index scan; Using index` | 去除 filesort，改为索引有序扫描 |
| 默认地址查询 | `fk_addresses_user / 2 / Using where` | `idx_addr_user_default_updated / 1 / Using index` | 过滤更贴合，估算扫描行下降 |
| 商品违规分页 | `fk_pv_product / 1 / Using where; Backward index scan` | `idx_pv_product_status_id / 1 / Backward index scan; Using index`（`FORCE INDEX` 验证） | 新索引可被命中，具备覆盖能力 |
| Outbox 拉取 | `idx_status_time / 2 / Using where; Using index; Using filesort` | `idx_status_time / 2 / Using where; Using index; Using filesort` | 当前仍有 filesort，后续通过 SQL 形态优化收口 |

---

## 3. 量化结论

1. **排序开销**：地址分页从 `Using filesort` 降为索引顺序扫描。  
2. **扫描行估算**：默认地址查询 `rows` 从 `2 -> 1`（当前样本下）。  
3. **索引可用性**：商品违规分页新增复合索引已可命中（已用 `FORCE INDEX` 验证）。  
4. **高频链路现状**：Outbox 每天约 `17,280` 次拉取，虽无全表扫描，但仍存在 filesort，列为下一阶段优化重点。

---

## 4. EXPLAIN 执行命令（复用）

```sql
EXPLAIN SELECT id,user_id,is_default,updated_at
FROM addresses
WHERE user_id=1
ORDER BY is_default DESC, updated_at DESC
LIMIT 0,20;

EXPLAIN SELECT id,user_id,is_default
FROM addresses
WHERE user_id=1 AND is_default=1
LIMIT 1;

EXPLAIN SELECT id,product_id,status
FROM product_violations
FORCE INDEX (idx_pv_product_status_id)
WHERE product_id=920005 AND status='active'
ORDER BY id DESC
LIMIT 0,20;

EXPLAIN SELECT id,status,next_retry_time
FROM message_outbox
WHERE status IN ('NEW','FAIL')
  AND (next_retry_time IS NULL OR next_retry_time <= NOW())
ORDER BY id ASC
LIMIT 200;
```

---

（文件结束）
