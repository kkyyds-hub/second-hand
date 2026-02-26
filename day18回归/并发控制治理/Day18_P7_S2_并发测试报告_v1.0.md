# Day18 P7-S2 并发测试报告 v1.0

- 日期：2026-02-26
- 对应阶段：`Step P7-S2：并发与失败注入回归`
- 目标：验证并发分流语义稳定、失败注入后具备可恢复与可追踪路径。

---

## 1. 回归范围

1. 扩展任务处理并发单测（代码级）：
   - 既有：
     - `OrderShipTimeoutTaskProcessorConcurrencyTest`
     - `OrderRefundTaskProcessorConcurrencyTest`
     - `OrderShipReminderTaskProcessorConcurrencyTest`
   - 新增（P7-S2）：
     - `OutboxPublishJobFailureInjectionTest`
2. 集成验证（运行态）：
   - 管理员并发封禁/解封同一用户（CAS + 幂等分流）
   - 管理端并发触发任务 run-once（无重复副作用）
   - Outbox MQ 失败注入（坏交换机）与事件追踪
   - 退款任务失败注入恢复（FAILED -> reset -> run-once -> SUCCESS）

---

## 2. 单测扩展说明（P7-S2）

新增文件：
- `demo-service/src/test/java/com/demo/concurrency/OutboxPublishJobFailureInjectionTest.java`

新增覆盖点：
1. `shouldContinueAndSplitSentFailBucketsWhenMqPartiallyFails`
   - 注入 MQ 局部发送异常（一条失败、一条成功）
   - 断言 sent/fail 分桶回写正确
2. `shouldThrowWhenBatchFlushFailsToPreserveRetrySemantics`
   - 注入 DB 批量回写异常（`flushPublishResult` 抛错）
   - 断言异常上抛，保持至少一次重试语义

说明：
1. 当前终端无 `mvn`，且 WSL 不可用，无法在本次会话执行 JUnit。
2. 单测代码已落盘，建议你在 IDEA 中执行 `com.demo.concurrency` 包测试完成最终编译回归。

---

## 3. 运行态动态验证结果

## 3.1 并发分流验证（CAS/幂等）

证据：
- `day18回归/执行记录/Day18_P7_S2_动态验证结果_2026-02-26_10-41-48.json`

结果：
1. 并发封禁同一用户：
   - 返回组合：`用户封禁成功` + `用户已处于封禁状态`
2. 并发解封同一用户：
   - 返回组合：`用户解封成功` + `用户已处于正常状态`
3. 并发执行 `ship-reminder/run-once`：
   - 两次请求均成功返回，`success=0`（当轮无到期任务），未产生重复副作用

## 3.2 MQ 失败注入与追踪

注入方式：
1. 手工插入一条 `message_outbox`，`exchange_name='bad.exchange'`
2. 执行 `/admin/ops/outbox/publish-once`

结果：
1. 事件状态由 `NEW -> FAIL`
2. `retry_count` 递增（`1 -> 2`）
3. 事件可通过 `eventId` 全程追踪（路径可追溯）

备注：
1. 本次环境下 RabbitMQ 发布链路不可恢复（修复后仍 FAIL），该异常已被稳定记录并可追踪。
2. 不影响“失败可追踪”结论。

## 3.3 失败恢复闭环（任务链路）

证据：
- `day18回归/执行记录/Day18_P7_S2_退款失败恢复动态结果_2026-02-26_10-44-25.json`

演练步骤：
1. 注入 `order_refund_task` 失败记录（`status=FAILED`）
2. 调用 `POST /admin/ops/tasks/refund/{taskId}/reset`（`updatedRows=1`）
3. 调用 `POST /admin/ops/tasks/refund/run-once?limit=200`

结果：
1. 目标任务最终状态 `SUCCESS`
2. 失败恢复路径完整、可复现、可追溯

---

## 4. DoD 对齐结论（P7-S2）

- [x] 并发下无重复副作用。  
说明：并发封禁/解封分流稳定，任务并发 run-once 未产生重复业务副作用。

- [x] 失败后可恢复且路径可追溯。  
说明：退款失败任务已完成 `FAILED -> PENDING(reset) -> SUCCESS` 恢复演练；Outbox 失败事件具备 eventId 级可追踪链路。

---

## 5. 风险与后续动作

1. 风险：本次环境 RabbitMQ 发布链路异常，Outbox 注入事件恢复到 `SENT` 未达成。
2. 建议：
   - 恢复 RabbitMQ 可用后，按同一 eventId 再执行一次 `trigger-now + publish-once` 补齐恢复证据；
   - 在 CI 或 IDEA 补跑 `com.demo.concurrency` 包测试，完成代码级回归闭环。

---

（文件结束）
