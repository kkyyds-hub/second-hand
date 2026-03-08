# Day19 P1-S1 观测入口与字段口径 执行记录 v1.0

- 日期：`2026-03-04`
- 执行人：`kk + Codex`
- 服务地址：`http://localhost:8080`
- 关联文档：
  - 规划：`day19回归/步骤规划/Day19_P1_S1_观测入口与字段口径_v1.0.md`
  - 复现：`day19回归/执行复现步骤/Day19_P1_S1_观测入口与字段口径_执行复现_v1.0.md`
- 当前状态：`已完成`

---

## 1. 本轮执行目标（请先写清楚）

本轮目标是否完整覆盖三件事：

1. 入口盘点（日志 / Outbox / 任务）
2. 字段口径统一（action/actor/result/errorCode/costMs）
3. 最小复现闭环（每类入口至少 1 个场景）

结论（执行后填写）：

- [x] 已覆盖
- [ ] 部分覆盖（需补：`______`）

---

## 2. 环境与前置条件回填

| 项目 | 结果 | 备注 |
|---|---|---|
| 服务可访问 | `[x]` | `http://localhost:8080` |
| 管理员 token 可用 | `[x]` | `/admin/employee/login` 成功返回 token |
| 日志可检索 AUDIT | `[x]` | `_tmp_day18_app8080.out.log` 可检索 `AUDIT auditId=` |
| 执行目录已创建 | `[x]` | `day19回归/执行记录/` |

---

## 3. 入口盘点结果（看哪里）

## 3.1 审计日志入口

1. 触发动作：`USER_LOGIN`、`ADMIN_LOGIN`（成功+失败）
2. 检索关键字：`AUDIT auditId=` / `action=USER_LOGIN` / `action=ADMIN_LOGIN`
3. 观察结果：可同时看到成功与失败日志链路
4. 结论：
   - [x] 可用
   - [ ] 不可用（原因：`______`）

## 3.2 Outbox 入口

1. 调用 `GET /admin/ops/outbox/metrics`：`成功`
2. 调用 `POST /admin/ops/outbox/publish-once`：`成功`
3. 关键返回字段是否齐全：`new/sent/fail/failRetrySum/pulled/failed` -> `齐全`
4. 结论：
   - [x] 可用
   - [ ] 不可用（原因：`______`）

## 3.3 任务入口

1. 三类列表是否可查：`ship-timeout/refund/ship-reminder` -> `是`
2. 三类 `run-once` 是否可执行：`是`
3. 补偿接口是否可执行（trigger-now/reset）：`是`
4. 结论：
   - [x] 可用
   - [ ] 不可用（原因：`______`）

---

## 4. 统一字段口径回填（看什么）

> 说明：这一章是本步骤核心。

| entryType | action | actor | result | errorCode | costMs | serverMeasured | rawRef |
|---|---|---|---|---|---:|---|---|
| AUDIT | USER_LOGIN | USER:13800000001 | SUCCESS / FAILED | 用户名或密码错误（失败样本） | 1285.01 | false | POST /user/auth/login/password |
| OUTBOX | OUTBOX_METRICS_QUERY / OUTBOX_PUBLISH_ONCE / OUTBOX_TRIGGER_NOW | ADMIN:- | SUCCESS |  | 8.26 ~ 13.09 | false | /admin/ops/outbox/** |
| TASK | TASK_LIST / TASK_RUN_ONCE / TASK_COMPENSATE | ADMIN:- | SUCCESS |  | 6.60 ~ 26.72 | false | /admin/ops/tasks/** |

回填说明（执行后填写）：

1. `action` 命名是否稳定：`稳定，已按入口动作固化`
2. `errorCode` 是否可解释：`可解释（失败时来自接口 msg / 审计 error）`
3. `costMs` 来源是否明确：`客户端脚本测量（serverMeasured=false）`

---

## 5. 最小复现结果（怎么看）

| 场景 | 操作 | 预期 | 实际 | 是否通过 |
|---|---|---|---|---|
| A 审计日志 | 登录成功+失败并检索 AUDIT | 能看到 action/result/error | 成功与失败链路都已记录，auditId 可检索 | `[x]` |
| B Outbox | metrics -> publish-once -> metrics | 能看到关键指标与处理结果 | 指标字段齐全；单事件补偿可调用 | `[x]` |
| C 任务 | list + run-once + 单条补偿 | 能看到分页/执行结果字段 | 三类列表与 run-once 都成功；补偿 updatedRows=1 | `[x]` |

---

## 6. 最小观测清单 C1~C10 勾选

| 编号 | 检查项 | 结果 |
|---|---|---|
| C1 | 审计动作至少 1 个成功+失败样本 | `[x]` |
| C2 | auditId 可用于日志串联 | `[x]` |
| C3 | Outbox metrics 字段完整 | `[x]` |
| C4 | Outbox publish-once 字段完整 | `[x]` |
| C5 | Outbox 单事件恢复可执行 | `[x]` |
| C6 | 三类任务列表可查 | `[x]` |
| C7 | 三类 run-once 可执行 | `[x]` |
| C8 | 至少 1 条补偿接口可执行 | `[x]` |
| C9 | 三类入口可映射统一 5 字段 | `[x]` |
| C10 | 每类入口至少 1 个复现步骤 | `[x]` |

---

## 7. DoD 验收结论

DoD-1：三类入口（日志/Outbox/任务）均有统一口径

- [x] 通过
- [ ] 不通过（差距：`______`）

DoD-2：每类入口都可对应至少一个复现步骤

- [x] 通过
- [ ] 不通过（差距：`______`）

最终结论：

- [x] P1-S1 完成
- [ ] P1-S1 未完成（需补动作：`______`）

---

## 8. 风险与后续动作（非常重要）

1. 本轮发现的字段缺口：`costMs 仍为脚本侧测量，服务端暂无统一耗时字段`
2. 本轮发现的观测盲区：`Outbox 单事件补偿在 SENT 事件上 updatedRows=0（可调用但无状态变化）`
3. 下步建议（指向具体动作）：`P1-S2 增加“如何挑选 NEW/FAIL 事件”的固定步骤，并补日志文件标准路径`

---

## 9. 证据文件索引

1. 动态结果 JSON（本次全量通过）：`day19回归/执行记录/Day19_P1_S1_Dynamic_Result_2026-03-04_17-12-40.json`
2. 日志样本：`_tmp_day18_app8080.out.log`
3. 相关脚本：`.tools/Day19_P1_S1_观测入口与口径校验.ps1`

---

（文件结束）
