# UserFrontDay05 联调准备与验收

- 日期：`2026-04-19`
- 文档版本：`v1.3`
- 当前状态：`进行中（Package-1 + Package-2 + Package-3 已有 runtime 证据）`

---

## 1. 本版目标

在 `v1.2`（Package-1 + Package-2）基础上，补齐 Day05 Package-3（cancel / confirm-receipt）运行态验证，并关闭第三包唯一 blocker（pending / shipped 前置缺失）。

---

## 2. Package-1（沿用既有证据）

| 场景 | 结果 | 证据 |
|---|---|---|
| 未登录访问 `/orders/buyer` 守卫跳转 | pass | `demo-user-ui/.tmp_runtime/2026-04-19-userfront-day05-package1-runtime-verify/summary.md` |
| 买家订单列表只读 | pass | `.../userfront-day05-package1-runtime-verify-result.json`、`.../network/buyer-orders-list-response.json` |
| 买家订单详情只读 | pass | `.../userfront-day05-package1-runtime-verify-result.json`、`.../network/buyer-order-detail-response.json` |

---

## 3. Package-2（沿用既有证据）

| 场景 | 结果 | 证据 |
|---|---|---|
| create（下单）`POST /api/user/orders` | pass | `demo-user-ui/.tmp_runtime/2026-04-19-userfront-day05-package2-runtime-verify/network/create-order-attempt-1-response.json` |
| mock-pay（演示辅助）`POST /api/user/orders/{orderId}/pay/mock?scenario=FAIL` | pass | `demo-user-ui/.tmp_runtime/2026-04-19-userfront-day05-package2-runtime-verify/network/mock-pay-response.json` |
| pay（支付）`POST /api/user/orders/{orderId}/pay` | pass | `demo-user-ui/.tmp_runtime/2026-04-19-userfront-day05-package2-runtime-verify/network/pay-response.json` |

---

## 4. Package-3（本轮新增 runtime）

| 场景 | 结果 | 证据 |
|---|---|---|
| precondition seed（pending / shipped） | pass | `demo-user-ui/.tmp_runtime/2026-04-19-userfront-day05-package3-runtime-verify/precondition-fix-evidence.md`、`.../network/precondition-seed-actions.json` |
| cancel（取消订单）`POST /api/user/orders/{orderId}/cancel` | pass | `demo-user-ui/.tmp_runtime/2026-04-19-userfront-day05-package3-runtime-verify/network/cancel-response.json` |
| confirm-receipt（确认收货）`POST /api/user/orders/{orderId}/confirm-receipt` | pass | `demo-user-ui/.tmp_runtime/2026-04-19-userfront-day05-package3-runtime-verify/network/confirm-receipt-response.json` |
| Package-3 运行总结果 | pass | `demo-user-ui/.tmp_runtime/2026-04-19-userfront-day05-package3-runtime-verify/userfront-day05-package3-runtime-verify-result.json`、`.../summary.md` |

补充：
- precondition seed 先尝试复用既有 paid 订单发货，因卖家权限不匹配返回 `订单不存在或无权查看该订单`；
- 随后改为“seller 在售商品 create -> pay -> ship”最小补数，得到 shipped 订单后复跑 package3，cancel/confirm-receipt 均 pass。

---

## 5. 本轮未覆盖项（明确 not-run）

| 场景 | 当前状态 | 说明 |
|---|---|---|
| 售后申请 / 争议 | not-run | 不在本轮范围 |
| Day05 最终 acceptance | not-run | 本线程不做最终裁定 |

---

## 6. 升级判定（是否进入 `$drive-demo-user-ui-delivery`）

- 结论：`不触发`
- 原因：本轮问题属于 `environment/auth-or-data-precondition`，通过最小前置补数后链路恢复；未出现需要同轮修复的 contract/controller/request-layer 缺陷。

---

## 7. 口径提醒

1. Package-3 `pass` 仅代表 cancel / confirm-receipt 在当前环境已可复现。
2. 不得外推为 Day05 已完成，或整站联调已通过。
3. Day05 最终 acceptance 仍需后续包与专门裁定线程处理。
