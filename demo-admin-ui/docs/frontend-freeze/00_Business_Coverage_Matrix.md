# demo-admin-ui 前端业务覆盖总表

> 主入口仍然是 `demo-admin-ui/docs/frontend-freeze/README.md`
> 本表是按业务域查看覆盖情况的辅助总账，不替代各 `FrontDayNN` 文档。
> 最后更新：2026-03-15

## 使用规则

- 一行对应一个有交付意义的业务子流，而不只是一个页面文件名。
- `当前状态` 表示业务推进状态，`证据等级` 表示确认强度，两者不要混写。
- 当代码或联调证据变化时，先更新对应 `FrontDayNN/05_进度回填`，再同步本表。
- 没有运行态证据时，不得写成 `联调已通过`。

## 状态枚举

- `已完成并回填`
- `代码已完成待运行验证`
- `进行中`
- `计划中`
- `阻塞`

## 证据等级枚举

- `文档已记录`
- `代码已确认`
- `构建已通过`
- `运行态已确认`
- `联调已通过`

## 覆盖矩阵

| 业务域 | 子流/页面 | 前端面 | 接口/控制器面 | 当前状态 | 证据等级 | 当前归属 FrontDay | 最后更新日期 | 主要证据 | 主要缺口/风险 | 下一动作 |
|---|---|---|---|---|---|---|---|---|---|---|
| Auth 与登录 | 登录提交 / token 保存 / 退出登录 | `src/pages/Login.vue`, `src/pages/LogoutPage.vue`, `src/router/index.ts` | `src/api/auth.ts`, `src/utils/request.ts`, `EmployeeController.java` | 已完成并回填 | 代码已确认 + 文档已记录 | FrontDay01 | 2026-03-15 | `demo-admin-ui/docs/frontend-freeze/FrontDay01/05_进度回填/FrontDay01_Progress_Backfill_v1.0.md` | 运行态验收证据仍可继续补强 | 纳入 FrontDay09 回归抽查 |
| 路由与导航 | 左侧菜单、登录守卫、退出可达性 | `src/layouts/MainLayout.vue`, `src/router/index.ts`, `src/pages/LogoutPage.vue` | `src/utils/request.ts` | 已完成并回填 | 代码已确认 + 文档已记录 | FrontDay01 | 2026-03-15 | `demo-admin-ui/docs/frontend-freeze/FrontDay01/05_进度回填/FrontDay01_Progress_Backfill_v1.0.md` | 跨页跳转仍建议在回归日复核 | 纳入 FrontDay09 回归抽查 |
| Dashboard | 首页概览与统计卡片 | `src/pages/Dashboard.vue` | `src/api/dashboard.ts`, `src/api/adminExtra.ts`, `AdminDashboardController.java`, `StatisticsController.java` | 进行中 | 代码已确认 + 部分运行态已确认 | FrontDay05 | 2026-03-15 | `demo-admin-ui/docs/frontend-freeze/FrontDay05/README.md`, `demo-admin-ui/docs/frontend-freeze/FrontDay05/04_联调准备与验收/FrontDay05_Joint_Debug_Ready_v1.0.md` | 趋势类与扩展统计证据仍待继续补强 | 继续 FrontDay05 / FrontDay09 的运行验证 |
| ç¨æ·ä¸åå®¶ç®¡ç | åè¡¨ãç¶ææä½ãå¯¼åºä¸æ©å±ç®¡çå
| 用户与商家管理 | 列表、状态操作、导出与扩展管理入口 | `src/pages/users/UserList.vue` | `src/api/user.ts`, `src/api/adminExtra.ts`, `UserController.java`, `AdminCreditController.java`, `ViolationController.java` | 已完成并回填 | 代码已确认 + 运行态已确认 | FrontDay02 | 2026-03-15 | `demo-admin-ui/docs/frontend-freeze/FrontDay02/05_进度回填/FrontDay02_Progress_Backfill_v1.0.md`, `demo-admin-ui/docs/frontend-freeze/FrontDay09/05_进度回填/FrontDay09_Progress_Backfill_v1.0.md`, `demo-admin-ui/docs/frontend-freeze/FrontDay09/04_联调准备与验收/runtime-artifacts/2026-03-15/FrontDay09_userlist_ban_unban_2026-03-15.json` | UserList 封禁/解封已于 2026-03-15 完成真实接口 smoke；建档 / 导出与跨控制器扩展动作仍可按需补页面级证据 | 继续 FrontDay09 其余写动作回归 |
¶ä½åå¨ä½åå½ |
| 商品审核与举报处理 | 审核列表、通过/驳回、违规关联处理 | `src/pages/products/ProductReview.vue` | `src/api/product.ts`, `ProductController.java` | 已完成并回填 | 代码已确认 + 文档已记录 | FrontDay03 | 2026-03-15 | `demo-admin-ui/docs/frontend-freeze/FrontDay03/05_进度回填/FrontDay03_Progress_Backfill_v1.0.md`, `demo-admin-ui/docs/backend-real-linkup.md` | 驳回理由与兼容字段仍建议联调复核 | 纳入 FrontDay09 联调回归 |
| 纠纷、仲裁与违规中心 | 审核中心、售后仲裁、违规视图 | `src/pages/audit/AuditCenter.vue` | `src/api/audit.ts`, `AdminAuditController.java`, `AdminAfterSaleController.java`, `ProductController.java` | 已完成并回填 | 代码已确认 + 文档已记录 | FrontDay04 | 2026-03-15 | `demo-admin-ui/docs/frontend-freeze/FrontDay04/05_进度回填/FrontDay04_Progress_Backfill_v1.0.md` | `ticketNo` 等后端数据质量仍需运行态复核 | 纳入 FrontDay09 联调回归 |
| 运维中心 | 只读概览、任务快照、操作入口边界 | `src/pages/ops/OpsCenter.vue` | `src/api/adminExtra.ts`, `AdminOutboxOpsController.java`, `AdminTaskOpsController.java`, `AdminOrderController.java` | 进行中 | 代码已确认 + 运行态已确认 | FrontDay06 | 2026-03-15 | `demo-admin-ui/docs/frontend-freeze/FrontDay06/04_联调准备与验收/FrontDay06_Joint_Debug_Ready_v1.0.md`, `demo-admin-ui/docs/frontend-freeze/FrontDay06/05_进度回填/FrontDay06_Progress_Backfill_v1.0.md` | 动作型接口和 run-once 类操作还未宣称全量完成 | 在 FrontDay09 明确动作型回归范围 |
| 系统设置 | 占位页、边界确认、非目标约束 | `src/pages/settings/SystemSettings.vue` | 当前无稳定真实 settings 接口 | 进行中 | 代码已确认 + 文档已记录 | FrontDay06 | 2026-03-15 | `demo-admin-ui/docs/frontend-freeze/FrontDay06/05_进度回填/FrontDay06_Progress_Backfill_v1.0.md` | 后端能力尚未落地，不应虚构 settings API | 维持边界说明，等待后端或需求变化 |
| 共享交互规范 | 标题、表单、表格、弹窗、按钮、状态反馈统一 | `src/style.css`, 多个 `src/pages/...` 页面 | 前端共享规则，无单独控制器 | 已完成并回填 | 代码已确认 + 文档已记录 + 部分运行态已确认 | FrontDay07 | 2026-03-15 | `demo-admin-ui/docs/frontend-freeze/FrontDay07/05_进度回填/FrontDay07_Progress_Backfill_v1.0.md` | 新页面接入时仍可能出现局部样式漂移 | 后续新页面必须按 Day07 规范落地 |
| 共享 API 模块治理 | 列表错误态、重试入口、字段映射、页面消费边界 | `src/api/*.ts`, `src/pages/users/UserList.vue`, `src/utils/request.ts` | 多控制器共享请求治理 | 已完成并回填 | 代码已确认 + 运行态已确认 | FrontDay08 | 2026-03-15 | `demo-admin-ui/docs/frontend-freeze/FrontDay08/05_进度回填/FrontDay08_Progress_Backfill_v1.0.md`, `demo-admin-ui/docs/frontend-freeze/FrontDay08/04_联调准备与验收/FrontDay08_Joint_Debug_Ready_v1.0.md`, `demo-admin-ui/docs/frontend-freeze/FrontDay08/04_联调准备与验收/runtime-artifacts/2026-03-15/FrontDay08_runtime_verification_2026-03-15.json` | Day08 范围内的页面错误处理、最小 API 模块治理与只读 / 低风险运行态证据已闭环；真实写动作仍在各业务域后续回归范围内 | 转入 FrontDay09 做动作型回归；若同轮要修接口或跨端推进，切到 `drive-demo-admin-ui-delivery` |

## 当前空白检查

- 目前主要缺口不在“是否有页面”，而在“动作型联调证据、趋势 / 扩展场景与跨页回归是否足够完整”。
- Dashboard、运维中心仍属于重点跟进项；共享 API 模块治理已完成 Day08 范围内闭环。
- 若后续新增业务域，必须先补到本表，再决定是否拆入新的 FrontDay 范围。
