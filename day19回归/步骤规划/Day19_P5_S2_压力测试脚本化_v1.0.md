# Day19 P5-S2 压力测试脚本化 v1.0

- 日期：`2026-03-08`
- 对应范围：`Day19_Scope_Freeze_v1.0.md -> Phase 5 / Step P5-S2`
- 当前状态：`已执行完成（2026-03-08 17:59:31）`
- 执行脚本：`day19回归/执行复现步骤/Day19_P5_S2_压力测试脚本化_执行复现_v1.0.ps1`
- 动态证据：`day19回归/执行记录/Day19_P5_S2_压力测试结果_2026-03-08_17-59-31.json`

---

## 1. 目标与范围

本步骤目标：形成个人项目可持续复跑的轻量压测入口，输出可直接面试展示的结构化结果。

本次范围：

1. 固化 PowerShell 压测脚本，不引入重型压测平台。
2. 固化参数模板：`smoke / standard / stress-lite`
3. 固化结果结构：`P95 / 错误率 / 吞吐 / verdict`
4. 固化服务健康探测，服务不可达时立即停止

---

## 2. 方案选择

为什么选择 PowerShell 而不是 JMeter：

1. 仓库已有多份可复用 PowerShell 回归资产，复用成本最低。
2. 个人项目目标是“可长期复跑”，不是搭建重型平台。
3. 结果 JSON 更容易直接接入当前 Day19 证据目录。

---

## 3. 统一口径

1. 成功：`HTTP 2xx` 且 `Result.code = 1`
2. 错误率：`failedRequests / totalRequests`
3. 吞吐：`throughputRps = totalRequests / durationSec`
4. P95：聚合所有测量请求延迟后计算
5. 服务停止：健康探测失败后直接退出，不继续后续压测

---

## 4. 压测对象与参数

固定链路：

1. `POST /user/auth/login/password`
2. `GET /user/market/products?page=1&pageSize=20`
3. `POST /payment/callback`

固定模板：

| Profile | 并发 | 轮次 | 每轮请求 | 用途 |
|---|---:|---:|---:|---|
| `smoke` | 6 | 2 | 80 | 冒烟验证 |
| `standard` | 12 | 3 | 300 | Day19 默认展示口径 |
| `stress-lite` | 24 | 3 | 500 | 轻量放大并发 |

---

## 5. DoD 映射

| DoD | 对应设计动作 |
|---|---|
| 一条命令可复跑压测 | 固化 `-Profile` 脚本入口 |
| 结果格式可直接用于面试展示 | 输出 `interviewSummary[]` 与 `summary` |

---

## 6. 交付物

1. 设计：`day19回归/步骤规划/Day19_P5_S2_压力测试脚本化_v1.0.md`
2. 复现：`day19回归/执行复现步骤/Day19_P5_S2_压力测试脚本化_执行复现_v1.0.ps1`
3. 证据：`day19回归/执行记录/Day19_P5_S2_压力测试结果_2026-03-08_17-59-31.json`

---

（文件结束）
