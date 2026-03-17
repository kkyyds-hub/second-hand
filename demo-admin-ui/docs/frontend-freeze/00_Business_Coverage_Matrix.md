# demo-admin-ui 前端业务覆盖总表

> 主入口仍然是 `demo-admin-ui/docs/frontend-freeze/README.md`
> 本表是按业务域查看覆盖情况的辅助总账，不替代各 `FrontDayNN` 文档。
> 最后更新：`2026-03-16`

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
| Auth 与登录 | 登录提交 / token 保存 / 退出登录 | `src/pages/Login.vue`、`src/pages/LogoutPage.vue`、`src/router/index.ts` | `src/api/auth.ts`、`src/utils/request.ts`、`EmployeeController.java` | 已完成并回填 | 代码已确认 + 文档已记录 | FrontDay01 | 2026-03-16 | `demo-admin-ui/docs/frontend-freeze/FrontDay01/05_进度回填/FrontDay01_Progress_Backfill_v1.0.md` | 运行态验收证据仍可继续补强 | 纳入 FrontDay10 演示入口说明，不单列新增运行态通过 |
| 路由与导航 | 左侧菜单、登录守卫、退出可达性 | `src/layouts/MainLayout.vue`、`src/router/index.ts`、`src/pages/LogoutPage.vue` | `src/utils/request.ts` | 已完成并回填 | 代码已确认 + 文档已记录 | FrontDay01 | 2026-03-16 | `demo-admin-ui/docs/frontend-freeze/FrontDay01/05_进度回填/FrontDay01_Progress_Backfill_v1.0.md` | 跨页跳转仍建议在演示前做入口级走查 | 纳入 FrontDay10 演示顺序与页面可达性说明 |
| Dashboard | 首页概览、sellerName、趋势 / 扩展统计 | `src/pages/Dashboard.vue` | `src/api/dashboard.ts`、`src/api/adminExtra.ts`、`AdminDashboardController.java`、`StatisticsController.java` | 已完成并回填 | 代码已确认 + 运行态已确认 | FrontDay05 | 2026-03-16 | `demo-admin-ui/docs/frontend-freeze/FrontDay09/05_进度回填/FrontDay09_Progress_Backfill_v1.0.md`、`demo-admin-ui/docs/frontend-freeze/FrontDay09/04_联调准备与验收/runtime-artifacts/2026-03-16/FrontDay09_dashboard_sellername_2026-03-16.json`、`demo-admin-ui/docs/frontend-freeze/FrontDay09/04_联调准备与验收/runtime-artifacts/2026-03-16/FrontDay09_dashboard_trend_stats_2026-03-16.json` | `overview.disputeQueue` 为空时仍按既有设计回退 `/admin/audit/overview?riskLevel=HIGH`；SVG 趋势线仍是 `Dashboard.vue` 内 `mockTrendData` 静态装饰，不应误记为后端实时趋势图能力 | 转入 FrontDay10 演示版冻结与移交；若后续需要真实趋势图接口，另行立项 |
| 用户与商家管理 | 列表、封禁/解封、导出与扩展管理入口 | `src/pages/users/UserList.vue` | `src/api/user.ts`、`src/api/adminExtra.ts`、`UserController.java`、`AdminCreditController.java`、`ViolationController.java` | 已完成并回填 | 代码已确认 + 运行态已确认 | FrontDay02 | 2026-03-16 | `demo-admin-ui/docs/frontend-freeze/FrontDay02/05_进度回填/FrontDay02_Progress_Backfill_v1.0.md`、`demo-admin-ui/docs/frontend-freeze/FrontDay09/04_联调准备与验收/runtime-artifacts/2026-03-15/FrontDay09_userlist_ban_unban_2026-03-15.json` | 封禁/解封主链已闭环；建档 / 导出与跨控制器扩展动作仍可按需补页面级证据 | 转入 FrontDay10 演示版冻结与移交；若要扩证导出或扩展动作，再单独回填 |
| 商品审核与举报处理 | 审核列表、通过/驳回、举报关联处理 | `src/pages/products/ProductReview.vue` | `src/api/product.ts`、`ProductController.java` | 已完成并回填 | 代码已确认 + 运行态已确认 | FrontDay03 | 2026-03-16 | `demo-admin-ui/docs/frontend-freeze/FrontDay03/05_进度回填/FrontDay03_Progress_Backfill_v1.0.md`、`demo-admin-ui/docs/frontend-freeze/FrontDay09/04_联调准备与验收/runtime-artifacts/2026-03-16/FrontDay09_productreview_approve_reject_2026-03-16.json` | 审核通过/驳回已于 `2026-03-16` 完成真实接口 smoke；举报关联处理与更完整页面级证据仍可继续补强 | 转入 FrontDay10 演示版冻结与移交；若后续补举报关联证据，再回写本表 |
| 纠纷、仲裁与违规中心 | 审核中心、举报 dismiss、售后仲裁 approve | `src/pages/audit/AuditCenter.vue` | `src/api/audit.ts`、`AdminAuditController.java`、`AdminAfterSaleController.java`、`ProductController.java` | 已完成并回填 | 联调已通过 | FrontDay04 | 2026-03-16 | `demo-admin-ui/docs/frontend-freeze/FrontDay04/05_进度回填/FrontDay04_Progress_Backfill_v1.0.md`、`demo-admin-ui/docs/frontend-freeze/FrontDay09/04_联调准备与验收/runtime-artifacts/2026-03-16/FrontDay09_auditcenter_report_dismiss_2026-03-16.json`、`demo-admin-ui/docs/frontend-freeze/FrontDay09/04_联调准备与验收/runtime-artifacts/2026-03-16/FrontDay09_auditcenter_dispute_arbitrate_2026-03-16.json` | 主链已闭环；历史老工单若缺少真实 `ticketNo` 仍需持续关注，但不再阻塞当前前端演示主链 | 转入 FrontDay10 演示版冻结与移交，保持风险提示不被删掉 |
| 运维中心 | 概览、任务快照、publish-once / refund run-once | `src/pages/ops/OpsCenter.vue` | `src/api/adminExtra.ts`、`AdminOutboxOpsController.java`、`AdminTaskOpsController.java`、`AdminOrderController.java` | 已完成并回填 | 代码已确认 + 运行态已确认 | FrontDay06 | 2026-03-16 | `demo-admin-ui/docs/frontend-freeze/FrontDay06/05_进度回填/FrontDay06_Progress_Backfill_v1.0.md`、`demo-admin-ui/docs/frontend-freeze/FrontDay09/04_联调准备与验收/runtime-artifacts/2026-03-16/FrontDay09_opscenter_write_actions_2026-03-16.json` | 当前真实页面动作链路已闭环；若要验证真实 MQ 投递，需要单独起 RabbitMQ，不属于当前演示主链必过项 | 转入 FrontDay10 演示版冻结与移交；若后续要补真实消息投递，再单独立项 |
| 系统设置 | 占位页、边界确认、非目标约束 | `src/pages/settings/SystemSettings.vue` | 当前无稳定真实 settings 接口 | 进行中 | 代码已确认 + 文档已记录 | FrontDay06 | 2026-03-16 | `demo-admin-ui/docs/frontend-freeze/FrontDay06/05_进度回填/FrontDay06_Progress_Backfill_v1.0.md` | 后端能力尚未落地，不应虚构 settings API，也不应纳入 Day10 演示主链完成口径 | 维持边界说明，并在 FrontDay10 handoff 中明确“不纳入演示主链” |
| 共享交互规范 | 标题、表单、表格、弹窗、按钮、状态反馈统一 | `src/style.css`、多个 `src/pages/...` 页面 | 前端共享规则，无单独控制器 | 已完成并回填 | 代码已确认 + 文档已记录 | FrontDay07 | 2026-03-16 | `demo-admin-ui/docs/frontend-freeze/FrontDay07/05_进度回填/FrontDay07_Progress_Backfill_v1.0.md` | 新页面接入时仍可能出现局部样式漂移 | 作为 FrontDay10 演示基线继续沿用，后续新增页面必须遵守 Day07 规范 |
| 共享 API 模块治理 | 列表错误态、重试入口、字段映射、页面消费边界 | `src/api/*.ts`、`src/pages/users/UserList.vue`、`src/utils/request.ts` | 多控制器共享请求治理 | 已完成并回填 | 代码已确认 + 运行态已确认 | FrontDay08 | 2026-03-16 | `demo-admin-ui/docs/frontend-freeze/FrontDay08/05_进度回填/FrontDay08_Progress_Backfill_v1.0.md`、`demo-admin-ui/docs/frontend-freeze/FrontDay08/04_联调准备与验收/runtime-artifacts/2026-03-15/FrontDay08_runtime_verification_2026-03-15.json` | Day08 范围内的页面错误处理、最小 API 模块治理与只读 / 低风险运行态证据已闭环；如接口再变，不应直接在 Day10 文档里虚报通过 | 作为 FrontDay10 演示链路的共享治理基线；若需跨端修复，切换到 `drive-demo-admin-ui-delivery` |
| 演示版冻结与移交 | 演示范围、证据入口、handoff 口径、遗留项分层 | `demo-admin-ui/docs/frontend-freeze/README.md`、`demo-admin-ui/docs/frontend-freeze/FrontDay10/*` | 不新增控制器；以 Day09 已确认接口与证据为准，并回填 Day10 最小浏览器验证主链结果 | 已完成并回填 | 文档已记录 + 运行态已确认 | FrontDay10 | 2026-03-16 | `demo-admin-ui/docs/frontend-freeze/FrontDay10/README.md`、`demo-admin-ui/docs/frontend-freeze/FrontDay10/05_进度回填/FrontDay10_Progress_Backfill_v1.0.md`、`demo-admin-ui/docs/frontend-freeze/FrontDay09/04_联调准备与验收/runtime-artifacts/2026-03-15/FrontDay09_userlist_ban_unban_2026-03-15.json`、`demo-admin-ui/docs/frontend-freeze/FrontDay09/04_联调准备与验收/runtime-artifacts/2026-03-16/FrontDay09_dashboard_sellername_2026-03-16.json`、`demo-admin-ui/docs/frontend-freeze/FrontDay09/04_联调准备与验收/runtime-artifacts/2026-03-16/FrontDay09_dashboard_trend_stats_2026-03-16.json`、`demo-admin-ui/docs/frontend-freeze/FrontDay09/04_联调准备与验收/runtime-artifacts/2026-03-16/FrontDay09_productreview_approve_reject_2026-03-16.json`、`demo-admin-ui/docs/frontend-freeze/FrontDay09/04_联调准备与验收/runtime-artifacts/2026-03-16/FrontDay09_opscenter_write_actions_2026-03-16.json` | RabbitMQ `localhost:5672` 连接拒绝日志不是 Day10 主链直接阻塞；Dashboard 趋势 SVG 仍来自 `mockTrendData` 静态装饰；`SystemSettings` 不纳入 Day10 主链完结口径；后端历史测试源码编译问题不属于 Day10 前端演示冻结主链 | 转入冻结后维护；若后续出现新增跨端修复/新增运行回归，先回填 `FrontDay10/05_进度回填`，再切换 `drive-demo-admin-ui-delivery` |

## 当前空白检查

- `FrontDay10` 已于 `2026-03-16` 完成“演示版冻结与移交”收口回填，当前进入冻结后维护阶段。
- Day10 最小浏览器验证主链（Dashboard / UserList / ProductReview / OpsCenter 列表概览与写动作）均已 `pass`，且本轮不新增运行验证链路。
- 当前遗留仅剩后端历史测试源码编译问题的独立治理，以及 `SystemSettings` 边界说明的持续保持；两者都不应被写成 Day10 已完成业务项。
