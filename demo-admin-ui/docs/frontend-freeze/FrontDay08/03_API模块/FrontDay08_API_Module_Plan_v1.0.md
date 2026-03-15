# FrontDay08 API 模块计划

- 日期：`2026-03-17`
- 文档版本：`v1.0`
- 当前状态：`已完成并回填`

---

## 1. 模块目标

把当天相关的页面能力落到明确的 API 文件、字段映射、错误处理和 mock/real 策略上；`2026-03-15` 已按“单 API 模块、小步闭环”完成本轮最小治理。

---

## 2. 重点文件

| 文件 | 角色 | 当日结论 |
|---|---|---|
| `demo-admin-ui/src/api/auth.ts` | 登录与鉴权 API | 本轮未改代码；继续保持最小边界，鉴权协议仍由 `auth.ts + request.ts` 统一处理。 |
| `demo-admin-ui/src/api/dashboard.ts` | 首页总览 API | 继续负责首页 overview / 工作队列数据；统计快照失败来源摘要已明确由 `adminExtra.ts` 负责。 |
| `demo-admin-ui/src/api/user.ts` | 用户管理 API | 本轮未扩张修改；用户字段映射仍留在模块层，页面只补错误展示与重试入口。 |
| `demo-admin-ui/src/api/product.ts` | 商品审核 API | 已收口审核状态 / 风险中文文案、筛选映射与字段兜底，`ProductReview.vue` 不再维护协议级文本 switch。 |
| `demo-admin-ui/src/api/audit.ts` | 纠纷与违规 API | 已收口工单类型 / 风险 / 状态中文文案，以及 `getAuditProcessMeta` / `submitAuditAction` 的统一处理边界。 |
| `demo-admin-ui/src/api/adminExtra.ts` | 扩展统计与运维 API | 已收口首页统计快照与运维运行概览的失败来源、失败摘要与结构化 notice，页面直接消费 bundle 结果。 |
| `demo-admin-ui/src/utils/request.ts` | 统一请求与错误处理入口 | Day08 未改协议；继续作为 `code === 1`、401 回登录、HTTP 错误分类打印的共享基线。 |

---

## 3. 当日 API 冻结规则

1. 页面里尽量不直接做协议字段适配；若必须做运行态提示，也先消费 API / request 层给出的结构化结果。
2. 错误消息优先来自 API 或请求层统一处理；页面只补充场景化 fallback，不再各自拼多套文案。
3. mock / real 切换策略继续沿用 `isMockEnabled()` 与 `src/mock/*`，不得因为单页联调临时逻辑破坏全局模式。
4. 本轮未新增 API 文件，也未改后端控制器；实际代码治理仅落在 `adminExtra.ts`、`audit.ts`、`product.ts` 三个模块。

---

## 4. 本轮输出

1. 接口口径与页面行为对应关系已明确，详见 `02_接口对齐`。
2. 字段映射、兜底、失败来源摘要与中文文案的位置已清晰固化在 API 模块。
3. `2026-03-15` 已补齐五页最小范围运行态证据，足以支撑 Day08 关闭；真实写动作与跨页回归转入 FrontDay09。
