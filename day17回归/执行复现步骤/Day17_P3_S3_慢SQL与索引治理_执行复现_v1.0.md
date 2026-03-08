# Day17 Step P3-S3：慢 SQL 与索引治理执行复现步骤

- 文档版本：v1.0
- 日期：2026-02-24
- 目标：复现慢 SQL 分级、索引落地与 EXPLAIN 校验全过程

---

## 1. 前置条件

1. MySQL 可访问，目标库为 `secondhand2`。
2. 已导入测试数据（可使用：`_localhost__3_-2026_02_24_14_35_47-dump.sql`）。
3. 服务代码已同步到当前 Day17 分支。

---

## 2. 执行索引脚本

1. 执行文件：`day17回归/慢SQL与索引治理/Day17_P3_S3_索引脚本_v1.0.sql`
2. 期望结果：
   - 新增（或跳过）以下索引：
     - `idx_addr_user_default_updated`
     - `idx_pv_product_status_id`
     - `idx_outbox_status_retry_id`

---

## 3. EXPLAIN 校验

按 `day17回归/慢SQL与索引治理/Day17_P3_S3_EXPLAIN结论_v1.0.md` 中 SQL 逐条执行 EXPLAIN，重点看：

1. 地址分页 SQL 不再出现 `Using filesort`。  
2. 默认地址 SQL 的 `key` 命中 `idx_addr_user_default_updated`。  
3. 商品违规分页可命中 `idx_pv_product_status_id`（必要时 `FORCE INDEX` 验证）。  
4. Outbox 拉取链路确认“无全表扫描、仍有 filesort”的当前状态。

---

## 4. DoD 勾选

- [ ] 已完成慢 SQL 高/中/低分级  
- [ ] 已执行索引脚本并通过索引存在性校验  
- [ ] 关键 SQL 至少完成一次 EXPLAIN 校验  
- [ ] 已记录优化前后对照数据（rows / Extra / key）  

---

（文件结束）
