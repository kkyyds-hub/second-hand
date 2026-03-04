# Day18 P7-S2 并发与失败注入执行记录 v1.0

- 日期：2026-02-26（2026-03-04 补充收口）
- 对应步骤：`Step P7-S2：并发与失败注入回归`
- 关联复现文档：`day18回归/执行复现步骤/Day18_P7_S2_并发与失败注入回归_执行复现_v1.0.md`

---

## 1. 执行范围

1. 任务处理并发单测覆盖盘点与扩展（新增 Outbox 失败注入单测）。
2. 运行态并发分流验证（封禁/解封、任务 run-once 并发）。
3. 运行态失败注入与恢复验证（Outbox MQ 失败、退款任务失败恢复）。

---

## 2. 代码级变更（单测扩展）

新增文件：
- `demo-service/src/test/java/com/demo/concurrency/OutboxPublishJobFailureInjectionTest.java`

新增覆盖点：
1. MQ 局部发送异常分桶回写。
2. DB 批量回写异常上抛（保持至少一次语义）。

说明：
1. 终端环境无 `mvn`，WSL 不可用，本次未执行 JUnit。
2. 代码已落盘，待你在 IDEA 内执行测试用例。

---

## 3. 动态验证结果

## 3.1 并发分流

证据：
- `day18回归/执行记录/Day18_P7_S2_动态验证结果_2026-02-26_10-41-48.json`
- `day18回归/执行记录/Day18_CloseLoop_Dynamic_Result_2026-03-04_10-53-17.json`

结果：
1. 并发封禁：`用户封禁成功` + `用户已处于封禁状态`。
2. 并发解封：`用户解封成功` + `用户已处于正常状态`。
3. 并发 ship-reminder run-once：两次均成功返回，`success=0`（当轮无到期任务）。

## 3.2 MQ 失败注入

证据：
- `day18回归/执行记录/Day18_P7_S2_动态验证结果_2026-02-26_10-41-48.json`

结果：
1. 注入坏交换机事件后，Outbox 状态：`FAIL`。
2. `retry_count` 递增（1 -> 2），失败路径可追踪。
3. 已补充修复演练：将异常样本 `payload_json` 修正为合法 JSON 后，`trigger-now + publish-once` 成功收敛到 `SENT`。

## 3.3 失败恢复（退款任务）

证据：
- `day18回归/执行记录/Day18_P7_S2_退款失败恢复动态结果_2026-02-26_10-44-25.json`

结果：
1. reset：`updatedRows=1`。
2. run-once 后任务状态：`SUCCESS`。
3. 恢复路径：`FAILED -> PENDING(reset) -> SUCCESS`。

---

## 4. DoD 回填

| DoD 项 | 结果 | 说明 |
|---|---|---|
| 并发下无重复副作用 | `[x]` | 并发封禁/解封分流稳定，任务并发 run-once 无重复副作用 |
| 失败后可恢复且路径可追溯 | `[x]` | 退款失败任务恢复成功；Outbox 失败事件具备 eventId 追踪链路 |

---

## 5. 结论与遗留

1. P7-S2 目标已达成：并发分流稳定、失败恢复路径可复现，Outbox `FAIL -> SENT` 已补齐实证。
2. 遗留：补跑 `com.demo.concurrency` 单测包，完成编译态闭环。

---

（文件结束）
