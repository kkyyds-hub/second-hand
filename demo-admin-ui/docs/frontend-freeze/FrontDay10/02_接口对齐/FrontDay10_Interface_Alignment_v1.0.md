# FrontDay10 前后端接口对齐

- 日期：`2026-03-16`
- 文档版本：`v1.0`
- 当前状态：`进行中（冻结演示版接口口径）`

---

## 1. 对齐目标

围绕 `演示版冻结与移交`，固定 Day10 需要引用的接口、字段口径、演示展示范围与已知缺口。Day10 **不新增接口范围**，只承接 `2026-03-16` 前已经确认的契约与证据。

---

## 2. 演示版接口冻结清单

| 场景 | 前端文件 | 已确认接口 / 契约 | 当前口径 | 演示版 / 移交说明 | 主要证据 |
|---|---|---|---|---|---|
| 登录与入口 | `src/pages/Login.vue`、`src/router/index.ts` | `POST /admin/employee/login`、本地 token 持久化、路由守卫 | 作为演示入口能力保留，Day10 不追加新增运行态结论 | 演示时只说明“当前入口已接入”，不把 Day10 写成新增联调通过 | `demo-admin-ui/docs/frontend-freeze/FrontDay01/05_进度回填/FrontDay01_Progress_Backfill_v1.0.md` |
| Dashboard 概览 / sellerName / 趋势 / 扩展统计 | `src/pages/Dashboard.vue` | `GET /admin/dashboard/overview?date=2026-03-16`、`GET /admin/statistics/dau`、`GET /admin/statistics/order-gmv`、`GET /admin/statistics/product-publish` | `sellerName`、趋势卡片、扩展统计卡片已于 `2026-03-16` 完成真实页面联调 | `overview.disputeQueue` 为空时按既有设计回退 `/admin/audit/overview?riskLevel=HIGH`；SVG 趋势线仍是 `mockTrendData` 静态装饰，不单列为后端接口能力 | `FrontDay09_dashboard_sellername_2026-03-16.json`、`FrontDay09_dashboard_trend_stats_2026-03-16.json` |
| UserList 封禁 / 解封 | `src/pages/users/UserList.vue` | `POST /admin/users/{id}/ban`、`POST /admin/users/{id}/unban`（以现有 `src/api/user.ts` 实际封装为准） | `active -> banned -> active` 已完成真实接口 smoke | Day10 只整理为演示动作说明，不再把已闭环动作写成“待补” | `FrontDay09_userlist_ban_unban_2026-03-15.json` |
| ProductReview 审核通过 / 驳回 | `src/pages/products/ProductReview.vue` | `POST /admin/products/{id}/approve`、`POST /admin/products/{id}/reject`（以现有 `src/api/product.ts` 实际封装为准） | `under_review -> on_sale / off_shelf` 已完成真实接口 smoke | Day10 只保留演示口径与边界说明；若后续补举报关联处理证据，再另行回填 | `FrontDay09_productreview_approve_reject_2026-03-16.json` |
| AuditCenter 举报 dismiss / 仲裁 approve | `src/pages/audit/AuditCenter.vue` | `PUT /admin/products/reports/{ticketNo}/resolve`、`POST /admin/aftersales/{id}/arbitrate`（以 `src/api/audit.ts` 为准） | 两条主动作已于 `2026-03-16` 完成真实页面联调 | 老工单若缺少真实 `ticketNo` 仍需关注，但不再阻塞当前演示主链 | `FrontDay09_auditcenter_report_dismiss_2026-03-16.json`、`FrontDay09_auditcenter_dispute_arbitrate_2026-03-16.json` |
| OpsCenter publish-once / refund run-once | `src/pages/ops/OpsCenter.vue` | `POST /admin/outbox/publish-once`、`POST /admin/tasks/refund/run-once`（以 `src/api/adminExtra.ts` 为准） | 两条真实页面动作已闭环，运维概览已改为按 `PENDING / FAILED` 汇总 | RabbitMQ `localhost:5672` 当前不可达不影响本轮演示主链；若要验证真实消息投递，需另起任务 | `FrontDay09_opscenter_write_actions_2026-03-16.json` |
| SystemSettings 边界 | `src/pages/settings/SystemSettings.vue` | 当前无稳定真实 settings 接口 | 继续保持占位与边界说明 | 不纳入 Day10 演示主链完成口径，不虚构接口 | `demo-admin-ui/docs/frontend-freeze/FrontDay06/05_进度回填/FrontDay06_Progress_Backfill_v1.0.md` |

---

## 3. Day10 接口对齐原则

1. Day10 不扩展新的接口面，只承接 Day09 已确认能力。
2. 没有新增运行证据时，不把 Day10 写成“新增联调通过”。
3. 如后续出现新的契约变化，先更新 `FrontDay10/05_进度回填`，再决定是否切换到 `drive-demo-admin-ui-delivery`。
4. 所有演示说明都必须保留边界提示，不能把 fallback / 占位 / 风险擦掉。

---

## 4. 当前已知缺口 / 风险

1. **后端历史测试源码编译问题**：影响常规 `mvn clean -DskipTests package` 体验，但不改变 Day10 演示版接口口径。
2. **SystemSettings**：仍无稳定真实接口，不纳入当前演示主链完结口径。
3. **OpsCenter 真实 MQ 投递**：当前未作为本轮必过项，若后续需要验证，必须单独起 MQ 并重新回填证据。
4. **Dashboard SVG 趋势线**：当前仍是静态装饰，不能向接手人或演示对象描述为“已接真实趋势图接口”。
