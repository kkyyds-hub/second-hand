# UserFrontDay06 联调准备与验收

- 日期：`2026-04-22`
- 文档版本：`v1.1`
- 当前状态：`进行中（Package-1 seller orders / logistics / ship 已完成 runtime verify；Day06 其余子域未验收）`

---

## 1. 本轮验证范围

### 已验证

1. 未登录访问 `/orders/seller` 的守卫回跳
2. 卖家订单列表 `GET /api/user/orders/sell`
3. 卖家订单详情 `GET /api/user/orders/{orderId}`
4. 卖家物流查看 `GET /api/user/orders/{orderId}/logistics`
5. 卖家发货 `POST /api/user/orders/{orderId}/ship`
6. 发货后详情 / 物流回读

### 未纳入本轮

1. `seller decision`
2. `order messages`
3. Day06 final acceptance

---

## 2. 前置条件与 seed 说明

- 本轮使用了最小 precondition seed。
- 原因不是“完全没有 paid 单”，而是：历史 paid 单 `900041` 存在，但不在卖家订单列表首屏 10 条窗口内，无法用一条干净的 `list -> detail -> ship` 链路完成 Package-1 运行态验证。
- 因此通过现有业务 API 新建并支付了一条 fresh paid order `907609`，不做数据库直改、不改业务代码。

关键证据：

- `demo-user-ui/.tmp_runtime/2026-04-22-userfront-day06-package1-runtime-verify/network/precondition-seed-actions.json`
- `demo-user-ui/.tmp_runtime/2026-04-22-userfront-day06-package1-runtime-verify/precondition-fix-evidence.md`

---

## 3. Package-1 子流判定

| 子流 | 结论 | 说明 |
|---|---|---|
| auth guard | pass | `/orders/seller` 未登录回跳 `/login?redirect=/orders/seller` |
| seller list | pass | fresh paid order `907609` 出现在列表首屏并可点击进入 |
| seller detail | pass | 详情页以 `paid` 状态加载成功 |
| logistics before ship | pass | 发货前物流接口可读，trace 为空但不构成失败 |
| ship submit | pass | `POST /api/user/orders/907609/ship` 返回 `200 / code=1` |
| logistics after ship | pass | 发货后详情 / 物流均回读为 `shipped`，且快照包含 `YTO + trackingNo` |

---

## 4. 本轮验收口径

1. 可以把 `seller orders / logistics / ship` 这一条 Day06 Package-1 业务包写成 `已完成并回填`。
2. 不能把 `UserFrontDay06` 整体写成 `已完成并回填`。
3. `seller decision` 与 `order messages` 仍应保持 `待后续包继续`。

---

## 5. 关键证据路径

- build：`demo-user-ui/.tmp_runtime/2026-04-22-userfront-day06-package1-runtime-verify/build.log`
- dev：`demo-user-ui/.tmp_runtime/2026-04-22-userfront-day06-package1-runtime-verify/dev.log`
- runtime result：`demo-user-ui/.tmp_runtime/2026-04-22-userfront-day06-package1-runtime-verify/userfront-day06-package1-runtime-verify-result.json`
- summary：`demo-user-ui/.tmp_runtime/2026-04-22-userfront-day06-package1-runtime-verify/summary.md`
- screenshots：
  - `.../screenshots/auth-guard-orders-seller.png`
  - `.../screenshots/seller-orders-list.png`
  - `.../screenshots/seller-order-detail-before-ship.png`
  - `.../screenshots/seller-order-detail-after-ship.png`

---

## 6. 后续动作

1. 继续把 `seller decision` 从 Day05 buyer 发起侧中独立出来做 Day06 后续包验证。
2. 再单独推进 `order messages`，不要和本轮已完成的 seller fulfillment Package-1 混写。
