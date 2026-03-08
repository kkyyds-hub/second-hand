# Day17 P3-S3 慢 SQL 与索引治理：SQL 优化清单 v1.0

- 日期：2026-02-24
- 目标：将 SQL 优化从“经验判断”收敛为“样本分级 + EXPLAIN 结论 + 索引脚本”
- 范围：`addresses`、`product_violations`、`message_outbox`、任务调度相关查询

---

## 1. 样本来源与分级口径

### 1.1 样本来源

1. Mapper SQL：`demo-service/src/main/resources/mapper/**/*.xml`  
2. 调度高频链路（调用次数/天）：  
   - `OutboxPublishJob`：每 5 秒执行，约 `17,280` 次/天  
   - `OutboxMonitorJob`：每 30 秒执行，约 `2,880` 次/天  
   - `OrderShipTimeoutTaskJob`：每 60 秒执行，约 `1,440` 次/天  
   - `OrderRefundTaskJob`：每 60 秒执行，约 `1,440` 次/天  
   - `OrderShipReminderTaskJob`：每 60 秒执行，约 `1,440` 次/天  
3. 数据库样本：`_localhost__3_-2026_02_24_14_35_47-dump.sql` + 本地库 `secondhand2` EXPLAIN

### 1.2 风险分级规则

- **高风险**：高频（调度/核心列表）且存在 `filesort`/潜在大范围扫描。
- **中风险**：中频或有索引但与过滤/排序不完全贴合。
- **低风险**：已有稳定复合索引，EXPLAIN 无明显风险信号。

---

## 2. 慢 SQL 样本分级清单

| 编号 | SQL / Mapper | 调用链路 | 频次估算 | 现状 | 风险 |
|---|---|---|---:|---|---|
| H-1 | `MessageOutboxMapper.listPending` | `OutboxPublishJob` | 17,280/天 | 使用 `idx_status_time`，但 `ORDER BY id` 仍出现 `Using filesort` | 高 |
| H-2 | `AddressMapper.findPageByUserId` | 用户地址列表 | 接口驱动 | 仅 `user_id` 单列索引，排序走 filesort | 高 |
| M-1 | `AddressMapper.findDefaultByUserId` | 下单地址兜底 | 接口驱动 | 单列 `user_id` 可用，但 `user_id + is_default` 可进一步收敛扫描 | 中 |
| M-2 | `ProductViolationMapper.findByProductIdPage` | 管理端违规记录分页 | 接口驱动 | 仅 `product_id` 单列索引，复合过滤+排序能力不足 | 中 |
| M-3 | `OrderRefundTaskMapper.listRunnable` | `OrderRefundTaskJob` | 1,440/天 | 现有索引可用，但排序与过滤并非同一最优前缀 | 中 |
| L-1 | `OrderShipTimeoutTaskMapper.listDuePending` | `OrderShipTimeoutTaskJob` | 1,440/天 | 有 `status + deadline_time` 与 `status + next_retry_time` | 低 |
| L-2 | `OrderShipReminderTaskMapper.listStaleRunning` | `OrderShipReminderTaskJob` | 1,440/天 | 有 `status + running_at`，覆盖核心过滤 | 低 |
| L-3 | `UserCreditLogMapper.listByUserIdPage` | 用户/管理端积分分页 | 接口驱动 | 有 `user_id + create_time` 复合索引 | 低 |

---

## 3. 本次索引优化动作

| 动作 | 目标查询 | 变更 | 目的 |
|---|---|---|---|
| A-1 | `addresses` 用户地址分页/默认地址 | 新增 `idx_addr_user_default_updated(user_id,is_default,updated_at,id)` | 消除分页排序 filesort，收敛默认地址扫描 |
| A-2 | `product_violations` 按商品分页 | 新增 `idx_pv_product_status_id(product_id,status,id)` | 让过滤与排序共享索引路径 |
| A-3 | `message_outbox` 待发送拉取 | 新增 `idx_outbox_status_retry_id(status,next_retry_time,id)` | 降低高频扫描成本，给后续 SQL 重写预留索引基础 |

对应脚本：`day17回归/慢SQL与索引治理/Day17_P3_S3_索引脚本_v1.0.sql`

---

## 4. 保留问题与下一步

1. `listPending` 仍有 `Using filesort`（由 `status IN (...) + next_retry_time 条件 + ORDER BY id` 组合导致）。  
2. 该问题本阶段不改业务语义（保持按 `id` 出队），下一步在 P3-S4 评估两种路径：  
   - 路径 A：保留语义，改为“两段拉取 + 合并”减少单次 filesort 数据量；  
   - 路径 B：改为 `ORDER BY next_retry_time, id`（需业务确认出队顺序契约）。

---

## 5. DoD 对齐结论（P3-S3）

- [x] 已形成慢 SQL 样本分级（高/中/低）
- [x] 已输出索引脚本并落地关键复合索引
- [x] 已给出每条关键 SQL 的 EXPLAIN 结论（见 EXPLAIN 文档）
- [x] 关键查询未出现明显全表扫描风险（当前样本均走索引路径）

---

（文件结束）
