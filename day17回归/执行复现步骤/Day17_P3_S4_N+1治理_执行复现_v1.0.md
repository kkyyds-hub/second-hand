# Day17 Step P3-S4：N+1 治理执行复现步骤

- 文档版本：v1.0
- 日期：2026-02-24
- 目标：验证批处理链路已从“循环固定单查”切换为“批量查询 + Map 回填”，并验证 Outbox filesort 收口方案可执行

---

## 1. 前置条件

1. 服务可启动，数据库为 `secondhand2`。  
2. Dev 环境开启 mapper 日志（`com.demo.mapper=debug`）。  
3. 具备可触发数据：
   - `order_ship_timeout_task` 至少 10 条待处理；
   - `order_refund_task` 至少 10 条可执行；
   - `order_ship_reminder_task` 至少 10 条 RUNNING/PENDING 混合任务；
   - `message_outbox` 至少 100 条 `NEW/FAIL` 样本（更容易观察排序收口价值）。

---

## 2. N+1 治理复现

### 2.1 发货超时任务链路

1. 触发 `OrderShipTimeoutTaskJob` 一轮。  
2. 观察 SQL 日志：  
   - 应出现 1 次 `listDuePending`；  
   - 应出现 1 次 `selectOrderBasicByIds`；  
   - 不应再出现 N 次固定 `selectOrderBasicById`（并发兜底除外）。

### 2.2 退款任务链路

1. 触发 `OrderRefundTaskJob` 一轮。  
2. 观察 SQL 日志：  
   - 应出现 1 次 `listRunnable`；  
   - 应出现 1 次 `selectOrderBasicByIds`；  
   - 不应再出现 N 次固定 `selectOrderBasicById`（异常兜底除外）。

### 2.3 发货提醒任务链路

1. 触发 `OrderShipReminderTaskJob` 一轮。  
2. 观察 SQL 日志：  
   - 应出现 `markRunningBatch` + `listRunningByRound`；  
   - 应出现 1 次 `selectOrderForReminderByIds`；  
   - 不应再出现 N 次固定 `selectOrderForReminder`（回退场景除外）。

---

## 3. Outbox filesort 收口复现

1. 执行 EXPLAIN（使用 `MessageOutboxMapper.listPending` 同款 SQL）。  
2. 核验点：
   - SQL 已变为“分支 `LIMIT` + `UNION ALL` + 外层排序截断”；
   - 语义仍为 `NEW/FAIL + 到期 + id ASC + limit`；
   - 最终排序集合上界为 `2 * limit`（而非全量候选集合）。

---

## 4. DoD 勾选

- [ ] 高频批处理链路无明显固定 N+1 查询  
- [ ] 三个批处理链路均命中批量订单查询方法  
- [ ] Outbox `listPending` 已切换为收口版 SQL  
- [ ] 关键接口/任务响应时间有稳定改善（建议对比 3 轮平均值）  

---

（文件结束）
