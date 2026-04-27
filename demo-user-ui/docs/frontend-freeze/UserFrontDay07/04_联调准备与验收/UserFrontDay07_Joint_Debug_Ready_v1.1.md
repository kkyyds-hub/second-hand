# UserFrontDay07 联调准备与验收

- 日期：`2026-04-24`
- 文档版本：`v1.1`
- 当前状态：`Package-1 代码已落地 + fresh build pass；Package-1A auth/data 前置已解除，资产读链路 runtime pass`

---

## 1. 本轮执行结论

| 项目 | 结论 | 证据 |
|---|---|---|
| 代码落地 | pass | `src/api/wallet.ts`、`src/api/points.ts`、`src/api/credit.ts`、`src/pages/assets/*`、`src/router/index.ts`、`src/layouts/UserLayout.vue`、`src/pages/AccountCenterPage.vue` |
| fresh build | pass | `demo-user-ui/.tmp_runtime/2026-04-24-userfront-day07-package1-build/build.log`、`build-exit.txt` |
| runtime 真实验证 | pass | `2026-04-26 Package-1A`：真实登录态 + `/assets/wallet`、`/assets/points`、`/assets/credit` 浏览器复跑，主请求均为 200；证据 `demo-user-ui/.tmp_runtime/2026-04-26-userfront-day07-package1A-auth-data-runtime-2026-04-26-225327` |
| blocker | 无 | 旧 `auth-or-data-precondition` 已通过真实 buyer 登录态解除；RabbitMQ 连接告警存在于后端日志但不阻断资产读链路 |

---

## 2. 后续最小验证清单

| 场景 | 计划验证点 | 预期留证 |
|---|---|---|
| 钱包余额 / 流水 | `/assets/wallet` 能加载余额与流水，空态 / 错误态可记录 | 页面截图、network 结果 |
| 提现申请 | `POST /user/wallet/withdraw` 成功 / 失败路径都可记录 | 页面截图、接口结果、失败提示 |
| 积分总额 / 流水 | `/assets/points` 能加载积分总额与流水分页 | 页面截图、network 结果 |
| 信用概览 / 流水 | `/assets/credit` 能加载信用概览与日志分页 | 页面截图、network 结果 |
| 鉴权回归 | `/assets/*` 仍遵守 Day01 登录态与 401 规则 | 路由跳转记录、401 行为说明 |

---

## 3. 验收判定

1. 当前只能写 `代码已落地 + build pass + runtime 待验证`。
2. 后续 verify 线程完成浏览器与真实接口验证后，才能把运行态升级为 pass。
3. 提现申请即使 runtime pass，也只代表申请记录提交成功，不代表真实银行 / 支付渠道出金已打通。


---

## 4. 2026-04-26 Package-1A auth/data 解锁复跑

- 登录态获取：读取 `day10回归/Day10_Local_v2.postman_environment.json` 中 buyer 测试账号，调用 `POST /user/auth/login/password`，返回 `code=1`、token present、`userId=1`、`loginName=buyer01`。
- 证据目录：`demo-user-ui/.tmp_runtime/2026-04-26-userfront-day07-package1A-auth-data-runtime-2026-04-26-225327`。
- 浏览器方式：Vite `http://localhost:5173`，写入 `localStorage.user_token` 与 legacy `authentication` 后打开资产路由。

| 子域 | 页面路由 | 主请求结果 | runtime 结论 |
|---|---|---|---|
| 钱包 | `/assets/wallet` | `/api/user/wallet/balance` 200；`/api/user/wallet/transactions?page=1&pageSize=10` 200 | `pass`：非 401，且返回非空余额 / 流水数据 |
| 积分 | `/assets/points` | `/api/user/points/total` 200；`/api/user/points/ledger?page=1&pageSize=10` 200 | `pass`：非 401，且返回非空总额 / 流水数据 |
| 信用 | `/assets/credit` | `/api/user/credit` 200；`/api/user/credit/logs?page=1&pageSize=10` 200 | `pass`：非 401，且返回非空概览 / 流水数据 |

边界：本节只证明资产中心读链路 runtime；`POST /user/wallet/withdraw` 与真实银行 / 支付渠道出金不在本次 Package-1A 结论内。本节不写 Day07 final acceptance，不推进 Day08。


