# Day19 P2-S2 性能预算与发布门槛 v1.0

- 日期：`2026-03-06`
- 对应范围：`Day19_Scope_Freeze_v1.0.md -> Phase 2 / Step P2-S2`
- 当前状态：`已落地（文档+脚本）；待执行证据回填`
- 基线来源：`day19回归/执行记录/Day19_P2_S1_动态结果_2026-03-06_09-54-21.json`

---

## 1. 目标与原则

本步骤目标：把 `P2-S1` 的“性能基线数据”升级为“发布可执行门槛”，避免发布决策依赖主观判断。

原则：

1. 预算项必须可由现有脚本输出字段直接采集。
2. 门槛分为“硬门槛（阻断发布）”与“观察门槛（风险放行）”。
3. 每次改动后至少执行一次性能回归，形成结构化证据。

---

## 2. 预算项定义（可采集）

## 2.1 硬门槛（Release Gate）

| 链路 | 预算项 | 门槛 | 采集路径（P2-S1 JSON） | 说明 |
|---|---|---:|---|---|
| 登录 `login_password` | P95 | `<= 500ms` | `scenarios[name=login_password].baseline.p95Ms` | 登录链路允许较高尾延迟 |
| 登录 `login_password` | 错误率 | `<= 1.00%` | `scenarios[name=login_password].baseline.errorRatePct` | 业务成功口径：HTTP2xx 且 code=1 |
| 登录 `login_password` | 吞吐 | `>= 30 rps` | `scenarios[name=login_password].baseline.throughputRps` | 防止明显性能崩塌 |
| 商品列表 `market_product_list` | P95 | `<= 80ms` | `scenarios[name=market_product_list].baseline.p95Ms` | 列表查询应保持低延迟 |
| 商品列表 `market_product_list` | 错误率 | `<= 1.00%` | `scenarios[name=market_product_list].baseline.errorRatePct` | 前台高频链路，不容错误放大 |
| 商品列表 `market_product_list` | 吞吐 | `>= 40 rps` | `scenarios[name=market_product_list].baseline.throughputRps` | 保障容量 |
| 支付回调 `payment_callback` | P95 | `<= 120ms` | `scenarios[name=payment_callback].baseline.p95Ms` | 回调链路保持稳定即可 |
| 支付回调 `payment_callback` | 错误率 | `<= 1.00%` | `scenarios[name=payment_callback].baseline.errorRatePct` | 回调失败风险高 |
| 支付回调 `payment_callback` | 吞吐 | `>= 35 rps` | `scenarios[name=payment_callback].baseline.throughputRps` | 防止回调堆积 |

## 2.2 样本有效性门槛

| 项目 | 门槛 | 采集路径 | 说明 |
|---|---:|---|---|
| 单链路总样本 | `>= 600` | `scenarios[*].baseline.totalRequests` | 样本不足不允许判定通过 |
| 轮次数 | `>= 3` | `config.rounds` | 保证分位数稳定性 |

---

## 3. 退化判定规则

## 3.1 相对退化（相对 P2-S1 基线）

| 指标 | 判定公式 | 退化阈值 | 采集路径 |
|---|---|---:|---|
| P95 退化比例 | `(currentP95 - baselineP95) / baselineP95 * 100` | `> 20%` 记为退化 | `scenarios[*].baseline.p95Ms` |
| 吞吐下降比例 | `(baselineRps - currentRps) / baselineRps * 100` | `> 20%` 记为退化 | `scenarios[*].baseline.throughputRps` |
| 错误率恶化（百分点） | `currentErrorPct - baselineErrorPct` | `> 0.50` 记为退化 | `scenarios[*].baseline.errorRatePct` |

说明：`baseline` 固定为 `2026-03-06 09:54:21` 的 `P2-S1` 结果，`current` 为本次回归结果。

## 3.2 严重级别定义

| 级别 | 条件 | 发布决策 |
|---|---|---|
| `BLOCKER` | 任一链路错误率 `> 1%`，或 P95 超预算 `20%+`，或吞吐低于预算 `70%`，或样本不足 | 阻断发布，建议回滚 |
| `MAJOR` | 任一硬门槛失败但未达到 BLOCKER | 暂缓发布，修复后重跑 |
| `MINOR` | 硬门槛通过，但命中相对退化阈值 | 可带风险放行，需观察 |
| `PASS` | 硬门槛与相对退化均通过 | 可发布 |

---

## 4. 回滚建议矩阵

| 场景 | 触发条件 | 立即动作 | 回滚建议 |
|---|---|---|---|
| 回调风险 | `payment_callback` 错误率 `>1%` | 停止发布，保留证据 | 立即回滚到上一稳定版本 |
| 登录尾延迟异常 | `login_password` P95 `>500ms` 且持续 3 轮 | 暂停发布，排查鉴权链路 | 若 `>600ms` 且无快速修复，回滚 |
| 列表容量退化 | `market_product_list` 吞吐 `<40 rps` | 排查缓存/SQL | 若下降 `>30%` 且不可快速恢复，回滚 |
| 全链路轻微退化 | 仅 `MINOR`，硬门槛全通过 | 记录风险，增强监控 | 不立即回滚，要求下一版本修复 |

---

## 5. 发布前性能检查清单

1. 已执行 `P2-S1` 回归脚本并生成当次 JSON 证据。
2. 三条链路样本均满足 `>= 600`。
3. 三条链路错误率均 `<= 1%`。
4. 三条链路 P95 均未超过各自预算。
5. 三条链路吞吐均未低于各自预算。
6. 已完成相对退化计算（P95/吞吐/错误率）。
7. 已给出发布决策：`PASS / MINOR / MAJOR / BLOCKER`。
8. 如非 `PASS`，已填写处置与回滚建议。
9. 证据已归档到 `day19回归/执行记录/`。

---

## 6. 执行方式（自动化）

推荐脚本（本步骤新增）：

`day19回归/执行复现步骤/Day19_P2_S2_性能预算与发布门槛_执行复现_v1.0.ps1`

示例：

```powershell
Set-Location 'c:\Users\kk\Desktop\demo'
powershell -NoProfile -ExecutionPolicy Bypass -File 'day19回归/执行复现步骤/Day19_P2_S2_性能预算与发布门槛_执行复现_v1.0.ps1' `
  -RunRegression
```

脚本输出：

1. `day19回归/执行记录/Day19_P2_S2_动态结果_*.json`
2. 总体门槛判定（`PASS/MINOR/MAJOR/BLOCKER`）
3. 回滚建议摘要

---

## 7. DoD 映射

| DoD | 验收动作 | 判定标准 |
|---|---|---|
| 每个预算都可由现有脚本采集 | 检查第 2 章“采集路径”并在 JSON 中抽样验证 | 所有预算项均有字段路径 |
| 每次改动可执行一次性能回归 | 执行 `P2-S2` 脚本并触发 `P2-S1` 回归 | 至少产出 1 份 `P2-S2` 动态结果 |

---

## 8. 风险与边界

1. 当前商品列表基线样本主要来自“空列表路径”，若业务数据量显著增大，应补充“有数据页”预算。
2. 支付回调基线为“幂等路径”，后续应补“首次成功回调”专项预算。
3. 本步骤不引入新压测平台，完全依赖现有脚本与 JSON 结构。

---

（文件结束）
