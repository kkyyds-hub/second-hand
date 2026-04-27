# UserFrontDay05 联调准备与验收

- 日期：`2026-04-19`
- 文档版本：`v1.2`
- 当前状态：`进行中（Package-1 + Package-2 已有 runtime 证据）`

---

## 1. 本版目标

在 `v1.1`（Package-1 只读）基础上，补齐 Day05 Package-2 的运行态证据，并保持 Day05 边界不外溢。

---

## 2. Package-1（沿用既有证据）

| 场景 | 结果 | 证据 |
|---|---|---|
| 未登录访问 `/orders/buyer` 守卫跳转 | pass | `demo-user-ui/.tmp_runtime/2026-04-19-userfront-day05-package1-runtime-verify/summary.md` |
| 买家订单列表只读 | pass | `.../userfront-day05-package1-runtime-verify-result.json`、`.../network/buyer-orders-list-response.json` |
| 买家订单详情只读 | pass | `.../userfront-day05-package1-runtime-verify-result.json`、`.../network/buyer-order-detail-response.json` |

---

## 3. Package-2（本轮新增 runtime）

| 场景 | 结果 | 证据 |
|---|---|---|
| create（下单）`POST /api/user/orders` | pass | `demo-user-ui/.tmp_runtime/2026-04-19-userfront-day05-package2-runtime-verify/network/create-order-attempt-1-response.json` |
| mock-pay（演示辅助）`POST /api/user/orders/{orderId}/pay/mock?scenario=FAIL` | pass | `demo-user-ui/.tmp_runtime/2026-04-19-userfront-day05-package2-runtime-verify/network/mock-pay-response.json` |
| pay（支付）`POST /api/user/orders/{orderId}/pay` | pass | `demo-user-ui/.tmp_runtime/2026-04-19-userfront-day05-package2-runtime-verify/network/pay-response.json` |
| Package-2 运行总结果 | pass | `demo-user-ui/.tmp_runtime/2026-04-19-userfront-day05-package2-runtime-verify/userfront-day05-package2-runtime-verify-result.json`、`.../summary.md` |

补充：首轮候选商品（历史订单来源）出现 `code=0 / 商品非在售状态`，已通过 market 候选重试后获得稳定 pass（最终 productId=`920086`，orderId=`907603`）。

---

## 4. 本轮未覆盖项（明确 not-run）

| 场景 | 当前状态 | 说明 |
|---|---|---|
| 取消订单 | not-run | 不在本轮范围 |
| 确认收货 | not-run | 不在本轮范围 |
| 售后申请 / 争议 | not-run | 不在本轮范围 |
| Day05 最终 acceptance | not-run | 本线程不做最终裁定 |

---

## 5. 升级判定（是否进入 `$drive-demo-user-ui-delivery`）

- 结论：`不触发`
- 原因：本轮未发现需要同轮修复的 contract/controller/request-layer/auth 问题；`create/pay/mock-pay` 已形成独立 runtime truth。

---

## 6. 口径提醒

1. Package-2 `pass` 仅代表 create/pay/mock-pay 三子流在当前环境可复现。
2. 不得外推为 Day05 已完成，或整站联调已通过。
3. Day05 最终 acceptance 仍需后续包与专门裁定线程处理。
