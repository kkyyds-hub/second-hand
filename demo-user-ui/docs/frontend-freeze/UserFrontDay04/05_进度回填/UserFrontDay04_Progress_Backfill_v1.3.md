# UserFrontDay04 进度回填

- 日期：`2026-04-18`
- 文档版本：`v1.3`

---

## 1. 当前判定

- 总结：`UserFrontDay04` 已完成第一包只读 runtime verify 的 docs 回填，并完成第二包写链路 runtime verify 的 docs-only 回填（主链 `blocked`，补充 existing-actions 为 `pass`）。
- 当前状态：`进行中（第二包已完成并回填；create 仍 blocked；Day04 全量范围未完成）`
- root active day：仍为 `UserFrontDay02`（待最终裁定），本次未切换。

---

## 2. 本轮新增回填（第二包，基于既有 runtime 结果，不重跑）

| 项目 | 判定 | 证据等级 | 证据路径 | 回填说明 |
|---|---|---|---|---|
| 第二包主链（create->edit->状态流转->delete） | `blocked` | `runtime(build+dev+browser)` | `demo-user-ui/.tmp_runtime/2026-04-18-userfront-day04-package2-runtime-verify/userfront-day04-package2-runtime-verify-result.json`、`demo-user-ui/.tmp_runtime/2026-04-18-userfront-day04-package2-runtime-verify/summary.md`、`demo-user-ui/.tmp_runtime/2026-04-18-userfront-day04-package2-runtime-verify/network/`、`demo-user-ui/.tmp_runtime/2026-04-18-userfront-day04-package2-runtime-verify/screenshots/` | 主链 verdict=`blocked`，`escalation.needDriveDelivery=false`。 |
| create（`POST /api/user/products`） | `blocked` | `runtime(browser+network)` | 同上（重点：`network/create-post-request.json`、`network/create-post-response.json`） | 请求已触发且鉴权头存在；后端返回 `code=0`，报文为“信用等级过低（LV1），暂不可发布商品”。 |
| 第二包 existing-actions（基于现有 off_shelf 商品） | `pass` | `runtime(browser+network+screenshot)` | `demo-user-ui/.tmp_runtime/2026-04-18-userfront-day04-package2-runtime-verify/userfront-day04-package2-existing-actions-result.json`、`demo-user-ui/.tmp_runtime/2026-04-18-userfront-day04-package2-runtime-verify/existing-actions-summary.md`、`demo-user-ui/.tmp_runtime/2026-04-18-userfront-day04-package2-runtime-verify/network/`、`demo-user-ui/.tmp_runtime/2026-04-18-userfront-day04-package2-runtime-verify/screenshots/` | 选取 `productId=920050` 后，`edit/withdraw/resubmit/off-shelf/on-shelf/delete` 均返回 `code=1`。 |

补充说明：

- 第二包主链里 `edit/delete/off-shelf/resubmit/on-shelf/withdraw` 的 blocked 根因是 `missing createdProductId`（由 create blocked 传导）；
- build/dev/browser 执行记录已在证据目录中留存，但本线程未重跑。

---

## 3. 当前唯一 blocker / 剩余边界

| 项目 | 当前判定 | 说明 |
|---|---|---|
| 唯一 blocker：create 前置条件 | `blocked` | 后端 `owner=backend`、`reason=auth-or-data-precondition`，当前账号信用等级不足（LV1）导致无法创建新商品。 |
| Day04 写链路全量闭环 | 未完成 | 由于缺失 `createdProductId`，第二包主链无法完成“从新建商品出发”的 edit/delete/状态流转闭环验证。 |
| Day04 最终 acceptance | 未开始 | 本线程明确不做 acceptance。 |

---

## 4. 升级判断（是否升级到 `$drive-demo-user-ui-delivery`）

- 结论：`未触发升级`。
- 理由：第二包证据中 `needDriveDelivery=false`，且当前 blocker 被归类为“账号/数据前置条件”，未发现需同轮修复的 contract/controller/request-layer 真问题。

---

## 5. 本次回填声明

1. 未改 `demo-user-ui/src/**` 或 `demo-service/**` 实现代码。
2. 未重跑 build/dev/browser/runtime（仅回填既有证据）。
3. 未做最终 acceptance。
4. 未将 Day04 写成“已完成”或 root active day。
