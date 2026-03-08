# Day16 数据库改动清单（1A + 2A + 3A + 4B）

## 1. 设计选择落地结论
1. `1A`：新增独立举报工单表 `product_report_ticket`（不复用 `user_violations`）。
2. `2A`：`products.reason` 仅存文本原因，不新增 `products.reason_code`。
3. `3A`：Day16 新增表统一使用 `create_time/update_time` 命名。
4. `4B`：在数据库层增加状态 `CHECK` 约束（至少覆盖 `products.status` 与 `product_report_ticket.status`）。

## 2. 与当前库的差异（基于 2026-02-18 dump）
1. 已有：`products`、`product_violations`、`message_outbox`、`mq_consume_log`。
2. 缺失：`product_status_audit_log`、`product_report_ticket`。
3. `products.status` 目前只有注释约束，没有 `CHECK`。

## 3. 上线前必做校验 SQL
```sql
-- 3.1 products.status 先验数据校验（避免加 CHECK 失败）
SELECT status, COUNT(*) AS cnt
FROM products
GROUP BY status
HAVING status NOT IN ('under_review', 'on_sale', 'off_shelf', 'sold');
```

```sql
-- 3.2 如果结果非空，先治理脏数据再继续（示例）
-- UPDATE products SET status = 'off_shelf' WHERE status = 'xxx';
```

## 4. 结构变更 SQL（执行顺序）
```sql
-- 4.1 新增商品状态审计日志表（Day16 必需）
CREATE TABLE IF NOT EXISTS product_status_audit_log (
  id BIGINT NOT NULL AUTO_INCREMENT,
  product_id BIGINT NOT NULL,
  action VARCHAR(32) NOT NULL,
  operator_id BIGINT NOT NULL,
  operator_role VARCHAR(16) NOT NULL,
  before_status VARCHAR(32) NOT NULL,
  after_status VARCHAR(32) NOT NULL,
  reason_code VARCHAR(64) DEFAULT NULL,
  reason_text VARCHAR(255) DEFAULT NULL,
  extra_json JSON DEFAULT NULL,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_psal_product_time (product_id, create_time DESC),
  KEY idx_psal_operator_time (operator_id, create_time DESC),
  KEY idx_psal_action_time (action, create_time DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
COMMENT='商品状态审计日志';
```

```sql
-- 4.2 新增举报工单表（Day16 必需）
CREATE TABLE IF NOT EXISTS product_report_ticket (
  id BIGINT NOT NULL AUTO_INCREMENT,
  ticket_no VARCHAR(32) NOT NULL,
  product_id BIGINT NOT NULL,
  reporter_id BIGINT NOT NULL,
  report_type VARCHAR(64) NOT NULL,
  description VARCHAR(500) NOT NULL,
  evidence_urls TEXT DEFAULT NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
  resolver_id BIGINT DEFAULT NULL,
  resolve_action VARCHAR(32) DEFAULT NULL,
  resolve_remark VARCHAR(255) DEFAULT NULL,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_ticket_no (ticket_no),
  KEY idx_prt_product_status (product_id, status),
  KEY idx_prt_reporter_time (reporter_id, create_time DESC),
  CONSTRAINT chk_prt_status_day16 CHECK (status IN ('PENDING', 'RESOLVED_VALID', 'RESOLVED_INVALID')),
  CONSTRAINT chk_prt_action_day16 CHECK (resolve_action IS NULL OR resolve_action IN ('dismiss', 'force_off_shelf')),
  CONSTRAINT fk_prt_product FOREIGN KEY (product_id) REFERENCES products(id),
  CONSTRAINT fk_prt_reporter FOREIGN KEY (reporter_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
COMMENT='商品举报工单（Day16简版）';
```

```sql
-- 4.3 为 products.status 增加 DB 级强约束（4B）
ALTER TABLE products
  ADD CONSTRAINT chk_products_status_day16
  CHECK (status IN ('under_review', 'on_sale', 'off_shelf', 'sold'));
```

## 5. 与 2A 对应的字段策略
1. `products.reason` 同时承载审核驳回原因与强制下架原因文本。
2. `reason_code` 仅记录在 `product_status_audit_log.reason_code`。
3. 不新增 `products.reason_code`，避免历史查询和 DTO 改造面扩大。

## 6. 代码侧同步改动清单（不是本 SQL 文件执行内容）
1. 新增 `ProductStatusAuditLog` 实体、Mapper、Service，所有状态流转后强制写审计。
2. 新增 `ProductReportTicket` 实体、Mapper、Service，对应接口：
`POST /user/market/products/{productId}/report`
`PUT /admin/products/reports/{ticketNo}/resolve`
3. 实现 `PUT /admin/products/{productId}/force-off-shelf`，并按 2A 写 `products.reason`。
4. 事件与 Outbox 新增：
`PRODUCT_REVIEWED`
`PRODUCT_FORCE_OFF_SHELF`
`PRODUCT_REPORT_RESOLVED`
5. 修复 `ProductViolationMapper.insert` 参数绑定（单参数方法不要写 `#{violation.xxx}`，或显式 `@Param("violation")`）。

## 7. 回滚预案
```sql
ALTER TABLE products DROP CHECK chk_products_status_day16;
DROP TABLE IF EXISTS product_report_ticket;
DROP TABLE IF EXISTS product_status_audit_log;
```

## 8. 执行顺序建议
1. 先跑第 3 节校验 SQL。
2. 脏数据清理后执行第 4 节 DDL。
3. 再发代码版本（接口 + Mapper + 事件）。
4. 最后跑 Day16 回归脚本与验收。
