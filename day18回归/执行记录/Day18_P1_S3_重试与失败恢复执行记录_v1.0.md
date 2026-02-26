# Day18 P1-S3 重试与失败恢复执行记录 v1.0

- 日期：2026-02-25
- 关联复现文档：`day18回归/执行复现步骤/Day18_P1_S3_重试与失败恢复演练_执行复现_v1.0.md`
- 当前状态：已执行并完成回填。

---

## 1. 环境信息

1. 服务地址：`http://localhost:8080`
2. 数据库：`secondhand2`
3. 执行人：`Codex`
4. 执行时间：`2026-02-25 11:17:10 ~ 11:17:16`
5. 管理员账号：`13900000001`

说明：
1. 由于执行前测试库不存在 `Outbox NEW/FAIL`、`refund FAILED`、`ship-reminder FAILED` 样本，本次按演练标准对单条记录做“最小化种子注入”（仅修改状态），随后立即执行恢复流程并核验状态机。

---

## 2. 执行步骤记录

| 场景 | 输入（taskId/eventId） | 操作 | 结果摘要 | 是否通过 |
|---|---|---|---|---|
| 场景 A Outbox 恢复 | `eventId=8af946fd-618f-4c51-9704-a37a5b567b42` | `trigger-now + publish-once` | 前置 `FAIL|1|2026-02-25 11:27:10` -> 后置 `SENT|1|NULL`；`publish-once` 返回 `pulled=1,sent=1,failed=0` | `[x]` |
| 场景 B 发货超时恢复 | `taskId=19` | `trigger-now + run-once` | 前置 `PENDING|4|...|close_rows_0_status_paid` -> 后置 `PENDING|5|...|close_rows_0_status_paid`；状态未非法跃迁，符合“可重试失败继续重试” | `[x]` |
| 场景 C 退款任务恢复 | `taskId=2` | `reset + run-once` | 前置 `FAILED|0|...|manual_seed_p1s3` -> `reset` 成功 -> `run-once success=1` -> 后置 `SUCCESS|0|NULL|NULL` | `[x]` |
| 场景 D 发货提醒恢复 | `taskId=33` | `trigger-now + run-once` | 前置 `FAILED|0|...|manual_seed_p1s3` -> `trigger-now` 成功 -> `run-once success=1` -> 后置 `SUCCESS|0|...|NULL` | `[x]` |

---

## 3. SQL 核验结果

1. `message_outbox`：`SENT:37`（演练后无残留 FAIL）。
2. `order_ship_timeout_task`：`DONE:1, PENDING:9`（目标任务 `id=19` 保持 `PENDING` 并 `retry_count` 递增）。
3. `order_refund_task`：`SUCCESS:1`（目标任务 `id=2` 恢复成功）。
4. `order_ship_reminder_task`：`PENDING:11, SUCCESS:7`（目标任务 `id=33` 恢复成功）。

---

## 4. 异常与处置

1. 异常现象：执行前缺少部分失败样本（无 `Outbox FAIL/NEW`、无 `refund FAILED`、无 `reminder FAILED`）。
2. 根因判断：前序回归已将任务基本收敛为 `SENT/SUCCESS/PENDING`，无现成故障态可直接演练。
3. 处置动作：对单条记录做最小化状态注入后执行标准恢复流程（trigger/reset/run-once）。
4. 复盘结论：恢复接口与状态机约束生效，未出现非法状态逆向迁移。

---

## 5. DoD 勾选（回填区）

- [x] 重试行为与状态机一致。  
- [x] 运维可按文档完成一次失败恢复演练。  
- [x] 已形成可追踪的执行证据（接口返回 + SQL 结果）。  

---

（文件结束）
