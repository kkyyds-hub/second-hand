# Day17 P3-S4 Outbox `listPending` filesort 收口说明 v1.0

- 日期：2026-02-24
- 对象：`MessageOutboxMapper.listPending`
- 目标：在不改变业务语义的前提下，收口排序开销放大风险

---

## 1. 改造前后 SQL 形态

### 改造前（单路排序）

```sql
SELECT ...
FROM message_outbox
WHERE status IN ('NEW','FAIL')
  AND (next_retry_time IS NULL OR next_retry_time <= NOW())
ORDER BY id ASC
LIMIT #{limit}
```

### 改造后（分支限流 + 合并）

```sql
SELECT ...
FROM (
  (SELECT ... FROM message_outbox WHERE status='NEW'  ... ORDER BY id ASC LIMIT #{limit})
  UNION ALL
  (SELECT ... FROM message_outbox WHERE status='FAIL' ... ORDER BY id ASC LIMIT #{limit})
) t
ORDER BY t.id ASC
LIMIT #{limit}
```

---

## 2. 语义一致性说明（保语义）

保持不变：
1. 过滤条件：仅 `NEW/FAIL` 且到期可重试；  
2. 返回顺序：全局 `id ASC`；  
3. 返回数量：最多 `limit` 条。  

收口点：
- 最终参与外层排序的数据量上界从“全候选集”收口到“最多 `2 * limit`”。

---

## 3. EXPLAIN 对照（`limit=50`）

### 3.1 改造前

- `key = idx_status_time`
- `Extra = Using index condition; Using filesort`

### 3.2 改造后

- 外层 `derived`：`Extra = Using filesort`
- 两个分支：`key = idx_status_time`，`Extra = Using index condition; Using filesort`

结论：
- 在当前数据规模下，`Using filesort` 标记仍可能存在；
- 但排序集合已被上界约束，避免随着待发送积压量线性放大。

---

## 4. 一致性校验结果

使用 `limit=50` 对“改造前结果集”与“改造后结果集”做双向差集校验：

- `old - new`：0 行  
- `new - old`：0 行  

说明：当前样本下，两种写法结果一致。

---

## 5. 下一步（可选）

若后续仍需继续压缩排序成本，可评估：
1. 将 `next_retry_time IS NULL OR <= NOW()` 拆分成双分支，进一步提高索引可用性；  
2. 在业务允许时引入“按时间 + id”排序口径（需先确认出队契约）。  

---

（文件结束）
