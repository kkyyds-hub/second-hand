# 前端冻结文档主入口

> 启用日期：`2026-03-14`
> 适用项目：`demo-admin-ui`
> 当前唯一主入口：`demo-admin-ui/docs/frontend-freeze/README.md`

这套文档用于统一管理 `demo-admin-ui` 的前端计划、冻结、联调、回填与移交。
从 `2026-03-14` 起，旧的 `short-term-plan.md`、`daily-progress.md`、`frontend-todo.md` 只保留索引和归档职责，**不再作为主计划来源**。

---

## 1. 这套体系解决什么问题

1. 解决前端计划散落在多个说明型文档里的问题。
2. 解决“已经做了但没有回填”的问题。
3. 解决“今天到底做什么、做完怎么记、下一天如何续写”的问题。
4. 解决前后端联调、演示冻结和交接阶段缺少统一口径的问题。

---

## 2. 当前推荐执行日

- 当前日期：`2026-03-16`
- 当前执行日：`FrontDay10`
- 当前执行主题：`演示版冻结与移交`
- 当前状态：`已完成并回填（2026-03-16 Day10 演示版冻结与移交收口）`
- 进入入口：`demo-admin-ui/docs/frontend-freeze/FrontDay10/README.md`
- 接棒依据：
  1. `FrontDay09` 已完成并回填，Day09 技能边界闭环；
  2. `Dashboard / UserList / ProductReview / AuditCenter / OpsCenter` 的 `2026-03-16` 联调与补证据已闭环；
  3. 当前仅剩的后端历史测试源码编译阻塞不属于 `FrontDay10` 前端演示版冻结主链。
- 当日重点：
  - 冻结演示版展示范围、明确不展示范围；
  - 固化移交入口、证据入口、遗留项分层；
  - 明确 `Day10` 只做演示版冻结与移交，不重开 `Day09` 已闭环联调。

---

## 3. 当前前端基线（2026-03-16）

| 模块 | 当前状态 | 证据等级 | 主要证据 |
|---|---|---|---|
| 登录与鉴权 | 已完成并回填 | 代码已确认 + 文档已记录 | `src/pages/Login.vue`、`src/api/auth.ts`、`src/utils/request.ts`、`src/router/index.ts` |
| 路由与导航 | 已完成并回填 | 代码已确认 + 文档已记录 | `src/layouts/MainLayout.vue`、`src/router/index.ts`、`src/pages/LogoutPage.vue` |
| 用户与商家管理 | 已完成并回填 | 代码已确认 + 运行态已确认 | `src/pages/users/UserList.vue`、`src/api/user.ts`、`demo-admin-ui/docs/frontend-freeze/FrontDay09/04_联调准备与验收/runtime-artifacts/2026-03-15/FrontDay09_userlist_ban_unban_2026-03-15.json` |
| 商品审核 | 已完成并回填 | 代码已确认 + 运行态已确认 | `src/pages/products/ProductReview.vue`、`src/api/product.ts`、`demo-admin-ui/docs/frontend-freeze/FrontDay09/04_联调准备与验收/runtime-artifacts/2026-03-16/FrontDay09_productreview_approve_reject_2026-03-16.json` |
| 纠纷与违规 | 已完成并回填 | 联调已通过 | `src/pages/audit/AuditCenter.vue`、`src/api/audit.ts`、`demo-admin-ui/docs/frontend-freeze/FrontDay09/04_联调准备与验收/runtime-artifacts/2026-03-16/FrontDay09_auditcenter_report_dismiss_2026-03-16.json`、`demo-admin-ui/docs/frontend-freeze/FrontDay09/04_联调准备与验收/runtime-artifacts/2026-03-16/FrontDay09_auditcenter_dispute_arbitrate_2026-03-16.json` |
| Dashboard | 已完成并回填（sellerName + 趋势 / 扩展统计证据已闭环） | 代码已确认 + 运行态已确认 | `src/pages/Dashboard.vue`、`src/api/dashboard.ts`、`src/api/adminExtra.ts`、`demo-admin-ui/docs/frontend-freeze/FrontDay09/04_联调准备与验收/runtime-artifacts/2026-03-16/FrontDay09_dashboard_sellername_2026-03-16.json`、`demo-admin-ui/docs/frontend-freeze/FrontDay09/04_联调准备与验收/runtime-artifacts/2026-03-16/FrontDay09_dashboard_trend_stats_2026-03-16.json` |
| 运维中心 | 已完成并回填 | 代码已确认 + 运行态已确认 | `src/pages/ops/OpsCenter.vue`、`src/api/adminExtra.ts`、`demo-admin-ui/docs/frontend-freeze/FrontDay09/04_联调准备与验收/runtime-artifacts/2026-03-16/FrontDay09_opscenter_write_actions_2026-03-16.json` |
| 系统设置 | 进行中（静态边界已确认，不纳入 Day10 演示主链完结口径） | 代码已确认 + 文档已记录 | `src/pages/settings/SystemSettings.vue`、`demo-admin-ui/docs/frontend-freeze/FrontDay06/05_进度回填/FrontDay06_Progress_Backfill_v1.0.md` |

说明：

- `已完成并回填`：当前范围内已纳入冻结体系并有证据回填。
- `进行中`：主线已推进，但仍有后续范围、补充验证或下一天任务待继续。
- `联调已通过`：实现、协议与真实运行证据都已形成可回查结论。
- `FrontDay10` 已完成收口回填：本轮仅写回 `2026-03-16` 已完成的最小验证单元结果，不新增页面、不扩展新链路。

---

## 4. 十天冻结总表

| Day | 日期 | 主题 | 当前状态 | 主入口 |
|---|---|---|---|---|
| FrontDay01 | 2026-03-10 | 文档治理基线、登录与路由冻结 | 已完成并回填 | `demo-admin-ui/docs/frontend-freeze/FrontDay01/README.md` |
| FrontDay02 | 2026-03-11 | 用户与商家管理主链路冻结 | 已完成并回填 | `demo-admin-ui/docs/frontend-freeze/FrontDay02/README.md` |
| FrontDay03 | 2026-03-12 | 商品审核与设置占位冻结 | 已完成并回填 | `demo-admin-ui/docs/frontend-freeze/FrontDay03/README.md` |
| FrontDay04 | 2026-03-13 | 纠纷与违规、运维入口冻结 | 已完成并回填 | `demo-admin-ui/docs/frontend-freeze/FrontDay04/README.md` |
| FrontDay05 | 2026-03-14 | 首页真实接口替换与冻结体系上线 | 已完成并回填 | `demo-admin-ui/docs/frontend-freeze/FrontDay05/README.md` |
| FrontDay06 | 2026-03-15 | 运维中心只读联调与设置边界收口 | 已完成并回填 | `demo-admin-ui/docs/frontend-freeze/FrontDay06/README.md` |
| FrontDay07 | 2026-03-16 | 交互规范与通用状态冻结 | 已完成并回填 | `demo-admin-ui/docs/frontend-freeze/FrontDay07/README.md` |
| FrontDay08 | 2026-03-17 | API 模块治理与错误处理冻结 | 已完成并回填 | `demo-admin-ui/docs/frontend-freeze/FrontDay08/README.md` |
| FrontDay09 | 2026-03-18 | 联调回归与问题清零冻结 | 已完成并回填（2026-03-16 收口确认） | `demo-admin-ui/docs/frontend-freeze/FrontDay09/README.md` |
| FrontDay10 | 2026-03-19 | 演示版冻结与移交 | 已完成并回填（2026-03-16 收口） | `demo-admin-ui/docs/frontend-freeze/FrontDay10/README.md` |

> 注：上表日期列保留原十天编排日序；当前实际收口 / 接棒判断统一以 `2026-03-16` 的冻结结论为准。

---

## 5. 目录规则（冻结）

每个 `FrontDayNN` 文件夹都固定包含以下模块：

1. `01_冻结文档`：定义今天范围、非目标、交付物、退出标准。
2. `02_接口对齐`：定义当天前后端契约、字段口径、已知缺口。
3. `03_API模块`：定义 API 文件改造和字段映射策略。
4. `04_联调准备与验收`：定义环境、回归步骤、验收证据。
5. `05_进度回填`：记录已完成项、待验证项、阻塞与 handoff。

更新原则：

- 先看根 README，再进入当前执行日。
- 先更新 `05_进度回填`，再更新其他模块结论。
- 如果当天真正完成，再把根 README 的“当前执行日”推进到下一天。
- 没有运行证据时，只能写“代码已确认”或“文档已记录”。

---

## 6. 旧文档与新体系映射

| 旧文档 | 新职责 | 现在应该看哪里 |
|---|---|---|
| `demo-admin-ui/docs/plans/short-term-plan.md` | 索引入口 + 历史归档 | 本 README + 当前 `FrontDay` |
| `demo-admin-ui/docs/progress/daily-progress.md` | 进度入口 + 历史归档 | 各日 `05_进度回填` |
| `demo-admin-ui/docs/todos/frontend-todo.md` | 待办索引 | 各日 `01_冻结文档` 和 `05_进度回填` |

---

## 7. 推荐阅读顺序

1. 先读本 README。
2. 再读当前执行日 `FrontDay10/README.md`。
3. 若要追溯为什么 `FrontDay10` 可以接棒，先看 `FrontDay09/05_进度回填/FrontDay09_Progress_Backfill_v1.0.md`。
4. 然后按顺序读 `FrontDay10/01_冻结文档 -> 02_接口对齐 -> 03_API模块 -> 04_联调准备与验收 -> 05_进度回填`。
5. 若要追溯既有业务闭环，再回看 `FrontDay01 ~ FrontDay09`。

---

## 8. 下一次更新时怎么做

1. 先用代码、现有文档和 `FrontDay09` 已有证据确认 Day10 要引用哪些结论。
2. 在 `FrontDay10/05_进度回填` 中优先补 handoff 结论与遗留项分层。
3. 如 Day10 范围有新增运行证据，必须先写回 `05_进度回填`，再同步根 README 与覆盖矩阵。
4. 如范围变化明显，升级当天文档版本号。

---

## 9. 当前结论

- `FrontDay01 ~ FrontDay08` 已完成首次证据化回填并形成稳定前序基线。
- `FrontDay09` 已于 `2026-03-16` 完成收口确认：`Dashboard / UserList / ProductReview / AuditCenter / OpsCenter` 的主链运行态证据已闭环。
- `FrontDay10` 已于 `2026-03-16` 完成“演示版冻结与移交”收口回填：DemoFreeze handoff 文档链验收 + Dashboard 概览只读 + UserList 主动作只读 + ProductReview 审核通过 + OpsCenter 列表/概览只读 + publish-once + refund run-once，最小验证单元均为 `pass`。
- 当前仅剩的后端历史测试源码编译阻塞不属于 `FrontDay10` 前端演示版冻结主链，不影响本轮接棒成立。
