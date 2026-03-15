# FrontDay08 前后端接口对齐

- 日期：`2026-03-17`
- 文档版本：`v1.0`
- 当前状态：`已完成并回填`

---

## 1. 对齐目标

围绕 `API 模块治理与错误处理冻结`，固定当天前端需要依赖的接口、字段与已知缺口；`2026-03-15` 已按最小闭环完成本轮对齐结论。

---

## 2. 接口清单

| 场景 | 接口 / 契约 | 当前口径 | 备注 |
|---|---|---|---|
| 统一返回结构 | `{ code, msg, data }` | `src/utils/request.ts` 继续按 `code === 1` 视为成功，其余业务码统一抛出 `Error`。 | Day08 未改请求层协议，只补齐页面与 API 模块的消费说明。 |
| HTTP 异常状态码 | `401/403/404/500` | 继续沿用 `request.ts` 的统一处理；页面只补 `resolvePageErrorMessage`、banner、空态与重试入口。 | `2026-03-15` 只读验收未发现新增协议阻塞。 |
| Dashboard 统计快照 bundle | `/api/admin/statistics/dau`、`/api/admin/statistics/order-gmv`、`/api/admin/statistics/product-publish` | `adminExtra.ts` 统一输出 `snapshot / availability / failedSources / failureSummary / notice`；`Dashboard.vue` 只组合展示结果。 | 允许部分成功，不再在页面层维护 `availability -> 中文来源文案` 映射。 |
| OpsCenter 运行概览 bundle | `/api/admin/ops/outbox/metrics`、`/api/admin/ops/tasks/*`、`/api/admin/orders`、`/api/admin/users/user-violations/statistics` | `adminExtra.ts` 统一输出 `OpsRuntimeBundle`；页面直接消费结构化提示对象。 | `publish-once` / `run-once` 未执行，不影响 Day08 关闭。 |
| AuditCenter 工单处理契约 | `/api/admin/audit/overview`、`/api/admin/after-sales/{sourceId}/arbitrate`、`/api/admin/products/reports/{ticketNo}/resolve` | `audit.ts` 统一收口类型 / 风险 / 状态中文文案，以及 `getAuditProcessMeta` 的处理可用性判断。 | `DISPUTE` 依赖 `sourceId`；`REPORT` 依赖 `ticket.id`；`RISK` 仍为只读观察。 |
| ProductReview 状态映射 | `/api/admin/products/pending-approval`、`/api/admin/products/{id}/approve`、`/api/admin/products/{id}/reject` | `product.ts` 统一商品审核状态、风险等级中文文案、筛选映射与字段兜底。 | 本轮只验列表与失败态，未执行真实审核写动作。 |
| mock / real 模式 | `isMockEnabled()` + 环境文件 + `src/mock/*` | 保持现有切换模式；`2026-03-15` 在 `npm run dev:real` 下完成五页真实只读验收，并用前端本地 `code=0` 做低风险错误态模拟。 | 不因单页改造破坏全局切换策略；仓库下未见独立 `mock-server` 目录，本轮无需额外调整。 |

---

## 3. 当日接口对齐结论

1. 请求层协议未改，Day08 的治理重点是 API 模块与页面消费层，不额外扩张到新的前后端契约改造。
2. 后端路径差异、字段兜底、失败来源摘要与中文文案继续优先收口在 API 模块，不再下沉到页面模板或弹窗内重复维护。
3. 未执行真实写动作的项统一维持 `risk-controlled` 结论，并转入 FrontDay09 / `drive-demo-admin-ui-delivery`，不在 Day08 内冒进升级结论。

---

## 4. 当前已知风险 / 缺口

1. UserList 的新建 / 封禁 / 解封 / 导出、ProductReview 的通过 / 驳回、AuditCenter 的仲裁 / 举报处理、OpsCenter 的 `publish-once` / `run-once` 仍未在 `2026-03-15` 执行真实写动作。
2. Dashboard 趋势类与扩展统计证据仍需在 FrontDay05 / FrontDay09 继续补强，不应因 Day08 完成而被误判为全量闭环。
3. Audit 工单的 `ticketNo` / `sourceId` 数据质量、Ops 动作副作用窗口，仍应在下一轮联调回归中重点复核。
