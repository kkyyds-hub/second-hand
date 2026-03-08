# Day19 P6-S1 快速复现与移交清单 v1.0

- 日期：`2026-03-08`
- 对应范围：`day19回归/Day19_Scope_Freeze_v1.0.md -> Phase 6 / Step P6-S1`
- 当前状态：`已完成`
- 总导航：`day19回归/README.md`

---

## 1. 移交目标

本清单用于把 Day19 的结论快速移交给新环境或面试展示场景，重点保证两件事：

1. 每个关键结论都能追溯到明确的文档、脚本、动态证据和代码锚点。
2. 新环境可在 `30~60` 分钟内完成最小复现，而不是被全量资产淹没。

---

## 2. 环境前置

最小复现建议准备以下环境：

1. 应用服务：`http://localhost:8080`
2. MySQL：`localhost:3306 / secondhand2`
3. Redis：`localhost:6379`
4. RabbitMQ：`localhost:5672`
5. Node / PowerShell / mysql CLI 可用
6. 本地测试账号：
   - 用户：`13800000001 / 123456`
   - 管理员：`13900000001 / admin123`

说明：

1. `P5-S2` 会自动生成 `payment_callback` 样本订单，减少手工准备成本。
2. 若服务中途停止，带健康探测的脚本会直接退出，不会继续跑脏结果。

---

## 3. 30~60 分钟最小复现路径

| 顺序 | 步骤 | 预计耗时 | 操作 | 预期产物 |
|---|---|---:|---|---|
| 1 | `P1-S1` | 5~8 分钟 | 阅读并按文档检查日志 / Outbox / 任务入口 | 理解观测口径与检索方式 |
| 2 | `P2-S1` | 8~12 分钟 | 跑核心接口性能基线脚本 | 新的 `Day19_P2_S1_动态结果_*.json` |
| 3 | `P3-S2` | 8~12 分钟 | 跑缓存命中与回源对比脚本 | 新的 `Day19_P3_S2_动态验证结果_*.json` |
| 4 | `P5-S2` | 8~12 分钟 | 跑轻量压测标准档 | 新的 `Day19_P5_S2_压力测试结果_*.json` |
| 5 | `P4-S2` 可选 | 10~15 分钟 | 跑任务链路性能脚本 | 新的 `Day19_P4_S2_动态结果_*.json` |

推荐命令：

1. `powershell -ExecutionPolicy Bypass -File "day19回归/执行复现步骤/Day19_P2_S1_核心接口性能基线_执行复现_v1.0.ps1"`
2. `powershell -ExecutionPolicy Bypass -File "day19回归/执行复现步骤/Day19_P3_S2_缓存命中与回源对比_执行复现_v1.0.ps1"`
3. `powershell -ExecutionPolicy Bypass -File "day19回归/执行复现步骤/Day19_P5_S2_压力测试脚本化_执行复现_v1.0.ps1" -Profile standard`
4. `powershell -ExecutionPolicy Bypass -File "day19回归/执行复现步骤/Day19_P4_S2_任务链路性能_执行复现_v1.0.ps1"`

---

## 4. 证据链矩阵

| Step | 结论 | 关键指标 / 观察口径 | 设计层 | 复现层 | 证据层 | 代码锚点 |
|---|---|---|---|---|---|---|
| `P1-S1` | 日志 / Outbox / 任务三类入口已统一口径 | `action / actor / result / errorCode / costMs` | `day19回归/步骤规划/Day19_P1_S1_观测入口与字段口径_v1.0.md` | `day19回归/执行复现步骤/Day19_P1_S1_观测入口与字段口径_执行复现_v1.0.md` | `day19回归/执行记录/Day19_P1_S1_观测入口与字段口径_执行记录_v1.0.md` | `demo-service/src/main/java/com/demo/audit/AuditLogUtil.java` |
| `P1-S2` | 轻量阈值与处置动作已可执行 | `warning / error` 阈值与 SQL、日志关键字 | `day19回归/步骤规划/Day19_P1_S2_轻量指标与告警阈值表_v1.0.md` | `day19回归/执行复现步骤/Day19_P1_S2_轻量指标与告警阈值表_执行复现_v1.0.md` | `day19回归/执行记录/Day19_P1_S2_轻量指标与告警阈值表_执行记录_v1.0.md` | `demo-service/src/main/java/com/demo/job/OutboxMonitorJob.java` |
| `P2-S2` | 性能发布结论为 `MINOR` | 商品列表 / 支付回调吞吐相对退化 `22.60%` / `25.61%` | `day19回归/步骤规划/Day19_P2_S2_性能预算与发布门槛_v1.0.md` | `day19回归/执行复现步骤/Day19_P2_S2_性能预算与发布门槛_执行复现_v1.0.ps1` | `day19回归/执行记录/Day19_P2_S2_性能预算与发布门槛_执行记录_v1.0.md` | `demo-service/src/main/java/com/demo/controller/PaymentController.java` |
| `P3-S1` | 热点 SQL 无明显可规避全表扫描 | `EXPLAIN / EXPLAIN ANALYZE / SHOW INDEX` | `day19回归/步骤规划/Day19_P3_S1_SQL热点回归报告_v1.0.md` | `day19回归/执行复现步骤/Day19_P3_S1_SQL热点回归_执行复现_v1.0.ps1` | `day19回归/执行记录/Day19_P3_S1_EXPLAIN证据_2026-03-06_10-41-39.json` | `demo-service/src/main/java/com/demo/config/MybatisPlusConfig.java` |
| `P3-S2` | 缓存命中收益可量化展示 | 列表 `P50 34.90 -> 12.02ms`；详情 `24.86 -> 10.00ms` | `day19回归/步骤规划/Day19_P3_S2_缓存命中与回源对比_v1.0.md` | `day19回归/执行复现步骤/Day19_P3_S2_缓存命中与回源对比_执行复现_v1.0.ps1` | `day19回归/执行记录/Day19_P3_S2_动态验证结果_2026-03-08_04-12-56.json` | `demo-service/src/main/java/com/demo/service/serviceimpl/ProductServiceImpl.java` |
| `P4-S1` | Outbox 已具备可量化治理建议 | `limit=100` 平均吞吐 `899.04 msg/s` | `day19回归/步骤规划/Day19_P4_S1_Outbox发布性能报告_v1.0.md` | `day19回归/执行复现步骤/Day19_P4_S1_Outbox发布性能_执行复现_v1.0.ps1` | `day19回归/执行记录/Day19_P4_S1_动态结果_2026-03-08_04-34-39.json` | `demo-service/src/main/java/com/demo/job/OutboxPublishJob.java` |
| `P4-S2` | 任务 run-once 吞吐与恢复闭环成立 | `ship-timeout 26.63 task/s`；`refund 59.51 task/s`；`ship-reminder 96.06 task/s` | `day19回归/步骤规划/Day19_P4_S2_任务链路性能与恢复_v1.0.md` | `day19回归/执行复现步骤/Day19_P4_S2_任务链路性能_执行复现_v1.0.ps1` | `day19回归/Day19_P4_S2_任务链路性能与恢复报告_v1.0.md` | `demo-service/src/main/java/com/demo/controller/admin/AdminTaskOpsController.java` |
| `P5-S1` | 高并发下无重复业务副作用 | `50` 并发下支付 / 回调 / 封禁 / 解封 / 任务链路错误率 `0%` | `day19回归/步骤规划/Day19_P5_S1_并发回归方案_v1.0.md` | `day19回归/执行复现步骤/Day19_P5_S1_并发回归_执行复现_v1.0.ps1` | `day19回归/执行记录/Day19_P5_S1_并发回归执行记录_v1.0.md` | `demo-service/src/main/java/com/demo/service/serviceimpl/OrderServiceImpl.java` |
| `P5-S2` | 轻量压测单命令可复跑 | `login P95=353.13ms`；`list P95=24.01ms`；`callback P95=42.59ms` | `day19回归/步骤规划/Day19_P5_S2_压力测试脚本化_v1.0.md` | `day19回归/执行复现步骤/Day19_P5_S2_压力测试脚本化_执行复现_v1.0.ps1` | `day19回归/执行记录/Day19_P5_S2_压力测试结果_2026-03-08_17-59-31.json` | `demo-service/src/main/java/com/demo/controller/PaymentController.java` |

---

## 5. 交接顺序建议

如果是给新同学或未来的自己接手，建议按以下顺序阅读：

1. `day19回归/README.md`
2. `day19回归/Day19_Scope_Freeze_v1.0.md`
3. `P2-S2` 执行记录，先理解 Day19 最终发布口径
4. `P3-S2` 与 `P4-S2`，理解性能和异步链路收益
5. `P5-S1` 与 `P5-S2`，理解并发稳定性与压测复跑入口

---

## 6. 已知注意事项

1. 历史乱码目录已清理，正式证据链统一以 `day19回归/执行记录/` 为准。
2. `P2-S2` 的执行记录已完成，冻结文档已回填到正式完成态。
3. Day19 的“设计层 -> 复现层 -> 证据层”现已闭环，后续新增 Step 时建议继续沿用同样目录结构。

---

## 7. P6-S1 DoD 回填

- [x] Day19 关键结论均有证据路径
- [x] 新环境可在 `30~60` 分钟完成最小复现
- [x] 已输出总 README 与快速移交清单

---

（文件结束）
