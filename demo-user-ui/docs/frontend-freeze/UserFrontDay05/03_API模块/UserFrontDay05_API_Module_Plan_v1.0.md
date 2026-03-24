# UserFrontDay05 API 模块规划

- 日期：`2026-03-18`
- 文档版本：`v1.0`
- 当前状态：`计划中（v1.0 已建档，执行未开始）`

---

## 1. 模块目标

把 `买家订单、支付、取消、确认收货与售后发起` 落到清晰的 API 模块、页面消费边界和错误处理规则上。

---

## 2. 重点文件（规划态）

| 文件 | 当前状态 | 角色 | 说明 |
|---|---|---|---|
| `demo-user-ui/src/api/orders.ts` | 计划新增 | 承接买家订单列表、详情、创建、支付、取消、确认收货 | Day05 执行时创建。 |
| `demo-user-ui/src/api/afterSales.ts` | 计划新增 | 承接售后申请与纠纷发起 | seller decision 留给 Day06。 |
| `demo-user-ui/src/pages/orders/BuyerOrdersPage.vue` | 计划新增 | 买家订单列表主页面 | 当前线程只建规划。 |
| `demo-user-ui/src/pages/orders/BuyerOrderDetailPage.vue` | 计划新增 | 买家订单详情与动作承载页 | 支付、取消、确认收货统一在详情语境中消费。 |
| `demo-user-ui/src/router/index.ts` | Day01 已存在 | 预留订单与售后路由扩展 | 继续复用 Day01 守卫。 |

---

## 3. API 规则

1. 按业务域拆分 API 模块，不把跨日角色混写。
2. 字段适配与错误映射优先留在 API 层，不分散到页面。
3. 未经真实执行，不把规划文件写成已完成实现。

---

## 4. 当前字段与适配口径

- 买家订单列表查询、详情动作和状态栏字段待执行时确认。
- mock 支付必须与真实支付边界分开记录。
- 若执行中发现物流页必须独立存在，应先回填 Day05 与 Day06 的边界。
