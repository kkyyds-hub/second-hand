# Day17 P3-S4 N+1 查询治理记录 v1.0

- 日期：2026-02-24
- 目标：治理批处理链路中“循环内固定查订单”导致的 N+1 放大，并同步完成 Outbox `listPending` 的 filesort 收口
- 范围：`OrderShipReminder`、`OrderShipTimeout`、`OrderRefund`、`MessageOutbox`

---

## 1. 问题定义与治理口径

### 1.1 N+1 判定口径

- 同一批任务在循环内每条都执行一次固定查询（如 `selectById`）；
- 批大小为 `N` 时，SQL 往返呈 `1 + N` 线性增长；
- 数据量增大后，批处理耗时和数据库压力同步线性放大。

### 1.2 本次治理原则

1. **接口语义不变**：状态流转、事务边界、异常分支行为保持一致。  
2. **先去固定查询**：只去掉“每条必发生”的单查。  
3. **保留安全兜底**：并发冲突二次确认查询继续保留（非固定触发，不视为 N+1 主因）。  

---

## 2. 改造清单（代码级）

### 2.1 Mapper 批量查询能力补齐

- `demo-service/src/main/java/com/demo/mapper/OrderMapper.java`
  - 新增：`selectOrderBasicByIds(List<Long> orderIds)`
  - 新增：`selectOrderForReminderByIds(List<Long> orderIds)`
- `demo-service/src/main/resources/mapper/OrderMapper.xml`
  - 新增对应 `IN (...)` 批量查询 SQL（字段口径与单条查询一致）

### 2.2 Service 层从“循环单查”改为“批量预加载 + Map 回填”

- `demo-service/src/main/java/com/demo/service/serviceimpl/OrderShipTimeoutServiceImpl.java`
  - 新增 `loadOrderMap(...)`，批量查订单后传入 Processor
- `demo-service/src/main/java/com/demo/service/serviceimpl/OrderRefundTaskServiceImpl.java`
  - 新增 `loadOrderMap(...)`，批量查订单后传入 Processor
- `demo-service/src/main/java/com/demo/service/serviceimpl/OrderShipReminderTaskServiceImpl.java`
  - 新增 `loadReminderOrderMap(...)`，批量查订单后传入 Processor

### 2.3 Processor 兼容预加载订单参数

- `demo-service/src/main/java/com/demo/service/serviceimpl/OrderShipTimeoutTaskProcessor.java`
- `demo-service/src/main/java/com/demo/service/serviceimpl/OrderRefundTaskProcessor.java`
- `demo-service/src/main/java/com/demo/service/serviceimpl/OrderShipReminderTaskProcessor.java`

改造点：
- 保留原 `processOne(task)`，新增 `processOne(task, preloadedOrder)`；
- 优先使用 `preloadedOrder`，为空时回退原单查；
- 并发兜底二次查询逻辑保持不变。

---

## 3. N+1 治理前后对照

| 场景 | 治理前 | 治理后 | 收益 |
|---|---|---|---|
| 发货超时任务批处理 | `1（拉任务） + N（逐条查订单） + ...` | `1（拉任务） + 1（批量查订单） + ...` | 固定查询从 `N` 降为 `1` |
| 退款任务批处理 | `1（拉任务） + N（逐条查订单） + ...` | `1 + 1 + ...` | 固定查询从 `N` 降为 `1` |
| 发货提醒任务批处理 | `2（抢占+拉任务） + N（逐条查订单） + ...` | `2 + 1 + ...` | 固定查询从 `N` 降为 `1` |

说明：
- `...` 代表状态更新与通知等业务 SQL，本次未改语义。
- 并发冲突下的二次确认查询保留，不属于固定 N+1 模式。

---

## 4. 附加优化：Outbox `listPending` filesort 收口（保语义）

### 4.1 改造文件

- `demo-service/src/main/resources/mapper/MessageOutboxMapper.xml`
- `demo-service/src/main/java/com/demo/mapper/MessageOutboxMapper.java`

### 4.2 改造策略

- 改造前：`status IN ('NEW','FAIL') + 全量候选排序 + LIMIT`
- 改造后：按状态分支各取前 `limit`，`UNION ALL` 合并后再按 `id ASC` 取前 `limit`

### 4.3 为什么是“保语义”

保持不变：
1. 仍只处理 `NEW/FAIL` 且到期记录；  
2. 仍按全局 `id ASC` 出队；  
3. 仍只返回 `limit` 条。  

变化点：
- 最终排序集合从“潜在大集合”收口到“最多 `2 * limit`”的小集合，降低排序放大风险。

---

## 5. 保留场景与理由（不做过度改造）

1. **Processor 并发兜底二次查询保留**  
   - 理由：用于 `rows=0` 后状态再确认，保障一致性与幂等。
2. **单条失败补偿链路保留单查回退**  
   - 理由：调用方可能未走批处理预加载；回退逻辑确保功能完整。

---

## 6. DoD 对齐结论

- [x] 高频批处理链路不再存在明显“循环固定单查订单”模式  
- [x] 已完成批量查询 + Map 回填改造并保留一致性兜底  
- [x] Outbox `listPending` 完成 filesort 收口（保语义）  
- [x] 已形成可复现文档（见执行复现步骤）  

---

（文件结束）
