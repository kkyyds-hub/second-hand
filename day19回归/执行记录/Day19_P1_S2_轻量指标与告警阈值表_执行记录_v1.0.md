# Day19 P1-S2 轻量指标与告警阈值表 执行记录 v1.0

- 日期：`2026-03-06`
- 执行人：`kk + Codex`
- 服务地址：`http://localhost:8080`
- 关联文档：
  - 规划：`day19回归/步骤规划/Day19_P1_S2_轻量指标与告警阈值表_v1.0.md`
  - 复现：`day19回归/执行复现步骤/Day19_P1_S2_轻量指标与告警阈值表_执行复现_v1.0.md`
- 当前状态：`已完成`

---

## 1. 本轮执行目标

1. 固化 Outbox/任务轻量指标快照口径。
2. 验证 warning/error 阈值可计算、可判定。
3. 验证“积压、失败重试、成功率”三类处置流程可通过运维接口实操。
4. 产出可追溯的动态证据 JSON。

---

## 2. 动态结果索引

1. `day19回归/执行记录/Day19_P1_S2_Dynamic_Result_2026-03-06_10-15-07.json`

---

## 3. 关键执行结果

| 项目 | 结果 | 备注 |
|---|---|---|
| 管理员登录 | `[x]` | `/admin/employee/login` 返回 `code=1` |
| Outbox metrics 可用 | `[x]` | `/admin/ops/outbox/metrics` 返回 `code=1` |
| Outbox publish-once 可用 | `[x]` | `/admin/ops/outbox/publish-once` 返回 `code=1` |
| ship-timeout run-once 可用 | `[x]` | `/admin/ops/tasks/ship-timeout/run-once` 返回 `code=1` |
| refund run-once 可用 | `[x]` | `/admin/ops/tasks/refund/run-once` 返回 `code=1` |
| ship-reminder run-once 可用 | `[x]` | `/admin/ops/tasks/ship-reminder/run-once` 返回 `code=1` |
| SQL 快照可执行 | `[x]` | mysql CLI 可执行并返回数值 |

---

## 4. 阈值判定汇总

| 告警ID | latest | level | flow | 备注 |
|---|---:|---|---|---|
| BKL-OUTBOX | `0` | `NORMAL` | FLOW-A | 无积压 |
| BKL-ST | `0` | `NORMAL` | FLOW-A | 无 ship-timeout 可执行积压 |
| BKL-RF | `0` | `NORMAL` | FLOW-A | 无 refund 可执行积压 |
| BKL-SR | `0` | `NORMAL` | FLOW-A | 无 ship-reminder 可执行积压 |
| RETRY-OUTBOX-CNT | `0` | `NORMAL` | FLOW-B | 无 outbox fail 样本 |
| RETRY-OUTBOX-SUM | `0` | `NORMAL` | FLOW-B | 无 outbox retrySum 放大 |
| RETRY-TASK-HIGH | `2` | `NORMAL` | FLOW-B | 高重试样本存在但未达 warning |
| RETRY-TASK-SEV | `1` | `WARNING` | FLOW-B | 严重重试样本触发 warning |
| SR-OUTBOX | `null` | `NO_SAMPLE` | FLOW-C | `publish-once` 本轮 `pulled=0` |
| SR-TASK | `null` | `NO_SAMPLE` | FLOW-C | `run-once` 本轮可执行分母为 0 |

---

## 5. 三类处置流程执行结果

| 流程 | 是否执行 | 核心动作 | 结果 |
|---|---|---|---|
| FLOW-A 积压处置 | `[x]` | 连续 3 轮 `publish-once + 三类 run-once` | 接口全部成功，积压保持 `NORMAL` |
| FLOW-B 失败重试处置 | `[x]` | 命中 `RETRY-TASK-SEV` 后，对 `ship-timeout taskId=19` 执行 `trigger-now`，随后 `run-once` | `trigger-now updatedRows=1`，链路可实操复现；样本仍会因业务条件进入重试 |
| FLOW-C 成功率处置 | `[x]`（入口验证） | 连续 3 轮计算成功率 | 本轮 `NO_SAMPLE`（无可处理样本），未进入低成功率分支 |

补充证据（FLOW-B 手工动作）：

1. `POST /admin/ops/tasks/ship-timeout/19/trigger-now` -> `updatedRows=1`
2. `POST /admin/ops/tasks/ship-timeout/run-once?limit=200` -> `success=0`
3. SQL 复核显示样本仍处于 `PENDING` 且 `retry_count` 增长（业务状态分流导致）

---

## 6. DoD 验收

DoD-1：每个阈值都有具体处置步骤

- [x] 通过
- [ ] 不通过（差距：`______`）

DoD-2：处置步骤可通过运维接口实操复现

- [x] 通过
- [ ] 不通过（差距：`______`）

最终结论：

- [x] P1-S2 完成
- [ ] P1-S2 未完成（需补动作：`______`）

---

## 7. 风险与后续动作

1. 风险：存在历史高重试任务样本（如 `ship-timeout taskId=19, retry_count=784`）。
2. 影响：在业务条件未满足时，补偿后仍可能继续重试，导致 `RETRY-TASK-SEV` 长期保持 warning。
3. 下一步建议：
   - 在 `P4-S2` 增加“高重试僵尸样本清单”与人工闭环策略（状态核对 + 补偿 + 结果归档）。
   - 在 `P1-S2` 的执行脚本后续版本里增加“自动抓取 topN 高重试任务并生成补偿建议”。

---

（文件结束）
