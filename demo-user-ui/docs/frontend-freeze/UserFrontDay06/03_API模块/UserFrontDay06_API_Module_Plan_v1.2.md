# UserFrontDay06 API 模块计划

- 日期：`2026-04-24`
- 文档版本：`v1.2`
- 当前状态：`Package-1 seller fulfillment 已有 runtime 回填；Package-2 order messages 已代码+build 落地；Package-3 seller decision 已代码+build 落地，尚未做 runtime verify`

---

## 1. 文档目标

在不改后端的前提下，冻结 Day06 当前 API 模块落地现状，并补记 `seller decision` 最小包已经接入前端的真实范围。

---

## 2. 模块现状（截至 2026-04-24）

| 文件 / 页面 | 当前状态 | 说明 |
|---|---|---|
| `demo-user-ui/src/api/orders.ts` | 已落地 | 承接 seller orders / detail / logistics / ship，Package-1 已做 runtime 回填。 |
| `demo-user-ui/src/api/messages.ts` | 已落地 | 承接 order messages 的 list / send / mark-as-read，当前仅有 build 证据。 |
| `demo-user-ui/src/api/afterSales.ts` | 已扩展 | 本轮新增 seller decision 提交方法，继续保留 buyer apply / buyer dispute。 |
| `demo-user-ui/src/pages/orders/SellerAfterSaleDecisionPage.vue` | 已新增 | 独立 seller decision 页面，只承接手动输入 / URL 预填 `afterSaleId` 后提交。 |
| `demo-user-ui/src/router/index.ts` | 已更新 | 新增 `/orders/seller/after-sales/decision` 路由。 |
| `demo-user-ui/src/pages/seller/SellerWorkbenchPage.vue` | 已更新 | 新增 seller decision 入口卡片。 |

---

## 3. seller decision 契约冻结

### 3.1 接口

- `PUT /user/after-sales/{afterSaleId}/seller-decision`

### 3.2 请求体

| 字段 | 类型 | 口径 |
|---|---|---|
| `approved` | `boolean` | 必填；`true=同意`，`false=拒绝` |
| `remark` | `string` | 可选；前端限制 `<= 200` 个字符 |

### 3.3 前端入口口径

1. 页面入口：`/orders/seller/after-sales/decision`
2. `afterSaleId` 来源仅允许：
   - 手动输入
   - URL query 预填：`?afterSaleId=xxx`
3. 当前明确不做：
   - seller after-sale 查询接口
   - 从订单详情推断或伪造 `afterSaleId`
   - runtime verify / final acceptance

---

## 4. 模块边界

1. 不改后端 seller decision 契约。
2. 不把系统通知中心并入 Day06；该范围仍留给 Day08。
3. 不因 seller decision 落地而把 Day06 整体标记为完成。
4. 所有运行态结论都继续以后续 verify 线程为准。

---

## 5. 当前遗留项

| 项目 | 当前状态 | 备注 |
|---|---|---|
| `order messages` runtime verify | 未完成 | 仅有代码与 build 事实。 |
| `seller decision` runtime verify | 未完成 | 本轮明确不跑浏览器验证。 |
| Day06 final acceptance | 未完成 | 需等待剩余 verify 线程。 |
