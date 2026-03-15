# 前端冻结文档主入口

> 启用日期：2026-03-14
> 适用项目：`demo-admin-ui`
> 当前唯一主入口：`demo-admin-ui/docs/frontend-freeze/README.md`

这套文档用于统一管理 `demo-admin-ui` 的前端计划、冻结、联调、回填与移交。
从 2026-03-14 起，旧的 `short-term-plan.md`、`daily-progress.md`、`frontend-todo.md` 只保留索引和归档职责，**不再作为主计划来源**。

---

## 1. 这套体系解决什么问题

1. 解决前端计划散落在多个说明型文档里的问题。
2. 解决“已经做了但没有回填”的问题。
3. 解决“今天到底做什么、做完怎么记、下一天如何续写”的问题。
4. 解决前后端联调和演示阶段缺少统一口径的问题。

---

## 2. 当前推荐执行日

- 当前日期：`2026-03-15`
- 当前执行日：`FrontDay09`
- 当前执行主题：`联调回归与问题清零冻结`
- 当前状态：`计划中`
- 进入入口：`demo-admin-ui/docs/frontend-freeze/FrontDay09/README.md`
- 当日重点：在 FrontDay08 已完成页面错误处理收口、最小 API 模块治理，以及 `Dashboard / UserList / ProductReview / AuditCenter / OpsCenter` 五页只读 / 低风险运行态验收的基础上，优先对真实写动作、趋势 / 扩展证据补强与跨页问题清零做联调回归。

---

## 3. 当前前端基线（2026-03-15）

| 模块 | 当前状态 | 证据等级 | 主要证据 |
|---|---|---|---|
| 登录与鉴权 | 已完成并回填 | 代码已确认 + 文档已记录 | `src/pages/Login.vue`、`src/api/auth.ts`、`src/utils/request.ts`、`src/router/index.ts` |
| 路由与导航 | 已完成并回填 | 代码已确认 | `src/layouts/MainLayout.vue`、`src/router/index.ts`、`src/pages/LogoutPage.vue` |
| 用户与商家管理 | 已完成并回填 | 代码已确认 + 文档已记录 | `src/pages/users/UserList.vue`、`src/api/user.ts` |
| 商品审核 | 已完成并回填 | 代码已确认 + 文档已记录 | `src/pages/products/ProductReview.vue`、`src/api/product.ts`、`docs/backend-real-linkup.md` |
| 纠纷与违规 | 已完成并回填 | 代码已确认 + 文档已记录 | `src/pages/audit/AuditCenter.vue`、`src/api/audit.ts` |
| Dashboard | 进行中（趋势与扩展运行证据待补强） | 代码已确认 + 部分真实接口已验证 | `src/pages/Dashboard.vue`、`src/api/dashboard.ts`、`src/api/adminExtra.ts`、`docs/frontend-freeze/FrontDay05/04_联调准备与验收/FrontDay05_Joint_Debug_Ready_v1.0.md` |
| 运维中心 | 进行中（只读主线已闭环，写动作待后续验证） | 代码已确认 + 运行态已确认 | `src/pages/ops/OpsCenter.vue`、`src/api/adminExtra.ts`、`docs/frontend-freeze/FrontDay06/04_联调准备与验收/FrontDay06_Joint_Debug_Ready_v1.0.md` |
| 系统设置 | 进行中（静态边界已确认） | 代码已确认 + 文档已记录 | `src/pages/settings/SystemSettings.vue`、`docs/frontend-freeze/FrontDay06/05_进度回填/FrontDay06_Progress_Backfill_v1.0.md` |

说明：

- `已完成并回填`：当前范围内已纳入冻结体系并有证据回填。
- `进行中`：主线已推进，但仍有后续范围、补充验证或下一天任务待继续。
- `代码已确认 + 运行态已确认`：实现已经落地，且已补到真实运行证据。

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
| FrontDay09 | 2026-03-18 | 联调回归与问题清零冻结 | 计划中 | `demo-admin-ui/docs/frontend-freeze/FrontDay09/README.md` |
| FrontDay10 | 2026-03-19 | 演示版冻结与移交 | 计划中 | `demo-admin-ui/docs/frontend-freeze/FrontDay10/README.md` |

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
2. 再读当前执行日 `FrontDay09/README.md`。
3. 如果要承接 FrontDay08 的 API 模块治理、错误处理与运行证据，再看 `FrontDay08/README.md`。
4. 再按顺序读：`01_冻结文档 -> 02_接口对齐 -> 03_API模块 -> 04_联调准备与验收 -> 05_进度回填`。
5. 若要追溯已完成内容，再回看 FrontDay01 ~ FrontDay08。

---

## 8. 下一次更新时怎么做

1. 先用代码和现有文档确认今天哪些项已完成。
2. 在当天 `05_进度回填` 中补证据。
3. 如果当天已完成，再把根 README 推进到下一天。
4. 如果范围变化明显，升级当天文档版本号。

---

## 9. 当前结论

- FrontDay01 ~ FrontDay04 已完成首次证据化回填。
- FrontDay05 已完成并回填：Dashboard `overview + statistics` 的真实只读接口边界、真实 `0` 值展示口径、根 freeze README 上线与旧文档索引化改造都已闭环；更完整的趋势与扩展运行证据转入后续执行日继续补强。
- FrontDay06 已完成并回填：OpsCenter 六个 GET 只读接口的真实联调证据、聚合快照容错和 SystemSettings 静态边界都已闭环；写动作验证另起任务，不再阻塞 Day06 关闭。
- FrontDay07 已完成并回填：共享标题、状态标签、提示反馈、弹窗、表格状态、表单控件、禁用态、提交 loading、筛选条与复杂弹窗表单的统一规范已落地，关键页面与核心弹窗已完成多轮业务化收口，并补齐窄范围运行态证据。
- FrontDay08 已完成并回填：`UserList.vue`、`AuditCenter.vue`、`ProductReview.vue`、`Dashboard.vue` 与 `OpsCenter.vue` 的页面错误处理收口，以及 `src/api/adminExtra.ts`、`src/api/audit.ts`、`src/api/product.ts` 的最小 API 模块治理已闭环；`2026-03-15` 的真实只读 / 低风险错误态验收 5 项均为 `pass`，真实写动作转入 FrontDay09。
- FrontDay09 已成为当前推荐执行日：聚焦真实写动作、动作型接口、趋势 / 扩展证据补强与跨页问题清零。
- FrontDay10 仍处于详细预规划阶段。
