# UserFrontDay03 联调准备与验收

- 日期：`2026-04-22`
- 文档版本：`v1.2`
- 当前状态：`已完成并回填（Day03 同域 focused regression + final acceptance 已完成）`

---

## 1. 联调前置条件（执行线程）

1. Day02 仍保持“待最终裁定”口径，不在 Day03 线程重写 Day02 结论。
2. Day01 登录态、路由守卫、request 共享层保持可用。
3. 相关 controller 可达：`MarketProductController`、`FavoriteController`、`ReviewController`。

---

## 2. 本轮执行结果（focused regression + final acceptance）

| 子流 | scope | observed behavior | evidence level | 结论 | owner | reason |
|---|---|---|---|---|---|---|
| 市场列表 / 筛选 / 分页 | `/market`；`GET /user/market/products?page=1&pageSize=12` + `page=2` + `keyword=DAY1` | `page=1.total=44`；`page=2` 请求命中；`keyword=DAY1` 过滤请求命中；收藏状态读取命中 `12` 次 | runtime（browser + network + screenshot） | `pass` | - | - |
| 商品详情 + 评论 page=1 + 收藏状态读取 | `/market/920086`；`GET /user/market/products/920086` + `GET /user/market/products/920086/reviews?page=1&pageSize=10` + `GET /user/favorites/920086/status` | 详情、评论第一页、收藏状态读取均命中 `200/code=1` | runtime（browser + network + screenshot） | `pass` | - | - |
| 收藏 / 取消收藏最小闭环 | `/market/920086`；`POST /user/favorites/920086` + `DELETE /user/favorites/920086` | 两个写请求均命中 `200/code=1`；初始按钮文案为 `收藏`；鉴权头 `authentication` 正常携带 | runtime（browser + network + screenshot） | `pass` | - | - |
| 收藏列表最小映射 | `/favorites`；`GET /user/favorites` | 收藏动作后两次进入 `/favorites`，列表 GET 均命中，路由可达 | runtime（browser + network + screenshot） | `pass` | - | - |
| 评论列表分页 `page=2` 定向验证 | `/market/920086`；`GET /user/market/products/920086/reviews?page=1&pageSize=10` + `...page=2&pageSize=10` | `page=1.total=12`；下一页按钮存在且可点击；点击后 `page=2` 请求命中并返回 `code=1,data.page=2` | runtime（browser + network + screenshot） | `pass` | - | - |
| 评论提交（固定 `orderId=1` 首次尝试） | `/market/920086`；`POST /user/reviews` | 请求命中；返回 `code=0,msg=该订单已评价`；说明旧脚本的固定订单号已失效 | runtime（browser + network + json） | `blocked` | `environment` | `auth-or-data-precondition` |
| 评论提交（动态候选订单 targeted retry） | `/market/920069`；只读探针筛出 `orderId=907608/productId=920069` 后执行 `POST /user/reviews` | 已完成未评价候选订单探针命中；评论提交返回 `200/code=1`；鉴权头正常携带 | runtime（api-probe + browser + network + screenshot） | `pass` | - | - |
| 举报提交 | `/market/920086`；`POST /user/market/products/920086/report` | 请求命中并返回 `200/code=1`，工单号 `RPT-20260422-339194` | runtime（browser + network + screenshot） | `pass` | - | - |
| 我的评价入口 | `/reviews/mine`；`GET /user/reviews/mine` | 路由可达；请求命中 `200/code=1`；页面标题可见 | runtime（browser + network + screenshot） | `pass` | - | - |

补充说明：

1. `orderId=1` 的 `blocked` 已确认属于**旧验证前置失效**，不是前端实现缺陷，也不是后端 contract 冲突；本轮已通过动态候选订单复验关闭该 blocker。
2. 本轮未发现 `MarketProductController` / `FavoriteController` / `ReviewController` 契约冲突，不升级到 `$drive-demo-user-ui-delivery`。
3. 本轮结论仅覆盖 Day03 owned scope，可写为 `已完成并回填`；仍不等于 Day04/Day05/整站联调已通过。

---

## 3. 验收边界

1. 本轮只裁定 Day03 自身 owned scope：市场浏览/详情、评论与举报、收藏夹。
2. 本轮不重写 Day02“待最终裁定”口径，不切换 root active day。
3. 本轮不外推 Day04+、Day09、Day10 结论。

---

## 4. 证据最小清单（本轮已落地）

1. build truth：
   - `demo-user-ui/.tmp_runtime/2026-04-22-userfront-day03-focused-regression/frontend-build.log`
2. package1 focused regression：
   - `demo-user-ui/.tmp_runtime/2026-04-22-userfront-day03-focused-regression/package1/userfront-day03-package1-focused-runtime-result.json`
   - `demo-user-ui/.tmp_runtime/2026-04-22-userfront-day03-focused-regression/package1/summary.md`
3. 评论分页 `page=2` 定向验证：
   - `demo-user-ui/.tmp_runtime/2026-04-22-userfront-day03-focused-regression/page2/userfront-day03-review-page2-targeted-verify-result.json`
   - `demo-user-ui/.tmp_runtime/2026-04-22-userfront-day03-focused-regression/page2/summary.md`
4. package2 既有三子流复跑：
   - `demo-user-ui/.tmp_runtime/2026-04-22-userfront-day03-focused-regression/package2/userfront-day03-package2-runtime-verify-result.json`
   - `demo-user-ui/.tmp_runtime/2026-04-22-userfront-day03-focused-regression/package2/summary.md`
5. 评论提交动态候选订单 retry：
   - `demo-user-ui/.tmp_runtime/2026-04-22-userfront-day03-focused-regression/review-submit-retry/userfront-day03-review-submit-candidate-runtime-result.json`
   - `demo-user-ui/.tmp_runtime/2026-04-22-userfront-day03-focused-regression/review-submit-retry/summary.md`

---

## 5. 最终验收口径

1. Day03 最终口径可升级为 `已完成并回填`。
2. 根因：同域 focused regression 已真实执行，且所有 owned subflow 均有 `pass` 证据；唯一中途出现的 `orderId=1` blocker 已通过动态候选订单复验确认只是环境/数据前置，不是代码缺陷。
3. 升级路由结论：`不触发 $drive-demo-user-ui-delivery`。
