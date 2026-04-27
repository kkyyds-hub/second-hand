# UserFrontDay05 联调准备与验收

- 日期：`2026-04-19`
- 文档版本：`v1.4`
- 当前状态：`已具备收口材料，待最终裁定（Package-1~Package-4 已有 runtime 证据）`

---

## 1. 本版目标

在 `v1.3`（Package-1 + Package-2 + Package-3）基础上，补齐 Day05 Package-4（after-sale apply / dispute initiate）运行态验证，并明确 dispute 的 seller-reject 仅作为本轮 verify precondition seed，不外推为 Day06 完成。

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

## 4. Package-3（沿用既有证据）

| 场景 | 结果 | 证据 |
|---|---|---|
| precondition seed（pending / shipped） | pass | `demo-user-ui/.tmp_runtime/2026-04-19-userfront-day05-package3-runtime-verify/precondition-fix-evidence.md`、`.../network/precondition-seed-actions.json` |
| cancel（取消订单）`POST /api/user/orders/{orderId}/cancel` | pass | `demo-user-ui/.tmp_runtime/2026-04-19-userfront-day05-package3-runtime-verify/network/cancel-response.json` |
| confirm-receipt（确认收货）`POST /api/user/orders/{orderId}/confirm-receipt` | pass | `demo-user-ui/.tmp_runtime/2026-04-19-userfront-day05-package3-runtime-verify/network/confirm-receipt-response.json` |
| Package-3 运行总结果 | pass | `demo-user-ui/.tmp_runtime/2026-04-19-userfront-day05-package3-runtime-verify/userfront-day05-package3-runtime-verify-result.json`、`.../summary.md` |

---

## 5. Package-4（本轮新增 runtime）

| 场景 | 结果 | 证据 |
|---|---|---|
| precondition seed（fresh completed order for apply） | pass | `demo-user-ui/.tmp_runtime/2026-04-19-userfront-day05-package4-runtime-verify/precondition-fix-evidence.md`、`.../network/precondition-seed-actions.json` |
| after-sale apply（buyer）`POST /api/user/after-sales` | pass | `demo-user-ui/.tmp_runtime/2026-04-19-userfront-day05-package4-runtime-verify/network/after-sale-apply-response.json`、`.../screenshots/after-sale-apply-after-submit.png` |
| dispute seller-reject precondition seed（verify-only）`PUT /api/user/after-sales/{afterSaleId}/seller-decision` | pass | `demo-user-ui/.tmp_runtime/2026-04-19-userfront-day05-package4-runtime-verify/network/dispute-precondition-seller-reject-response.json` |
| dispute initiate（buyer）`POST /api/user/after-sales/{afterSaleId}/dispute` | pass | `demo-user-ui/.tmp_runtime/2026-04-19-userfront-day05-package4-runtime-verify/network/dispute-initiate-response.json`、`.../screenshots/dispute-after-submit.png` |
| Package-4 运行总结果 | pass | `demo-user-ui/.tmp_runtime/2026-04-19-userfront-day05-package4-runtime-verify/userfront-day05-package4-runtime-verify-result.json`、`.../summary.md` |

补充：
- 本轮 `afterSaleId=6` 来自 after-sale apply 成功响应；
- dispute 前置 seller-reject 仅用于本轮 verify seed，不构成 Day06 卖家售后处理完成结论；
- 本轮线程未做最终 acceptance 裁定。

---

## 6. 本轮未覆盖项（明确 not-run）

| 场景 | 当前状态 | 说明 |
|---|---|---|
| Day05 最终 acceptance | not-run | 本线程不做最终裁定，仅形成收口材料 |

---

## 7. 升级判定（是否进入 `$drive-demo-user-ui-delivery`）

- 结论：`不触发`
- 原因：Package-4 两个子流均 pass，且 blocker 已通过 precondition seed 闭环；未出现需要同轮修复的 contract/controller/request-layer 缺陷。

---

## 8. 口径提醒

1. Day05 当前仅可提升为 `已具备收口材料，待最终裁定`，不得写成 `已完成并回填` 或 `整站联调已通过`。
2. dispute 的 seller-reject 仅是本轮 verify seed，不得外推为 Day06 完成。
3. Day05 最终 acceptance 仍需独立裁定线程执行。

