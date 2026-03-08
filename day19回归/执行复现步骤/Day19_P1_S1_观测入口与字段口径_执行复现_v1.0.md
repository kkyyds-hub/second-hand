# Day19 P1-S1 观测入口与字段口径 执行复现 v1.0

- 日期：`2026-03-04`
- 关联规划文档：`day19回归/步骤规划/Day19_P1_S1_观测入口与字段口径_v1.0.md`
- 目标：把 P1-S1 的三件事真正跑一遍  
  1) 盘点入口（看哪里）  
  2) 固化口径（看什么）  
  3) 完成最小复现（怎么看）

---

## 0. 先说结论：这份文档怎么用

如果你是边做边学，建议按下面顺序走：

1. 先读第 1 章前置条件，确认环境没问题。
2. 再按第 2 章跑三类入口盘点。
3. 再按第 3 章把统一字段口径填到记录表。
4. 最后按第 4 章做最小复现，并在执行记录中勾选 DoD。

不要跳步。  
跳过“统一口径回填”会导致后面看到数据却说不清楚结论。

---

## 1. 前置条件（不满足就先不要执行）

1. 服务已启动，可访问：`http://localhost:8080`（或你本地实际端口）。
2. 管理员账号可登录（用于 `/admin/ops/outbox/**` 与 `/admin/ops/tasks/**`）。
3. 有可查看应用日志的方式（IDEA 控制台或日志文件）。
4. 建议准备 PowerShell 终端，方便直接跑脚本和接口。
5. 已有 Day19 目录：
   - `day19回归/步骤规划/`
   - `day19回归/执行复现步骤/`
   - `day19回归/执行记录/`

---

## 2. 第一步：盘点三类观测入口（看哪里）

这一章只做一件事：确认入口真实可用，不做复杂判断。

## 2.1 入口 A：审计日志（AUDIT）

### 操作

1. 触发至少一个会打审计日志的动作（推荐登录）：
   - `POST /user/auth/login/password` 错密一次、正密一次。
2. 在日志中检索 `AUDIT` 关键字。

### 最低通过标准

1. 能检索到包含 `auditId` 的日志行。
2. 同一 action 至少有 `SUCCESS` 和 `FAILED` 两种结果中的一种（推荐两种都看到）。

### 你应该学会的点

1. 审计日志不是“有没有打印”，而是字段能不能支持排障串联。
2. `auditId` 是第一检索键，不是可有可无。

---

## 2.2 入口 B：Outbox 运维指标

### 操作

1. 调用 `GET /admin/ops/outbox/metrics`。
2. 调用 `POST /admin/ops/outbox/publish-once?limit=20`。
3. 再调一次 `GET /admin/ops/outbox/metrics` 观察变化。

### 最低通过标准

1. `metrics` 返回包含：`new/sent/fail/failRetrySum`。
2. `publish-once` 返回包含：`limit/pulled/sent/failed/processedAt`。

### 你应该学会的点

1. Outbox 的“观测”核心不是只看一条日志，而是指标快照 + 操作结果的前后对比。
2. `failRetrySum` 比单纯 `fail` 更能反映“失败放大趋势”。

---

## 2.3 入口 C：任务运维接口

### 操作

1. 调用三类列表接口：
   - `GET /admin/ops/tasks/ship-timeout?status=PENDING&page=1&pageSize=20`
   - `GET /admin/ops/tasks/refund?status=FAILED&page=1&pageSize=20`
   - `GET /admin/ops/tasks/ship-reminder?status=FAILED&page=1&pageSize=20`
2. 调用三类 `run-once`：
   - `POST /admin/ops/tasks/ship-timeout/run-once?limit=20`
   - `POST /admin/ops/tasks/refund/run-once?limit=20`
   - `POST /admin/ops/tasks/ship-reminder/run-once?limit=20`

### 最低通过标准

1. 列表接口可返回分页结构（即使 total=0 也算通过）。
2. `run-once` 返回 `taskType/batchSize/success`。

### 你应该学会的点

1. “没有待处理任务”不等于接口没价值，返回 0 也是有效观测信号。
2. 任务链路观测要同时看“列表状态”和“run-once 实际处理量”。

---

## 3. 第二步：统一字段口径回填（看什么）

这一章是 P1-S1 的核心。  
你要把三类入口都翻译成同一字段模型，否则后面无法横向比较。

## 3.1 统一字段模型（本次执行必须用）

| 字段 | 含义 | 回填方式 |
|---|---|---|
| `action` | 本次动作 | 用稳定动作名（例：`USER_LOGIN`、`OUTBOX_PUBLISH_ONCE`） |
| `actor` | 操作主体 | 推荐 `actorType:actorId`，未知填 `-` |
| `result` | 结果 | `SUCCESS/FAILED/IDEMPOTENT/IGNORED` |
| `errorCode` | 错误码 | 优先审计 `error`；无则用 `msg` 临时映射 |
| `costMs` | 耗时毫秒 | 当前可用脚本侧测量值（标记 `serverMeasured=false`） |

## 3.2 如何回填（建议格式）

把每次调用或日志样本整理成结构化对象：

```json
{
  "entryType": "AUDIT|OUTBOX|TASK",
  "action": "USER_LOGIN",
  "actor": "USER:1",
  "result": "FAILED",
  "errorCode": "PASSWORD_MISMATCH",
  "costMs": 37.12,
  "serverMeasured": false,
  "rawRef": "日志行或接口返回片段"
}
```

## 3.3 允许的临时策略（初学者常见疑问）

1. `costMs` 现在不是每个入口都由服务端提供，所以允许先用客户端测量值。
2. Outbox/任务接口不直接返回 actor 时，可先记为 `ADMIN:-` 或 `-`，但要在记录里注明缺口。
3. `errorCode` 暂无统一枚举时，可用 `msg` 映射临时码，但要保持同一错误映射一致。

---

## 4. 第三步：最小复现（怎么看）

这一章要求每类入口至少给出 1 个可重复场景。  
不追求“大而全”，追求“可跑、可看、可解释”。

## 4.1 场景 A（审计日志）

1. 失败登录一次、成功登录一次。
2. 检索 `AUDIT` 并确认：
   - 有 `action=USER_LOGIN`
   - 有 `result=FAILED`（且带 error）
   - 有 `result=SUCCESS`

---

## 4.2 场景 B（Outbox）

1. 查询 `metrics`。
2. 执行 `publish-once`。
3. 再查询 `metrics`。
4. 若有具体事件，补一条 `trigger-now`（可选但推荐）。

---

## 4.3 场景 C（任务）

1. 查询三类任务列表（任一类有数据即可继续深入）。
2. 对三类执行 `run-once`。
3. 如有失败样本，选一条做 `trigger-now` 或 `reset`。

---

## 4.4 DoD 勾选（本轮）

- [x] 三类入口（日志/Outbox/任务）均有统一口径。  
- [x] 每类入口都可对应至少一个复现步骤。  

---

## 5. 建议命令（手工版）

## 5.1 登录与 token 准备

```powershell
# 管理员登录
$adminLogin = Invoke-RestMethod -Uri "http://localhost:8080/admin/employee/login" -Method Post -ContentType "application/json" -Body '{"loginId":"13900000001","password":"admin123"}'
$adminToken = $adminLogin.data.token
$hAdmin = @{ token = $adminToken }

# 用户登录（成功）
$userLogin = Invoke-RestMethod -Uri "http://localhost:8080/user/auth/login/password" -Method Post -ContentType "application/json" -Body '{"loginId":"13800000001","password":"123456"}'
```

## 5.2 Outbox 最小检查

```powershell
Invoke-RestMethod -Uri "http://localhost:8080/admin/ops/outbox/metrics" -Method Get -Headers $hAdmin
Invoke-RestMethod -Uri "http://localhost:8080/admin/ops/outbox/publish-once?limit=20" -Method Post -Headers $hAdmin
Invoke-RestMethod -Uri "http://localhost:8080/admin/ops/outbox/metrics" -Method Get -Headers $hAdmin
```

## 5.3 任务最小检查

```powershell
Invoke-RestMethod -Uri "http://localhost:8080/admin/ops/tasks/ship-timeout?status=PENDING&page=1&pageSize=20" -Method Get -Headers $hAdmin
Invoke-RestMethod -Uri "http://localhost:8080/admin/ops/tasks/refund?status=FAILED&page=1&pageSize=20" -Method Get -Headers $hAdmin
Invoke-RestMethod -Uri "http://localhost:8080/admin/ops/tasks/ship-reminder?status=FAILED&page=1&pageSize=20" -Method Get -Headers $hAdmin

Invoke-RestMethod -Uri "http://localhost:8080/admin/ops/tasks/ship-timeout/run-once?limit=20" -Method Post -Headers $hAdmin
Invoke-RestMethod -Uri "http://localhost:8080/admin/ops/tasks/refund/run-once?limit=20" -Method Post -Headers $hAdmin
Invoke-RestMethod -Uri "http://localhost:8080/admin/ops/tasks/ship-reminder/run-once?limit=20" -Method Post -Headers $hAdmin
```

---

## 6. 建议命令（脚本版，推荐）

已提供自动化脚本：`.tools/Day19_P1_S1_观测入口与口径校验.ps1`

```powershell
powershell -ExecutionPolicy Bypass -File .\.tools\Day19_P1_S1_观测入口与口径校验.ps1 `
  -BaseUrl "http://localhost:8080" `
  -LogFile "_tmp_day18_app8080.out.log" `
  -OutboxEventId "d45ffd92-4ea1-4f3a-bdf9-a37b1edebd99" `
  -TaskCompensateType "ship-timeout" `
  -TaskId 247
```

脚本会自动输出：

1. 结构化 JSON 结果（写入 `day19回归/执行记录/`）
2. 最小清单是否通过
3. 三类入口统一字段回填样本

---

## 7. 常见失败与处理（实习生友好版）

1. `401/403`：通常是 token 头名不对。  
   - 管理端接口使用 `token` 头，不是 `authentication`。
2. `code=0,msg=...`：先记录下来，不要直接判断脚本失败。  
   - 观测阶段允许出现业务失败，关键是能解释。
3. 日志里搜不到 `AUDIT`：  
   - 先确认你触发的是已埋审计的动作（如登录/支付/封禁）。
4. `run-once success=0`：  
   - 可能只是当前无到期任务，不是接口异常。

---

## 8. 归档要求（本次执行完成后必须做）

1. 把执行结论写入：  
   `day19回归/执行记录/Day19_P1_S1_观测入口与字段口径_执行记录_v1.0.md`
2. 把动态 JSON 放到：  
   `day19回归/执行记录/Day19_P1_S1_Dynamic_Result_*.json`
3. 若发现口径缺口（例如 `costMs` 无服务端值），必须在记录文档的“风险与后续动作”里明确写出。

---

（文件结束）
