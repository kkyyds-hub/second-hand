# FrontDay09 API 模块计划

- 日期：`2026-03-16`
- 文档版本：`v1.0`
- 当前状态：`执行中（Dashboard / AuditCenter / OpsCenter 的真实映射口径已补到运行证据）`

---

## 1. 模块目标

把当天相关的页面能力落到明确的 API 文件、字段映射、错误处理和 mock/real 策略上。

---

## 2. 重点文件

| 文件 | 角色 | 当日要求 |
|---|---|---|
| `demo-admin-ui/docs/checklists/joint-debug-checklist.md` | 现有联调清单基础 | Day09 需升级成按页面执行记录。 |
| `demo-admin-ui/docs/backend-real-linkup.md` | 接口参考 | 辅助定位真实接口。 |
| `demo-admin-ui/src/pages/* + src/api/*` | 联调执行对象 | 按问题现象回到对应页面/API 文件。 |
| `demo-admin-ui/src/api/dashboard.ts` | Dashboard 聚合接口消费层 | 负责把后端 `sellerName` / 旧 `user` 统一归一成页面安全字段。 |
| `demo-admin-ui/src/api/audit.ts` | AuditCenter 写动作收口层 | 负责统一 `GET /admin/audit/overview`、`PUT /admin/after-sales/{id}/arbitrate`、`PUT /admin/products/reports/{ticketNo}/resolve`。 |
| `demo-admin-ui/src/api/adminExtra.ts` | OpsCenter 运行概览与动作层 | 负责按状态过滤运维任务摘要，并收口 `publish-once / run-once` 请求。 |
| `demo-admin-ui/src/pages/ops/OpsCenter.vue` | OpsCenter 页面消费层 | 负责把运行概览摘要、动作确认与执行反馈稳定展示给运营。 |
| `demo-service/src/main/java/com/demo/service/serviceimpl/AdminDashboardServiceImpl.java` | Dashboard 聚合服务 | 审核队列改走专用 VO + `Product` 实体查询，不再借道 ProductReview DTO。 |

---

## 3. 当日 API 规则

1. Day09 只能基于真实执行结果下结论。
2. 代码存在但运行失败的项必须标记为未通过或阻塞。
3. 回归问题要能对应到具体页面/API/接口，不留模糊表述。

---

## 4. 预期输出

1. 接口口径与页面行为对应关系明确。
2. 字段映射、兜底、错误处理位置清晰。
3. 后续若改代码，可直接定位到当天关联文件。

---

## 5. 2026-03-16 Dashboard reviewQueue 模块补记

1. **前端 API 层**：`src/api/dashboard.ts` 已新增 `normalizeReviewQueue()`，把后端 `sellerName` 作为主字段消费，并保留旧 `user` 兼容回退。
2. **页面消费层**：`src/pages/Dashboard.vue` 与 `src/mock/dashboard.ts` 已统一改用 `sellerName`，避免“卖家”列继续挂在泛化字段 `user` 上。
3. **后端聚合层**：`AdminDashboardReviewQueueItemVO` 已独立承接首页审核队列字段，`AdminDashboardServiceImpl` 改为直接查询 `Product` 并回填卖家展示名。
4. **当前结论边界**：这条 Dashboard 子链路已于 `2026-03-16` 完成真实页面运行证据补充，可在 Day09 范围内记为 `pass`；未完成的仅剩趋势类与扩展统计补强。

---

## 6. 2026-03-16 AuditCenter API 模块补记

1. **统一处理入口**：`src/api/audit.ts` 的 `submitAuditAction()` 已在真实模式下稳定区分 `DISPUTE` 与 `REPORT` 两类动作，并分别映射到售后仲裁与举报处理接口。
2. **页面门禁口径**：`getAuditProcessMeta()` 对 `DISPUTE sourceId` 与 `REPORT ticketNo` 的前置校验已被真实联调验证，页面不会在关键主键缺失时误放开处理按钮。
3. **运行态证据**：`2026-03-16` 已通过真实页面证据确认：
   - `RPT-20260316-235856 -> PUT /admin/products/reports/{ticketNo}/resolve`
   - `AS-3 -> PUT /admin/after-sales/{afterSaleId}/arbitrate`
4. **当前遗留边界**：AuditCenter API 模块在 Day09 范围内已无新的协议偏差；后续剩余补强项集中在 Dashboard 扩展证据，不再属于 `src/api/audit.ts` 范围。

---

## 7. 2026-03-16 OpsCenter API 模块补记

1. **运行摘要修正**：`src/api/adminExtra.ts` 本轮新增 `OpsTaskListParams` 与按状态聚合 helper，把 `ship-timeout PENDING`、`refund PENDING + FAILED`、`ship-reminder PENDING + FAILED` 汇总成页面摘要，避免把历史 `SUCCESS / DONE / CANCELLED` 记录误算成待处理数。
2. **页面消费修正**：`src/pages/ops/OpsCenter.vue` 已把卡片文案改为“未完成 / 待重试任务”，并在文案中显式说明 `run-once` 只会处理当前到期或可执行批次。
3. **真实动作证据**：`2026-03-16` 已通过真实页面验证：
   - `POST /admin/ops/outbox/publish-once?limit=50`
   - `POST /admin/ops/tasks/refund/run-once?limit=50`
4. **当前结论边界**：OpsCenter 页面 / API 模块 / 控制器口径一致；本轮没有新增后端契约变更，变化属于**前端摘要映射修正**。
