# UserFrontDay07 API 模块规划

- 日期：`2026-04-24`
- 文档版本：`v1.1`
- 当前状态：`Package-1 API 模块与页面消费边界已落地，fresh build 已通过，runtime 待验证`

---

## 1. 模块目标

把 `钱包、积分与信用资产视图` 落到清晰的 API 模块、页面消费边界和错误处理规则上；本版已从规划态推进到代码落地态。

---

## 2. 重点文件

| 文件 | 当前状态 | 角色 | 说明 |
|---|---|---|---|
| `demo-user-ui/src/api/wallet.ts` | 已新增 | 承接余额、流水、提现 | 对齐 `WalletController` 三个用户端接口。 |
| `demo-user-ui/src/api/points.ts` | 已新增 | 承接积分总额与积分流水 | 与钱包分开，便于独立回填。 |
| `demo-user-ui/src/api/credit.ts` | 已新增 | 承接信用概览与信用流水 | 补齐此前游离的信用资产视图归属。 |
| `demo-user-ui/src/pages/assets/WalletPage.vue` | 已新增 | 钱包余额、流水、提现申请页面 | 包含分页、空态、错误态与提现表单。 |
| `demo-user-ui/src/pages/assets/PointsPage.vue` | 已新增 | 积分总额与流水页面 | 包含分页、空态、错误态。 |
| `demo-user-ui/src/pages/assets/CreditPage.vue` | 已新增 | 信用概览与流水页面 | 包含等级文案、分页、空态、错误态。 |
| `demo-user-ui/src/router/index.ts` | 已更新 | 资产路由入口 | 新增 `/assets` redirect 与三条子页面路由。 |
| `demo-user-ui/src/layouts/UserLayout.vue` | 已更新 | 主导航入口 | 新增“资产中心”导航。 |
| `demo-user-ui/src/pages/AccountCenterPage.vue` | 已更新 | 账户中心入口 | 新增资产中心入口卡。 |

---

## 3. API 规则

1. 按业务域拆分 API 模块，不把 wallet / points / credit 混进订单或通知模块。
2. 字段适配与错误映射优先留在 API 层，不分散到页面。
3. 页面只声明代码可消费接口；未经 runtime verify 不写真实运行通过。

---

## 4. 当前构建结论

fresh `npm.cmd run build` 已通过，证据：

- `demo-user-ui/.tmp_runtime/2026-04-24-userfront-day07-package1-build/build.log`
- `demo-user-ui/.tmp_runtime/2026-04-24-userfront-day07-package1-build/build-exit.txt`
