# UserFrontDay07 前后端接口对齐

- 日期：`2026-04-24`
- 文档版本：`v1.1`
- 当前状态：`Package-1 接口消费代码已落地，fresh build 已通过，runtime 真实验证待后续执行`

---

## 1. 本版对齐目标

围绕 `钱包、积分与信用资产视图` 完成用户端最小前端闭环的接口消费代码落地，并保留 runtime 待验证边界。

---

## 2. 接口清单与前端消费

| 场景 | 接口 / 契约 | 前端消费文件 | 当前状态 |
|---|---|---|---|
| 钱包余额 | `GET /user/wallet/balance` | `src/api/wallet.ts`、`src/pages/assets/WalletPage.vue` | 代码已落地 + build pass，runtime 待验证 |
| 钱包流水 | `GET /user/wallet/transactions?page=&pageSize=` | `src/api/wallet.ts`、`src/pages/assets/WalletPage.vue` | 代码已落地 + build pass，runtime 待验证 |
| 提现申请 | `POST /user/wallet/withdraw`，body: `amount` / `bankCardNo` | `src/api/wallet.ts`、`src/pages/assets/WalletPage.vue` | 代码已落地 + build pass，runtime 待验证；不代表真实出金 |
| 积分总额 | `GET /user/points/total` | `src/api/points.ts`、`src/pages/assets/PointsPage.vue` | 代码已落地 + build pass，runtime 待验证 |
| 积分流水 | `GET /user/points/ledger?page=&pageSize=` | `src/api/points.ts`、`src/pages/assets/PointsPage.vue` | 代码已落地 + build pass，runtime 待验证 |
| 信用概览 | `GET /user/credit` | `src/api/credit.ts`、`src/pages/assets/CreditPage.vue` | 代码已落地 + build pass，runtime 待验证 |
| 信用流水 | `GET /user/credit/logs?page=&pageSize=` | `src/api/credit.ts`、`src/pages/assets/CreditPage.vue` | 代码已落地 + build pass，runtime 待验证 |

---

## 3. 字段适配口径

1. 钱包金额在 API 层同时保留 string 与 number 展示字段，页面负责格式化展示。
2. 提现请求只提交 `amount` 与 `bankCardNo`，并在 API 层做最小校验。
3. 积分流水消费 `bizType`、`bizId`、`points`、`createTime`。
4. 信用概览消费 `creditScore`、`creditLevel`、`creditUpdatedAt`；等级文案由前端做保守映射。
5. 信用流水消费 `delta`、`reasonType`、`refId`、`scoreBefore`、`scoreAfter`、`reasonNote`、`createTime`。

---

## 4. 边界说明

- 继续复用 Day01 的鉴权、401 与 `authentication` 请求头规则。
- 当前未做 browser runtime，不写 runtime pass。
- 若后续 verify 发现接口字段或业务语义偏差，应在 Day07 继续回填修正。
