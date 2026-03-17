# FrontDay10 API 模块计划

- 日期：`2026-03-16`
- 文档版本：`v1.0`
- 当前状态：`进行中（冻结演示版 API handoff 映射）`

---

## 1. 模块目标

Day10 的 API 模块文档不以“新增实现”为目标，而是把已经完成或已经验证过的页面能力，映射回明确的 `src/api/*.ts` 文件、字段口径、页面消费边界与 handoff 入口。

---

## 2. Day10 重点文件映射

| 业务面 | 相关文件 | Day10 处理方式 | handoff 说明 |
|---|---|---|---|
| 登录与鉴权 | `src/api/auth.ts`、`src/utils/request.ts`、`src/router/index.ts` | 作为演示入口与权限边界说明保留 | 不新增登录逻辑改造，只保留入口说明和 token 约束 |
| Dashboard | `src/api/dashboard.ts`、`src/api/adminExtra.ts`、`src/pages/Dashboard.vue` | 固定 `overview + statistics` 的字段映射与证据入口 | 需特别标注 `sellerName` 已闭环、趋势线 SVG 仍是静态装饰 |
| 用户与商家管理 | `src/api/user.ts`、`src/api/adminExtra.ts`、`src/pages/users/UserList.vue` | 以已验证的封禁 / 解封动作为演示基线 | 若后续要补导出或扩展动作证据，另行回填，不在 Day10 虚报 |
| 商品审核 | `src/api/product.ts`、`src/pages/products/ProductReview.vue` | 固定通过 / 驳回主动作与状态流转说明 | 若后续补举报关联处理，再单独补证据，不影响当前演示主链 |
| 审核与仲裁 | `src/api/audit.ts`、`src/pages/audit/AuditCenter.vue` | 固定举报 dismiss / 仲裁 approve 的 API 映射 | 保留 `ticketNo` 质量风险提示，不把历史数据问题删除 |
| 运维中心 | `src/api/adminExtra.ts`、`src/pages/ops/OpsCenter.vue` | 固定 `publish-once / refund run-once` 与概览聚合口径 | MQ 真实投递不属于本轮必过项，需单独说明 |
| 系统设置边界 | `src/pages/settings/SystemSettings.vue` | 仅保留“占位页 / 边界说明” | 不新增 `src/api/settings.ts` 或任何伪接口文档 |
| 文档入口 | `demo-admin-ui/docs/frontend-freeze/README.md`、`FrontDay10/*` | 统一为 Day10 handoff 主入口 | 接手人先看文档，再决定是否需要改代码 |

---

## 3. Day10 API 规则

1. Day10 不做大范围 API 重构，不把文档治理日写成 API 改造日。
2. 页面对接口的说明必须与现有 `src/api/*.ts` 和已回填证据一致。
3. 如果后续需要重新改字段映射、改接口路径或补真实联调，必须先回填 `FrontDay10/05_进度回填`。
4. 共享请求治理、错误态与重试策略继续沿用 `FrontDay08` 结论，Day10 不重复造规则。

---

## 4. Day10 不做的 API 工作

1. 不新增 mock / real 切换策略。
2. 不新增未验证的 API 文件拆分。
3. 不把后端历史测试源码编译问题记成前端 API 模块未完成。
4. 不为了“演示好看”而删除已有 fallback、边界说明或风险提示。

---

## 5. 预期输出

1. 接手人能快速定位：某个演示页面对应哪个 API 文件、哪个证据文档、哪个已知边界。
2. Day10 所有“已纳入演示主链”的页面，都能追溯到具体 API 文件与具体证据路径。
3. 若后续要真正动代码，能明确知道应该继续使用哪个技能，而不是在 Day10 文档里混做开发。
