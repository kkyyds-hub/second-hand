# Day19 P5-S2 压力测试脚本化（轻量版）执行复现 v1.0

- 日期：`2026-03-08`
- 对应范围：`day19回归/Day19_Scope_Freeze_v1.0.md` -> `Phase 5 / Step P5-S2`
- 脚本入口：`day19回归/执行复现步骤/Day19_P5_S2_压力测试脚本化_执行复现_v1.0.ps1`
- 输出目录：`day19回归/执行记录`

---

## 1. 目标

本步骤把 Day19 的轻量压测能力固化为“一条命令可复跑”的入口，并把结果统一收敛为适合面试展示的结构化 JSON。

固定输出三类指标：

1. `P95`：响应时间尾延迟
2. `错误率`：失败请求 / 总请求
3. `吞吐`：`RPS = 总请求 / 总耗时`

---

## 2. 前置条件

1. 服务已启动：`http://localhost:8080`
2. 可用测试账号：`13800000001 / 123456`
3. 可用 `mysql` CLI：默认连接 `localhost:3306 / secondhand2 / root / 1234`
4. 默认会自动创建一条新的 `pending` 订单作为 `payment_callback` 压测样本
5. 若服务中途停止，脚本会立刻终止，不会继续压测

---

## 3. 一条命令复跑

标准档：

`powershell -ExecutionPolicy Bypass -File "day19回归/执行复现步骤/Day19_P5_S2_压力测试脚本化_执行复现_v1.0.ps1" -Profile standard`

烟雾档：

`powershell -ExecutionPolicy Bypass -File "day19回归/执行复现步骤/Day19_P5_S2_压力测试脚本化_执行复现_v1.0.ps1" -Profile smoke`

轻压档：

`powershell -ExecutionPolicy Bypass -File "day19回归/执行复现步骤/Day19_P5_S2_压力测试脚本化_执行复现_v1.0.ps1" -Profile stress-lite`

---

## 4. 参数模板

| Profile | 并发 | 轮次 | 预热请求 | 每轮测量请求 | 超时(s) | 说明 |
|---|---:|---:|---:|---:|---:|---|
| `smoke` | 6 | 2 | 20 | 80 | 10 | 快速验证脚本与服务是否稳定 |
| `standard` | 12 | 3 | 60 | 300 | 15 | Day19 默认复跑档，适合作为展示口径 |
| `stress-lite` | 24 | 3 | 80 | 500 | 20 | 轻量放大并发，用于观察尾延迟与错误率变化 |

说明：

1. `Profile` 可直接切换模板
2. `Rounds / WarmupRequests / MeasuredRequests / Concurrency / TimeoutSec` 可按需覆盖模板默认值
3. 若显式传入 `OrderNo / OrderAmount`，脚本会使用指定订单；否则自动造样本
4. 默认输出文件命名为：`day19回归/执行记录/Day19_P5_S2_压力测试结果_*.json`

---

## 5. 压测场景

本脚本固定压测以下三条链路：

1. `login_password`：`POST /user/auth/login/password`
2. `market_product_list`：`GET /user/market/products?page=1&pageSize=20`
3. `payment_callback`：`POST /payment/callback`

选择理由：

1. 覆盖认证、查询、写入三类典型链路
2. 与 `P2-S1` 指标口径一致，方便横向对比
3. `payment_callback` 默认自动造样本订单，避免复跑时命中历史脏状态

---

## 6. 结果结构

JSON 顶层结构：

1. `meta`：执行时间、服务地址、脚本路径、输出路径
2. `config`：Profile、并发、轮次、超时
3. `preflight`：服务探测、登录校验、token 获取
4. `scenarios[]`：每条链路的逐轮结果与聚合结果
5. `interviewSummary[]`：适合直接面试展示的摘要视图
6. `summary`：按链路归档的关键指标

重点字段：

1. `aggregate.p95Ms`
2. `aggregate.errorRatePct`
3. `aggregate.throughputRps`
4. `aggregate.verdict`

---

## 7. 服务停止时的处理

脚本在以下时机会做健康探测：

1. 预检前
2. 每个场景 warmup 前
3. 每一轮测量前
4. 若测量中出现连接不可达，再做一次立即复检

若探测到服务不可达，脚本会直接抛错退出，此时先重启服务，再重新执行上一节命令。

---

## 8. 面试展示建议

拿到结果后，优先讲以下三句话：

1. “我把个人项目压测固化成单命令入口，任何一次改动都能复跑。”
2. “结果统一输出 `P95 / 错误率 / 吞吐`，可直接横向比较不同版本。”
3. “脚本内置服务健康探测，服务挂掉时会立即停止，不会产生误导性数据。”

---

（文件结束）
