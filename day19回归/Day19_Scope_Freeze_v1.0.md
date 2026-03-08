# Day19 范围冻结：可观测性与性能优化（个人项目版）

- 项目：二手交易平台（`demo-platform` / `com.demo`）  
- 模块：Day19 可观测性与性能优化（监控、性能基线、并发压测、回归收口）  
- 文档版本：v1.0（首次冻结）  
- 冻结日期：2026-03-04  
- 文档性质：**里程碑冻结文档（允许跨多天执行）**  
- 目标：在 Day18 收口基础上，形成“可量化、可追踪、可复现”的性能与可观测闭环。

---

## 0. 范围定位（冻结）

1. Day19 目标不是“重运维平台搭建”，而是“个人项目可证明的可观测与性能提升闭环”。  
2. 所有优化动作必须绑定可对比证据：基线数据 -> 优化动作 -> 结果数据。  
3. 以现有代码能力为主（审计日志、Outbox 监控、任务运维接口、缓存与并发控制），避免引入高维护成本体系。  
4. 交付口径优先满足简历展示：清晰指标、明确取舍、可复跑脚本、可追溯证据。

---

## 1. 边界决策（已冻结）

1. 完整 ELK/EFK 集群部署：**NO（个人项目不做重部署，保留日志规范与检索证据）**  
2. Prometheus + Grafana + Alertmanager 全套生产化上线：**NO（Day19 不做重运维）**  
3. 基于现有日志与管理端接口做轻量可观测闭环：**YES**  
4. 关键链路性能预算（P95/错误率/吞吐）量化：**YES**  
5. 数据库热点 SQL 继续按 EXPLAIN + 索引策略回归：**YES**  
6. 商品读链路缓存命中与击穿防护实测复核：**YES**  
7. MQ/Outbox/任务链路的积压与失败重试可观测收口：**YES**  
8. 并发与压力测试只做核心链路，不追求全站大压测：**YES**  
9. 引入商业 APM（New Relic/AppDynamics）：**NO**  
10. 前端 CDN/WebSocket/大规模前端重构：**NO（当前 Day19 聚焦后端链路）**  
11. 静态代码质量检查改为轻量规则与脚本化检查：**YES**  
12. 新增大业务功能：**NO（Day19 只做可观测与性能收口）**

---

## 2. 当前工程基线（基于仓库事实）

1. 已具备统一审计日志模板：`AUDIT auditId/action/actor/target/result/ip/detail`，见  
   `demo-service/src/main/java/com/demo/audit/AuditLogUtil.java`。  
2. 已具备 Outbox 运维可观测入口（查询、手工触发、单轮发布、指标）：  
   `demo-service/src/main/java/com/demo/controller/admin/AdminOutboxOpsController.java`。  
3. 已具备 Outbox 定时监控任务与阈值告警日志（`new/sent/fail/failRetrySum`）：  
   `demo-service/src/main/java/com/demo/job/OutboxMonitorJob.java`。  
4. 已具备任务运维接口（列表、run-once、trigger-now、reset）：  
   `demo-service/src/main/java/com/demo/controller/admin/AdminTaskOpsController.java`。  
5. 已具备商品读链路缓存优化（详情/列表缓存、空值缓存、短锁、TTL 抖动、安全解锁 Lua）：  
   `demo-service/src/main/java/com/demo/service/serviceimpl/ProductServiceImpl.java`。  
6. 已具备关键读路径 `@Transactional(readOnly = true)` 优化（订单与商品读链路）。  
7. 已具备分页与安全守卫：  
   `MybatisPlusConfig`（分页上限 + BlockAttack）与 `PaginationMixGuardInterceptor`（禁止 PageHelper/MP 混用）。  
8. 已具备 RabbitMQ 关键参数与拓扑声明（含 `prefetch`、DLQ、延迟队列）：  
   `demo-service/src/main/resources/application.yml` + `RabbitMqConfiguration.java`。  
9. 已具备 Outbox/任务批处理参数与重试参数配置项：  
   `application.yml` 中 `outbox.*`、`order.*`。  
10. 当前未落地 Actuator/Micrometer/Prometheus 依赖与端点（`demo-service/pom.xml` 未引入对应 starter）。

---

## 3. Day19 路线图（冻结）

| 阶段 | 名称 | 核心目标 | 产出类型 |
|---|---|---|---|
| Phase 1 | 可观测基线收口 | 把“日志/任务/Outbox”观测入口标准化 | 口径文档 + 指标清单 + 复现脚本 |
| Phase 2 | 性能基线与预算 | 形成核心接口 P95/吞吐/错误率基线与预算 | 压测结果 + 基线报告 |
| Phase 3 | 数据与缓存优化 | SQL 与缓存热点继续收口，给出前后对比 | EXPLAIN/缓存验证记录 |
| Phase 4 | 异步链路性能与稳定性 | Outbox/MQ/任务链路积压与恢复能力量化 | 链路压测/失败恢复记录 |
| Phase 5 | 并发控制与限流复核 | 核心竞争链路并发语义稳定可证明 | 并发回归记录 + 参数建议 |
| Phase 6 | 文档冻结与复现移交 | Day19 证据化交付并形成快速复现路径 | 冻结文档 + 移交清单 |

---

## 4. Phase 1：可观测基线收口（冻结）

### Step P1-S1：观测入口盘点与统一口径
- 目标：明确当前系统“看哪里、看什么、怎么看”。  
- 范围：
  1. 盘点审计日志、Outbox 指标、任务运维接口。  
  2. 固化字段口径（动作、主体、结果、错误码、耗时）。  
  3. 输出个人项目可执行的最小观测清单。  
- 输出：`Day19_P1_S1_观测入口与字段口径_v1.0.md`。  
- DoD：
  1. 三类入口（日志/Outbox/任务）均有统一口径。  
  2. 每类入口都可对应至少一个复现步骤。  
- 完成状态：**已完成（执行于 2026-03-04 17:12:40）**
- 执行证据：`day19回归/执行记录/Day19_P1_S1_Dynamic_Result_2026-03-04_17-12-40.json`
- 代码锚点：
  1. `demo-service/src/main/java/com/demo/audit/AuditLogUtil.java`
  2. `demo-service/src/main/java/com/demo/controller/admin/AdminOutboxOpsController.java`
  3. `demo-service/src/main/java/com/demo/controller/admin/AdminTaskOpsController.java`

### Step P1-S2：轻量指标快照与告警阈值表
- 目标：形成可直接用于排障的“阈值 -> 处置动作”表。  
- 范围：
  1. 基于现有 Outbox/任务指标定义 warning/error 阈值。  
  2. 明确“积压、失败重试、处理成功率”三类处置流程。  
  3. 输出日志检索关键字与 SQL 对照。  
- 输出：`Day19_P1_S2_轻量指标与告警阈值表_v1.0.md`。  
- DoD：
  1. 每个阈值都有具体处置步骤。  
  2. 处置步骤可通过运维接口实操复现。  
- 完成状态：**已完成（执行于 2026-03-06 10:15:07）**
- 执行证据：`day19回归/执行记录/Day19_P1_S2_Dynamic_Result_2026-03-06_10-15-07.json`
- 代码锚点：
  1. `demo-service/src/main/java/com/demo/job/OutboxMonitorJob.java`
  2. `demo-service/src/main/resources/application.yml`

---

## 5. Phase 2：性能基线与预算（冻结）

### Step P2-S1：核心接口性能基线（本地）
- 目标：建立 Day19 基线，不再用“快/慢”口头描述。  
- 范围：
  1. 选取核心链路：登录、商品列表、支付/回调。  
  2. 输出每条链路 `P50/P95/错误率/吞吐`。  
  3. 固化压测命令、并发参数、样本规模。  
- 输出：
  1. `Day19_P2_S1_核心接口性能基线_v1.0.md`
  2. `Day19_P2_S1_动态结果_*.json`
- DoD：
  1. 至少 3 条链路具备基线数据。  
  2. 基线可重复执行且结果可解释。  
- 完成状态：**已完成（执行于 2026-03-06 09:54:21）**
- 执行证据：`day19回归/执行记录/Day19_P2_S1_动态结果_2026-03-06_09-54-21.json`
- 代码锚点：
  1. `demo-service/src/main/java/com/demo/controller/user/UserAuthController.java`
  2. `demo-service/src/main/java/com/demo/controller/user/MarketProductController.java`
  3. `demo-service/src/main/java/com/demo/controller/PaymentController.java`

### Step P2-S2：性能预算与退化门槛
- 目标：建立发布可用的“性能门槛”。  
- 范围：
  1. 定义预算（示例：`P95 <= 500ms`、`错误率 <= 1%`）。  
  2. 定义退化判定与回滚建议。  
  3. 形成发布前性能检查清单。  
- 输出：`Day19_P2_S2_性能预算与发布门槛_v1.0.md`。  
- DoD：
  1. 每个预算都可由现有脚本采集。  
  2. 每次改动可执行一次性能回归。  
- 完成状态：**已完成（执行于 2026-03-06 10:47:37）**
- 执行证据：`day19回归/执行记录/Day19_P2_S2_动态结果_2026-03-06_10-47-37.json`
- 代码锚点：
  1. `demo-service/src/main/java/com/demo/controller/user/UserAuthController.java`
  2. `demo-service/src/main/java/com/demo/controller/user/MarketProductController.java`
  3. `demo-service/src/main/java/com/demo/controller/PaymentController.java`

---

## 6. Phase 3：数据与缓存优化（冻结）

### Step P3-S1：热点 SQL 回归与索引复核
- 目标：继续压缩 DB 热点查询耗时，避免“缓存掩盖 SQL 问题”。  
- 范围：
  1. 基于订单/任务/Outbox 管理查询做 EXPLAIN 回归。  
  2. 复核分页查询是否满足索引命中与行数控制。  
  3. 输出“高风险 SQL -> 处置建议”。  
- 输出：
  1. `Day19_P3_S1_SQL热点回归报告_v1.0.md`
  2. `Day19_P3_S1_EXPLAIN证据_*.json`
- DoD：
  1. 高频 SQL 均有 EXPLAIN 证据。  
  2. 不存在明显可规避的全表扫描热点。  
- 完成状态：**已完成（执行于 2026-03-06 10:41:39）**
- 执行证据：`day19回归/执行记录/Day19_P3_S1_EXPLAIN证据_2026-03-06_10-41-39.json`
- 代码锚点：
  1. `demo-service/src/main/java/com/demo/controller/admin/AdminTaskOpsController.java`
  2. `demo-service/src/main/java/com/demo/controller/admin/AdminOutboxOpsController.java`
  3. `demo-service/src/main/java/com/demo/config/MybatisPlusConfig.java`

### Step P3-S2：商品读缓存实测与命中率复核
- 目标：验证缓存策略在实际并发下的收益与稳定性。  
- 范围：
  1. 对市场列表/详情执行缓存命中与回源对比。  
  2. 验证空值缓存、短锁、防误删锁逻辑。  
  3. 输出缓存参数调优建议（TTL/lock/jitter）。  
- 输出：
  1. `Day19_P3_S2_缓存命中与回源对比_v1.0.md`
  2. `Day19_P3_S2_动态验证结果_*.json`
- DoD：
  1. 可量化展示“首读回源 vs 命中返回”差异。  
  2. 击穿防护在并发下无明显失效。  
- 完成状态：**已完成（执行于 2026-03-08 04:12:56）**
- 执行证据：`day19回归/执行记录/Day19_P3_S2_动态验证结果_2026-03-08_04-12-56.json`
- 代码锚点：
  1. `demo-service/src/main/java/com/demo/service/serviceimpl/ProductServiceImpl.java`
  2. `demo-service/src/main/resources/application.yml`

---

## 7. Phase 4：异步链路性能与稳定性（冻结）

### Step P4-S1：Outbox 发布性能与积压收口
- 目标：让 Outbox 从“可用”升级为“可量化可治理”。  
- 范围：
  1. 观测 `publish-once` 不同 limit 下吞吐与失败率。  
  2. 观测 `new/sent/fail/failRetrySum` 变化曲线。  
  3. 输出最佳批大小建议与告警阈值建议。  
- 输出：
  1. `Day19_P4_S1_Outbox发布性能报告_v1.0.md`
  2. `Day19_P4_S1_动态结果_*.json`
- DoD：
  1. 能给出可执行的 batch-size 参数建议。  
  2. 能给出“积压异常”排障闭环。  
- 完成状态：**已完成（执行于 2026-03-08 04:34:39）**
- 执行证据：`day19回归/执行记录/Day19_P4_S1_动态结果_2026-03-08_04-34-39.json`
- 代码锚点：
  1. `demo-service/src/main/java/com/demo/job/OutboxPublishJob.java`
  2. `demo-service/src/main/java/com/demo/job/OutboxMonitorJob.java`
  3. `demo-service/src/main/java/com/demo/controller/admin/AdminOutboxOpsController.java`

### Step P4-S2：任务链路 run-once 吞吐与恢复能力验证
- 目标：验证 ship-timeout/refund/ship-reminder 在压力下的处理稳定性。  
- 范围：
  1. 运行 `run-once` 多轮，统计 success 与失败恢复时间。  
  2. 验证 trigger-now/reset 手工补偿路径。  
  3. 输出任务处理容量建议与重试间隔建议。  
- 输出：`Day19_P4_S2_任务链路性能与恢复报告_v1.0.md`。  
- DoD：
  1. 三类任务均有运行态样本。  
  2. 失败恢复路径可复现并可追踪。  
- 完成状态：**已完成（执行于 2026-03-08 15:30:35）**
- 执行证据：`day19回归/执行记录/Day19_P4_S2_动态结果_2026-03-08_15-27-35.json`
- 代码锚点：
  1. `demo-service/src/main/java/com/demo/controller/admin/AdminTaskOpsController.java`
  2. `demo-service/src/main/java/com/demo/job/OrderShipTimeoutTaskJob.java`
  3. `demo-service/src/main/java/com/demo/job/OrderRefundTaskJob.java`
  4. `demo-service/src/main/java/com/demo/job/OrderShipReminderTaskJob.java`

---

## 8. Phase 5：并发控制与压力测试（冻结）

### Step P5-S1：核心并发链路回归
- 目标：在高并发下保持业务语义稳定（幂等、无重复副作用）。  
- 范围：
  1. 支付、回调、封禁/解封、任务处理链路并发复核。  
  2. 校验 CAS 分流与幂等返回口径。  
  3. 记录冲突比例与最终一致性结果。  
- 输出：`Day19_P5_S1_并发回归执行记录_v1.0.md`。  
- DoD：
  1. 并发冲突不会导致重复业务副作用。  
  2. 冲突分流文案与日志口径稳定。  
- 完成状态：**已完成（执行于 2026-03-08 16:55:38）**
- 执行证据：`day19回归/执行记录/Day19_P5_S1_动态结果_2026-03-08_16-55-27.json`
- 代码锚点：
  1. `demo-service/src/main/java/com/demo/service/serviceimpl/OrderServiceImpl.java`
  2. `demo-service/src/main/java/com/demo/service/serviceimpl/UserServiceImpl.java`
  3. `demo-service/src/main/java/com/demo/service/serviceimpl/ProductServiceImpl.java`

### Step P5-S2：压力测试脚本化（轻量版）
- 目标：形成个人项目可持续复跑的压力测试入口。  
- 范围：
  1. 固化 PowerShell/Newman（或 JMeter 二选一）压测脚本。  
  2. 输出压测参数模板（并发、轮次、超时）。  
  3. 输出结果结构（P95/错误率/吞吐）。  
- 输出：
  1. `day19回归/执行复现步骤/Day19_P5_S2_压力测试脚本化_执行复现_v1.0.md`
  2. `day19回归/执行记录/Day19_P5_S2_压力测试结果_*.json`
- DoD：
  1. 一条命令可复跑压测。  
  2. 结果格式可直接用于面试展示。  
- 完成状态：**已完成（执行于 2026-03-08 17:59:31）**
- 执行证据：`day19回归/执行记录/Day19_P5_S2_压力测试结果_2026-03-08_17-59-31.json`
- 执行脚本：`day19回归/执行复现步骤/Day19_P5_S2_压力测试脚本化_执行复现_v1.0.ps1`

---

## 9. Phase 6：文档冻结与移交（冻结）

### Step P6-S1：Day19 证据化冻结
- 目标：把 Day19 所有结论变成可追溯资产。  
- 范围：
  1. 补齐设计文档、复现步骤、执行记录三层产物。  
  2. 建立“代码锚点 -> 指标 -> 结论”的证据链。  
  3. 输出 Day19 轻量移交清单。  
- 输出：
  1. `Day19_Scope_Freeze_v1.0.md`
  2. `day19回归/README.md`
  3. `day19回归/文档冻结与移交/Day19_P6_S1_快速复现与移交清单_v1.0.md`
- DoD：
  1. Day19 关键结论均有证据路径。  
  2. 新环境可在 30~60 分钟完成最小复现。  
- 完成状态：**已完成（执行于 2026-03-08 18:23:16）**
- 执行证据：
  1. `day19回归/README.md`
  2. `day19回归/文档冻结与移交/Day19_P6_S1_快速复现与移交清单_v1.0.md`

---

## 10. Day19 交付物分层索引（规划版）

> 说明：以下为 Day19 建议目录；当前冻结文档先行，后续按阶段逐步补齐。

| 层级 | 目录 | 作用 | 计划产物 |
|---|---|---|---|
| 总览层 | `day19回归/` | 统一边界与阶段结论 | `Day19_Scope_Freeze_v1.0.md` |
| 设计层 | `可观测性治理/` `性能治理/` `并发与压测/` | 沉淀指标口径与优化方案 | 各 Step 设计文档 |
| 复现层 | `执行复现步骤/` | 固化压测与排障步骤 | 各 Step 复现文档 |
| 证据层 | `执行记录/` | 保存动态结果与前后对比 | JSON/MD 执行记录 |
| 移交层 | `文档冻结与移交/` | 个人项目轻量移交 | 快速复现清单 |

---

## 11. 关键代码证据入口（速查）

| 主题 | 现有能力 | 代码入口 |
|---|---|---|
| 审计日志 | 统一 `AUDIT` 字段模板 | `demo-service/src/main/java/com/demo/audit/AuditLogUtil.java` |
| Outbox 可观测 | 指标查询/手工触发/单轮发布 | `demo-service/src/main/java/com/demo/controller/admin/AdminOutboxOpsController.java` |
| Outbox 定时监控 | 失败阈值监控与告警日志 | `demo-service/src/main/java/com/demo/job/OutboxMonitorJob.java` |
| 任务运维 | run-once/trigger-now/reset | `demo-service/src/main/java/com/demo/controller/admin/AdminTaskOpsController.java` |
| 缓存性能 | 缓存键、短锁、Lua 安全解锁、TTL 抖动 | `demo-service/src/main/java/com/demo/service/serviceimpl/ProductServiceImpl.java` |
| 并发与事务 | 读写事务分层与幂等分流 | `demo-service/src/main/java/com/demo/service/serviceimpl/OrderServiceImpl.java` |
| 分页与 SQL 安全 | PageHelper/MP 混用守卫 + 防全表更新 | `demo-service/src/main/java/com/demo/config/PaginationMixGuardInterceptor.java` `demo-service/src/main/java/com/demo/config/MybatisPlusConfig.java` |
| MQ 性能参数 | `prefetch`、DLQ、延迟队列 | `demo-service/src/main/resources/application.yml` `demo-service/src/main/java/com/demo/config/RabbitMqConfiguration.java` |

---

## 12. 个人项目执行建议（非强制）

1. Day19 优先做“有指标、有对比、有证据”的三段式闭环，不追求平台化大而全。  
2. 每个 Step 至少保留一个可复跑脚本和一个结构化结果文件（JSON/MD）。  
3. 面试表达优先展示“为什么改、改了什么、量化提升多少”，而不是只展示技术名词。  

---

（文件结束）
