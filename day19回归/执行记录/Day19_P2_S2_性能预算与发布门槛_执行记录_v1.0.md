# Day19 P2-S2 性能预算与发布门槛 执行记录 v1.0

- 日期：`2026-03-06`
- 执行人：`kk + Codex`
- 服务地址：`http://localhost:8080`
- 关联文档：
  - 规划：`day19回归/步骤规划/Day19_P2_S2_性能预算与发布门槛_v1.0.md`
  - 复现：`day19回归/执行复现步骤/Day19_P2_S2_性能预算与发布门槛_执行复现_v1.0.ps1`
- 当前状态：`已完成`

---

## 1. 本轮执行目标

1. 将 P2-S1 基线数据升级为可发布判定门槛（绝对预算 + 相对退化）。
2. 在本次执行中触发一次完整性能回归并生成门槛判定结果。
3. 产出可追溯的发布结论与回滚建议证据文件。

---

## 2. 动态证据索引

1. P2-S1 候选回归结果：`day19回归/执行记录/Day19_P2_S1_动态结果_2026-03-06_10-47-37.json`
2. P2-S2 门槛判定结果：`day19回归/执行记录/Day19_P2_S2_动态结果_2026-03-06_10-47-37.json`

---

## 3. 预算判定结果（按链路）

| 链路 | 绝对门槛判定 | 相对退化判定 | 关键数据（baseline -> current） | 结论 |
|---|---|---|---|---|
| `login_password` | 通过 | 通过 | P95: `449.26 -> 423.53`；错误率: `0 -> 0`；RPS: `38.86 -> 31.65`（下降 `18.55%`） | `PASS` |
| `market_product_list` | 通过 | **未通过** | P95: `13.46 -> 12.69`；错误率: `0 -> 0`；RPS: `54.55 -> 42.22`（下降 `22.60%`） | `MINOR` |
| `payment_callback` | 通过 | **未通过** | P95: `17.89 -> 17.20`；错误率: `0 -> 0`；RPS: `48.54 -> 36.11`（下降 `25.61%`） | `MINOR` |

说明：

1. 三条链路硬门槛全部通过（P95、错误率、吞吐、样本量）。
2. 商品列表与支付回调命中“相对吞吐退化 > 20%”，因此进入 `MINOR`。

---

## 4. 发布结论与回滚建议

- 总体结论：`MINOR`
- 发布动作：`RELEASE_WITH_RISK`
- 建议动作：
  1. 在发布说明中记录性能风险。
  2. 对 `market_product_list` 与 `payment_callback` 设临时重点观察。
  3. 在下一迭代完成吞吐回补优化并重跑 P2-S1/P2-S2。

---

## 5. 发布前检查清单执行结果

| 检查项 | 结果 |
|---|---|
| `check_01_regression_executed_or_candidate_present` | `true` |
| `check_02_rounds_ge_3` | `true` |
| `check_03_each_scenario_sample_ge_600` | `true` |
| `check_04_each_scenario_error_le_1pct` | `true` |
| `check_05_each_scenario_p95_within_budget` | `true` |
| `check_06_each_scenario_rps_within_budget` | `true` |
| `check_07_relative_degradation_evaluated` | `true` |
| `check_08_release_decision_generated` | `true` |

---

## 6. DoD 验收

DoD-1：每个预算都可由现有脚本采集

- [x] 通过
- [ ] 不通过（差距：`______`）

DoD-2：每次改动可执行一次性能回归

- [x] 通过（本次使用 `-RunRegression`，已生成新的 P2-S1 候选结果）
- [ ] 不通过（差距：`______`）

最终结论：

- [x] P2-S2 完成
- [ ] P2-S2 未完成（需补动作：`______`）

---

## 7. 风险与后续动作

1. 当前风险：`market_product_list`、`payment_callback` 吞吐相对基线分别下降 `22.60%`、`25.61%`。
2. 处置建议：在 Phase 3/4 的缓存与异步链路步骤中优先回补吞吐，再执行一次 P2-S1/P2-S2 复核。
3. 证据要求：后续复核继续沿用本步骤 JSON 结构，保证“同口径可对比”。

---

（文件结束）
