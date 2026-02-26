# Day18 P1-S1 异常分流说明 v1.0

- 日期：2026-02-25  
- 目标：统一“异常 -> 回滚/幂等/重试/通知”分流口径，保证链路可解释。

---

## 1. 分流总规则

1. 事务内业务异常默认回滚：写链路统一 `rollbackFor = Exception.class`。  
2. 幂等命中不视为失败：返回稳定成功语义或“已处理”语义，不重复副作用。  
3. 临时故障走重试：任务/Outbox 通过 `retry_count + next_retry_time` 推进。  
4. 终态冲突走取消或跳过：状态已终态时不再重复推进。  
5. 外部副作用后置执行：`afterCommit` 成功提交后才发消息/通知。

---

## 2. 异常分流矩阵（关键链路）

| 链路/方法 | 典型异常场景 | 分流动作 | 是否回滚主事务 | 证据点 |
|---|---|---|---|---|
| `OrderServiceImpl.createOrder` | 参数/状态校验失败、SQL异常 | 抛业务异常或系统异常 | 是 | `@Transactional(rollbackFor=Exception.class)` |
| `OrderServiceImpl.createOrder` | MQ 发送失败（`safePublish`） | 仅记录日志，后续由 Outbox/任务兜底 | 否（主事务不受影响） | `safePublish` 说明 |
| `OrderServiceImpl.payOrder` | 重复支付/并发更新 rows=0 | 幂等命中或非法状态分流 | 否（按幂等/业务语义返回） | `rows==0` 分流分支 |
| `OrderServiceImpl.handlePaymentCallback` | 重复回调 | 幂等命中，返回稳定成功文案 | 否 | 回调幂等分支 |
| `OrderShipTimeoutTaskProcessor.processOne` | 订单终态/不存在 | 任务 `CANCELLED` | 否（单条任务收敛） | `markCancelled` |
| `OrderShipTimeoutTaskProcessor.processOne` | 状态异常/并发未命中 | 任务重试（`PENDING + next_retry_time`） | 否（单条任务重试） | `markRetry` |
| `OrderShipTimeoutTaskProcessor.processOne` | 关单成功后通知 | 事务提交后发送通知 | N/A | `registerAfterCommit` |
| `OrderRefundTaskProcessor.processOne` | 记账失败/临时异常 | 标记 `FAILED` + `next_retry_time` | 否（单条任务重试） | `markFail` |
| `OrderRefundTaskProcessor.processOne` | `markSuccess` rows=0 且最新已 `SUCCESS` | 幂等命中，直接返回 | 否 | 最新状态回查 |
| `OutboxPublishJob.publishOutboxMessagesOnce` | 单条发送异常 | 入 `failIds`，统一批量标记 FAIL | 否（批次继续） | `failIds` + `flushPublishResult` |
| `OutboxBatchStatusService.flushPublishResult` | 批量回写异常 | 整体事务回滚，下轮重试 | 是（回写事务） | `@Transactional(rollbackFor=Exception.class)` |
| MQ 消费链路（如 `OrderPaidConsumer`） | `DuplicateKeyException`（幂等日志冲突） | 直接 ACK（幂等命中） | 否 | `mq_consume_log` 唯一键语义 |

---

## 3. 回滚与通知顺序口径

1. 事务内先写业务数据，再注册 `afterCommit` 通知。  
2. 未提交前发生异常：事务回滚，`afterCommit` 不执行。  
3. 提交成功后通知失败：只记日志，不反向影响已提交业务数据。  
4. 发送可靠性由 Outbox/任务补偿链路兜底，不依赖单次实时发送成功。

---

## 4. 幂等、重试、终态三类分流定义

## 4.1 幂等命中

1. 特征：重复请求或并发后发现目标状态已达成。  
2. 处理：返回成功语义或“已处理”语义，不重复写入。  
3. 证据：唯一键冲突、`rows=0 + latestStatus` 回查命中。

## 4.2 可重试失败

1. 特征：临时故障、依赖波动、并发竞争未命中但状态未终态。  
2. 处理：保留任务记录，增加 `retry_count`，设置 `next_retry_time`。  
3. 证据：`markRetry`、`markFail`、Outbox `FAIL` 状态。

## 4.3 终态取消/跳过

1. 特征：订单已发货/已完成/已取消等终态，不应继续推进。  
2. 处理：任务标记 `CANCELLED` 或消费侧直接 ACK 丢弃。  
3. 证据：`markCancelled`、消费者的状态校验分支。

---

## 5. 统一日志建议（P1-S1）

1. 幂等命中：`action + idemKey + detail`。  
2. 重试推进：`taskId/eventId + retryCount + nextRetryTime + reason`。  
3. 事务后通知：`scene + commitState + sendResult`。  
4. 并发分流：`expectedStatus + latestStatus + rows`。

---

## 6. 对后续开发的约束

1. 新增跨表写方法必须显式声明事务边界。  
2. 新增外部副作用默认放入 `afterCommit`。  
3. 新增任务处理必须定义“幂等命中/可重试/终态取消”三分流。  
4. 新增链路若选择“不回滚主流程”，必须在文档注明兜底机制。

---

（文件结束）
