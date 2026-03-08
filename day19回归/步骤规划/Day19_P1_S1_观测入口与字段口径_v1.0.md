# Day19 P1-S1 观测入口与字段口径 v1.0

- 日期：`2026-03-04`
- 对应范围：`Day19_Scope_Freeze_v1.0.md -> Phase 1 / Step P1-S1`
- 当前状态：`已执行完成（2026-03-04 17:12:40，C1~C10 全部通过）`
- 产出目标：统一回答三个问题  
  1) 看哪里  
  2) 看什么  
  3) 怎么看

---

## 0. 这一步到底在做什么

P1-S1 不是“再加一个监控系统”，而是把当前已有能力收敛成一个可执行的观测契约层。

一句话定义：  
把审计日志、Outbox 指标、任务运维接口从“能用”升级为“口径统一、可复现、可交付”。

这一步做完后，后续 P1-S2、P2、P4 的阈值、性能预算、排障步骤才有共同语言。

---

## 1. 要解决的项目问题（现状痛点）

1. 入口分散  
   现有观测能力分布在日志、任务接口、Outbox 接口和 SQL，排障需要靠经验拼接。
2. 字段不完全同构  
   审计日志有 `action/result/error`，但 Outbox/任务接口更多是计数和状态；三者没有一张统一映射表。
3. “错误码”与“耗时”口径不统一  
   - 错误信息在部分接口表现为 `Result.msg`，不是稳定错误码枚举；  
   - `costMs` 未形成跨入口统一字段，现阶段只能通过脚本侧测量补齐。
4. 复现路径缺少最小闭环  
   要证明 DoD，需要每类入口都有“至少一个可执行场景”。

---

## 2. 范围与非目标

### 2.1 本步骤范围（做）

1. 盘点三类入口：审计日志、Outbox、任务运维接口。
2. 固化统一字段口径：动作、主体、结果、错误码、耗时。
3. 输出个人项目最小观测清单与复现步骤。

### 2.2 非目标（不做）

1. 不引入 ELK/Prometheus/Grafana 新平台。
2. 不做大规模代码重构（例如全面改造统一异常码体系）。
3. 不做性能压测结论产出（压测属于 P2/P5）。

---

## 3. 代码与文档基线（证据锚点）

1. 审计日志统一工具  
   `demo-service/src/main/java/com/demo/audit/AuditLogUtil.java`
2. Outbox 运维入口  
   `demo-service/src/main/java/com/demo/controller/admin/AdminOutboxOpsController.java`
3. 任务运维入口  
   `demo-service/src/main/java/com/demo/controller/admin/AdminTaskOpsController.java`
4. Outbox 监控任务  
   `demo-service/src/main/java/com/demo/job/OutboxMonitorJob.java`
5. Day18 已有审计口径资产  
   `day18回归/审计与监控治理/Day18_P4_S1_审计日志规范_v1.0.md`
6. Day18 告警与运维资产  
   `day18回归/审计与监控治理/Day18_P4_S2_告警规则清单_v1.0.md`

---

## 4. 统一字段口径（P1-S1 冻结版）

## 4.1 字段字典

| 统一字段 | 含义 | 取值规则 | 来源优先级 |
|---|---|---|---|
| `action` | 本次观测动作名 | 大写下划线，稳定命名 | 日志 action > 约定接口动作名 |
| `actor` | 发起主体 | 推荐格式：`{actorType}:{actorId}`，未知为 `-` | 审计字段 > 登录上下文 > `-` |
| `result` | 结果语义 | `SUCCESS/FAILED/IDEMPOTENT/IGNORED` | 审计 result > 接口返回推断 |
| `errorCode` | 错误编码 | 优先稳定业务码；无稳定码时落地临时映射码 | 审计 error > 异常类型/`Result.msg` 映射 |
| `costMs` | 耗时（毫秒） | 服务端优先；暂无服务端时允许脚本侧测量并标注来源 | 服务端日志/埋点 > 客户端脚本测量 |

## 4.2 统一口径约束

1. `action` 只能追加，不能随意改名，避免历史数据断裂。
2. `result=FAILED` 时必须可定位 `errorCode`，禁止只给自然语言。
3. `costMs` 必须有来源标签：
   - `serverMeasured=true` 表示服务端真实埋点；
   - `serverMeasured=false` 表示客户端测量值。
4. 任何入口都应能映射到上述 5 个统一字段；无法映射时必须写明缺口与临时策略。

---

## 5. 三类入口盘点（看哪里/看什么/怎么看）

## 5.1 入口 A：审计日志（AUDIT）

### 看哪里

1. 应用日志中的 `AUDIT` 行（控制台或日志文件）。
2. 核心实现：`AuditLogUtil.success/failed`。

### 看什么

1. `auditId`
2. `action`
3. `actorType/actorId`
4. `targetType/targetId`
5. `result`
6. `error`（失败分支）
7. `ip`
8. `detail`

### 怎么看

1. 按 `auditId` 串联单次动作全链路。
2. 按 `action + result` 统计成功/失败/幂等分布。
3. 按 `result=FAILED + error` 聚合高频问题。

### 统一字段映射

| 统一字段 | 审计日志映射 |
|---|---|
| `action` | `action` |
| `actor` | `actorType:actorId` |
| `result` | `result` |
| `errorCode` | `error`（仅 FAILED） |
| `costMs` | 当前缺失（P1-S1 标记缺口，先不伪造） |

---

## 5.2 入口 B：Outbox 运维与监控

### 看哪里

1. 运维接口：`/admin/ops/outbox/metrics`
2. 运维接口：`/admin/ops/outbox/publish-once`
3. 运维接口：`/admin/ops/outbox/event/{eventId}`、`trigger-now`
4. 监控日志：`OutboxMonitorJob` 输出 INFO/ERROR

### 看什么

1. `new/sent/fail/failRetrySum`
2. `publish-once` 结果：`limit/pulled/sent/failed/processedAt`
3. 告警阈值触发：`failCount/failRetrySum` 与阈值对比

### 怎么看

1. 先看 `metrics` 判断是否积压或失败放大。
2. 再执行 `publish-once` 观察处理结果。
3. 单事件用 `trigger-now + event/{eventId}` 跟踪恢复闭环。

### 统一字段映射

| 统一字段 | Outbox 映射规则 |
|---|---|
| `action` | `OUTBOX_METRICS_QUERY` / `OUTBOX_PUBLISH_ONCE` / `OUTBOX_TRIGGER_NOW` |
| `actor` | 当前接口未直接返回，临时以调用身份（管理员）记录 |
| `result` | `Result.code==1` 记 `SUCCESS`；单事件恢复可结合 `data.success` |
| `errorCode` | 失败时优先 `Result.msg` 映射（临时策略） |
| `costMs` | 当前无服务端统一字段，先用脚本侧请求耗时 |

---

## 5.3 入口 C：任务运维接口

### 看哪里

1. 列表接口：  
   - `/admin/ops/tasks/ship-timeout`  
   - `/admin/ops/tasks/refund`  
   - `/admin/ops/tasks/ship-reminder`
2. 执行接口：`run-once`
3. 补偿接口：`trigger-now`、`reset`

### 看什么

1. 任务状态：`PENDING/RUNNING/SUCCESS/FAILED/CANCELLED`
2. 重试字段：`retryCount`、`nextRetryTime`
3. 错误字段：`lastError/failReason`
4. 执行返回：`taskType/batchSize/success/processedAt` 或 `updatedRows/success`

### 怎么看

1. 先列表定位异常任务（失败积压/高重试/卡住）。
2. 再执行 `run-once` 观察处理量与成功量。
3. 对单条异常做 `trigger-now/reset`，验证恢复路径可执行。

### 统一字段映射

| 统一字段 | 任务入口映射规则 |
|---|---|
| `action` | `TASK_LIST` / `TASK_RUN_ONCE` / `TASK_TRIGGER_NOW` / `TASK_RESET` |
| `actor` | 当前接口未回传，临时以调用身份（管理员）记录 |
| `result` | `Result.code==1` 默认 `SUCCESS`；当 `data.success=false` 标注 `FAILED` |
| `errorCode` | 失败时优先 `Result.msg` 或 `lastError/failReason` |
| `costMs` | 当前无服务端统一字段，先用脚本侧请求耗时 |

---

## 6. 个人项目最小观测清单（可执行）

| 编号 | 类别 | 最小检查项 | 最小成功标准 |
|---|---|---|---|
| C1 | 审计日志 | 至少 1 个动作触发成功+失败分支 | 可检索到同 action 的 SUCCESS/FAILED |
| C2 | 审计日志 | `auditId` 串联验证 | 能按 `auditId` 定位完整审计行 |
| C3 | Outbox | 查询 `metrics` | 返回 `new/sent/fail/failRetrySum` |
| C4 | Outbox | 执行 `publish-once` | 返回 `pulled/sent/failed` |
| C5 | Outbox | 单事件恢复 | `trigger-now` 后可查询到状态变化 |
| C6 | 任务 | 三类任务列表查询 | 分页数据可返回（即使 total=0） |
| C7 | 任务 | 至少 1 次 `run-once` | 返回 `taskType/batchSize/success` |
| C8 | 任务 | 至少 1 条补偿接口验证 | `trigger-now` 或 `reset` 返回 `updatedRows` |
| C9 | 统一口径 | 三类入口均可映射 5 字段 | action/actor/result/errorCode/costMs 不缺定义 |
| C10 | DoD | 三类入口均有复现步骤 | 每类至少 1 条可复跑命令 |

---

## 7. 详细执行步骤（可直接照做）

## Step 0：准备环境与证据目录

1. 确认服务地址（建议：`http://localhost:8080`）。
2. 确认管理员 Token 可用。
3. 新建本次证据文件：
   - `day19回归/执行记录/Day19_P1_S1_动态结果_yyyy-MM-dd_HH-mm-ss.json`
4. 准备日志检索入口（控制台或日志文件）。

---

## Step 1：审计日志入口复现

1. 触发一个有成功/失败分支的动作（推荐登录）：
   - `POST /user/auth/login/password`（错密一次，正密一次）
2. 检索日志关键字：
   - `AUDIT`
   - `action=USER_LOGIN`
   - `result=FAILED`
3. 记录字段完整性：
   - `auditId/action/result/error/detail` 是否齐全
4. 产出结构化记录：
   - `action`
   - `actor`
   - `result`
   - `errorCode`
   - `costMs`（此入口暂记 `N/A`，标注服务端缺口）

---

## Step 2：Outbox 入口复现

1. 调用 `GET /admin/ops/outbox/metrics` 采集当前快照。
2. 调用 `POST /admin/ops/outbox/publish-once?limit=20`。
3. 再次调用 `GET /admin/ops/outbox/metrics`。
4. 如有可定位 `eventId`，追加执行：
   - `POST /admin/ops/outbox/event/{eventId}/trigger-now`
   - `GET /admin/ops/outbox/event/{eventId}`
5. 对每个请求记录 `costMs`（脚本侧计时），并标记 `serverMeasured=false`。

---

## Step 3：任务入口复现

1. 分别查询三类任务列表：
   - `GET /admin/ops/tasks/ship-timeout?status=PENDING&page=1&pageSize=20`
   - `GET /admin/ops/tasks/refund?status=FAILED&page=1&pageSize=20`
   - `GET /admin/ops/tasks/ship-reminder?status=FAILED&page=1&pageSize=20`
2. 分别执行三类任务 `run-once`：
   - `POST /admin/ops/tasks/ship-timeout/run-once?limit=20`
   - `POST /admin/ops/tasks/refund/run-once?limit=20`
   - `POST /admin/ops/tasks/ship-reminder/run-once?limit=20`
3. 可选执行单条补偿：
   - `POST /admin/ops/tasks/refund/{taskId}/reset`
   - 或 `POST /admin/ops/tasks/ship-reminder/{taskId}/trigger-now`
4. 记录统一字段映射与 `costMs`。

---

## Step 4：统一口径回填

1. 将三类入口输出统一整理为同一结构：

```json
{
  "entryType": "AUDIT|OUTBOX|TASK",
  "action": "STRING",
  "actor": "STRING",
  "result": "SUCCESS|FAILED|IDEMPOTENT|IGNORED",
  "errorCode": "STRING_OR_NULL",
  "costMs": 0,
  "serverMeasured": false,
  "rawRef": "原始日志行或接口响应定位信息"
}
```

2. 对无法原生提供的字段写明“临时策略”。
3. 标注下一步改造建议（如服务端补充 `costMs`）。

---

## Step 5：DoD 验收

1. 检查是否满足：
   - 三类入口均形成统一口径说明；
   - 每类入口均有至少 1 条复现步骤；
   - 最小观测清单 C1~C10 至少全部可勾选。
2. 回填最终执行记录文档。

---

## 8. DoD 映射表（本步骤验收口径）

| DoD | 验收动作 | 通过标准 |
|---|---|---|
| 三类入口均有统一口径 | 检查第 5 章映射表 | 三类入口均覆盖 `action/actor/result/errorCode/costMs` |
| 每类入口至少一个复现步骤 | 检查第 7 章 Step1~Step3 | 每类都有可执行 API/日志检索命令 |

---

## 9. 对项目的直接好处（为什么值得做）

1. 排障速度提升  
   从“先猜问题在哪”变为“先按入口清单查证据”，缩短定位时间。
2. 指标可比性提升  
   不同入口统一成同一字段字典，后续阈值、预算、回归更容易横向比较。
3. 运维动作更可控  
   Outbox 和任务链路都有固定观测入口与恢复动作，降低“手工操作失误”概率。
4. 回归成本降低  
   每一步有最小复现路径，后续变更后可快速验证是否退化。
5. 面试表达更有说服力  
   能清晰说明“观测体系如何设计、如何执行、如何验收”，不是只说概念。

---

## 10. 当前缺口与后续建议

1. 缺口 A：`costMs` 缺少服务端统一埋点  
   建议在 P1-S2 或 P2 通过拦截器/统一日志补 `costMs`。
2. 缺口 B：接口层缺少稳定业务错误码枚举  
   当前大量依赖 `Result.msg`，建议逐步引入 `errorCode` 字段或错误码常量表。
3. 缺口 C：`actor` 在 Outbox/任务接口未显式回传  
   建议后续在运维日志中补齐调用主体信息，方便审计。

---

## 11. 本步骤输出文件清单

1. 规划文档（本文件）  
   `day19回归/步骤规划/Day19_P1_S1_观测入口与字段口径_v1.0.md`
2. 建议后续新增（执行阶段）  
   - `day19回归/执行复现步骤/Day19_P1_S1_观测入口与字段口径_执行复现_v1.0.md`
   - `day19回归/执行记录/Day19_P1_S1_观测入口与字段口径_执行记录_v1.0.md`
   - `day19回归/执行记录/Day19_P1_S1_动态结果_*.json`

---

（文件结束）
