# FrontDay09 前后端接口对齐

- 日期：`2026-03-16`
- 文档版本：`v1.0`
- 当前状态：`执行中（Dashboard sellerName、AuditCenter、OpsCenter 写动作口径已补齐）`

---

## 1. 对齐目标

围绕 `联调回归与问题清零冻结`，固定当天前端需要依赖的接口、字段与已知缺口。

---

## 2. 接口清单

| 场景 | 接口 / 契约 | 当前口径 | 备注 |
|---|---|---|---|
| 登录链路 | `POST /admin/employee/login + token 协议` | 回归登录、401、退出。 | 已有运行态证据。 |
| 首页链路 | `/admin/dashboard/overview + /admin/statistics/*` | `reviewQueue[*]` 在 `2026-03-16` 起改为首页专用 VO，卖家字段主口径为 `sellerName`。 | `sellerName` 已于 `2026-03-16` 完成真实页面联调；趋势 / 扩展统计仍待补证据。 |
| 用户链路 | `GET/POST/PUT /admin/user*` | 回归查询、建档、封禁、解封。 | `封禁 / 解封` 已通过真实联调。 |
| 商品审核链路 | `GET/PUT /admin/products/*` | 回归列表、通过、驳回。 | `审核通过 / 驳回` 已通过真实联调。 |
| 纠纷链路 | `GET /admin/audit/overview` + `PUT /admin/after-sales/{afterSaleId}/arbitrate` + `PUT /admin/products/reports/{ticketNo}/resolve` | `DISPUTE` 依赖 `sourceId`，`REPORT` 依赖 `ticketNo`；页面统一走 `src/api/audit.ts` 收口。 | `2026-03-16` 已完成举报 dismiss 与售后仲裁两条真实写动作闭环。 |
| 运维链路 | `/admin/ops/*` | 回归查询、`publish-once` 与至少一项 `run-once`。 | `2026-03-16` 已完成 `publish-once + refund run-once` 真实页面联调，并修正前端任务摘要口径。 |

---

## 3. 当日接口对齐原则

1. 若接口尚未稳定，先写清缺口和兜底策略，再决定是否接入。
2. 运行态未验证时，不把“理论可接”写成“已完成”。
3. 接口变化后，应同步更新对应 API 模块文档和进度回填结论。

---

## 4. 当前已知风险 / 缺口

1. **Dashboard 扩展证据**：`2026-03-16` 已完成 `/admin/dashboard/overview` 中 `reviewQueue[*].sellerName` 的真实页面联调；趋势类与更完整统计覆盖仍待后续补强。
2. **历史举报工单数据质量**：老数据若缺少真实 `ticketNo`，仍可能影响历史工单的直接处理命中；但本轮新建举报工单链路未触发该问题。
3. **RabbitMQ 环境未启**：`localhost:5672` 在 `2026-03-16` 实测不可达；但本轮 `POST /admin/ops/outbox/publish-once?limit=50` 实际返回 `pulled=0 / sent=0 / failed=0`，不阻塞当前 OpsCenter 页面动作链路记为 `pass`。若后续要验证真实消息投递，需要单独启用 MQ。

---

## 5. 2026-03-16 Dashboard reviewQueue 补充对齐

| 面 | 文件 | 当前结论 |
|---|---|---|
| 页面 | `demo-admin-ui/src/pages/Dashboard.vue` | “卖家”列已改消费 `sellerName`，不再继续沿用语义模糊的 `user`。 |
| API 模块 | `demo-admin-ui/src/api/dashboard.ts` | 新增 `reviewQueue` 字段归一：优先读 `sellerName`，同时兼容旧 `user`，用于处理前后端部署窗口不一致。 |
| 请求层 | `demo-admin-ui/src/utils/request.ts` | 本轮未改；Dashboard 仍通过管理端请求头 `token` 访问 `/admin/dashboard/overview`。 |
| 后端 VO | `demo-pojo/src/main/java/com/demo/vo/admin/AdminDashboardOverviewVO.java`、`demo-pojo/src/main/java/com/demo/vo/admin/AdminDashboardReviewQueueItemVO.java` | `reviewQueue` 已从内嵌 `ReviewQueueItem` 拆为 Dashboard 专用 VO，字段主口径显式为 `sellerName`。 |
| 后端服务 | `demo-service/src/main/java/com/demo/service/serviceimpl/AdminDashboardServiceImpl.java` | Dashboard 审核队列已改为直查 `Product` 实体并回填真实卖家展示名，不再复用 ProductReview 的 `ProductDTO ownerId` 兼容链路。 |
| 运行态结论 | `FrontDay09_Joint_Debug_Ready_v1.0.md` | `2026-03-16` 已完成 sellerName 真实页面联调，当前这条子链路可记为 `pass`。 |

---

## 6. 2026-03-16 AuditCenter 写动作补充对齐

| 面 | 文件 | 当前结论 |
|---|---|---|
| 页面 | `demo-admin-ui/src/pages/audit/AuditCenter.vue` | `REPORT` 与 `DISPUTE` 共用 `handleProcess()`，由页面表单状态翻译为不同真实 payload，并在成功后统一 `fetchData()` 回刷。 |
| API 模块 | `demo-admin-ui/src/api/audit.ts` | `REPORT -> PUT /admin/products/reports/{ticketNo}/resolve`；`DISPUTE -> PUT /admin/after-sales/{sourceId}/arbitrate`；`GET /admin/audit/overview` 继续作为列表与详情总入口。 |
| 请求层 | `demo-admin-ui/src/utils/request.ts` | 管理端写动作继续通过请求头 `token` 认证；本轮未改协议。 |
| 后端控制器 | `demo-service/src/main/java/com/demo/controller/admin/AdminAuditController.java`、`demo-service/src/main/java/com/demo/controller/admin/AdminAfterSaleController.java`、`demo-service/src/main/java/com/demo/controller/admin/ProductController.java` | 页面 / API 模块使用的查询、仲裁、举报处理三条接口与控制器暴露口径一致。 |
| 运行态结论 | `FrontDay09_Joint_Debug_Ready_v1.0.md` | `2026-03-16` 已完成 `ticketNo=RPT-20260316-235856` 的举报 dismiss 闭环，以及 `afterSaleId=3 / ticketId=AS-3` 的仲裁 approve 闭环。 |

---

## 7. 2026-03-16 OpsCenter 写动作补充对齐

| 面 | 文件 | 当前结论 |
|---|---|---|
| 页面 | `demo-admin-ui/src/pages/ops/OpsCenter.vue` | `runOpsAction()` 继续统一承接 `publish-once / run-once` 的确认弹窗、按钮 loading、反馈 banner 与成功后回刷；本轮同时把卡片文案改为“未完成 / 待重试任务”，不再把历史总量误写成待处理数。 |
| API 模块 | `demo-admin-ui/src/api/adminExtra.ts` | `fetchOpsRuntimeBundle()` 改为按状态过滤汇总：`ship-timeout -> PENDING`，`refund / ship-reminder -> PENDING + FAILED`；`publishOutboxOnce()` 与 `runRefundOnce()` 的真实接口口径保持不变。 |
| 请求层 | `demo-admin-ui/src/utils/request.ts` | 管理端仍通过请求头 `token` 调用 `/admin/ops/*`；本轮未改认证协议。 |
| 后端控制器 | `demo-service/src/main/java/com/demo/controller/admin/AdminOutboxOpsController.java`、`demo-service/src/main/java/com/demo/controller/admin/AdminTaskOpsController.java` | 页面实际调用的 `POST /admin/ops/outbox/publish-once` 与 `POST /admin/ops/tasks/refund/run-once` 均与控制器暴露路径一致。 |
| 运行态结论 | `FrontDay09_Joint_Debug_Ready_v1.0.md` | `2026-03-16` 已完成真实页面联调：`publish-once` 返回 `pulled=0 / sent=0 / failed=0`，`refund run-once` 返回 `success=0`；页面反馈文案、刷新后的运行概览与后台接口结果一致。 |
