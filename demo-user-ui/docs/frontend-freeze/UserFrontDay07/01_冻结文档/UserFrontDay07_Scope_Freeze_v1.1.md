# UserFrontDay07 范围冻结（Scope Freeze）

- 日期：`2026-04-24`
- 文档版本：`v1.1`
- 当前状态：`Package-1 资产中心最小完整切片已代码落地，fresh build 已通过，runtime 真实验证留给后续 verify 线程`
- 当日主题：`钱包、积分与信用资产视图`

---

## 1. 本版起点结论

`UserFrontDay07 Package-1` 承接资产中心最小完整切片：

1. 钱包：余额、流水、提现申请。
2. 积分：积分总额、积分流水。
3. 信用：信用概览、信用流水。

本版只声明代码落地与 fresh build pass；未声明 runtime pass，也不把提现写成真实金融出金链路。

---

## 2. Package-1 做什么

| 范围分类 | 本版落地内容 | 前端入口 |
|---|---|---|
| 钱包 | 余额卡、流水列表、分页、提现表单、成功/失败提示 | `src/pages/assets/WalletPage.vue`、`/assets/wallet` |
| 积分 | 积分总额卡、积分流水列表、分页、空态/错误态 | `src/pages/assets/PointsPage.vue`、`/assets/points` |
| 信用视图 | 信用分概览、等级展示、信用流水列表、分页、空态/错误态 | `src/pages/assets/CreditPage.vue`、`/assets/credit` |
| 入口 | Layout 导航与账户中心入口 | `src/layouts/UserLayout.vue`、`src/pages/AccountCenterPage.vue` |

---

## 3. Package-1 不做什么

1. 不把真实银行 / 支付渠道提现写成已打通；当前只提交后端提现申请记录。
2. 不回写 Day05 支付结果或 Day06 履约逻辑。
3. 不把资产页存在提前写成 runtime / 联调已通过。
4. 不把系统通知中心带进来。
5. 不推进 `UserFrontDay08`。

---

## 4. 当前证据口径

- 已具备：前端 API 模块、页面、路由、导航入口、账户中心入口、fresh build 通过证据。
- 尚未具备：浏览器真实运行、接口真实返回截图、network 证据、runtime artifacts。
- 因此当前三层状态为：代码已落地 `是`、构建已通过 `是`、运行态已验证 `否（留给后续 verify）`。
