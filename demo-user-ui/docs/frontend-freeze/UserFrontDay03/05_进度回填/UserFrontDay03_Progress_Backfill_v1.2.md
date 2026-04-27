# UserFrontDay03 进度回填

- 日期：`2026-04-22`
- 文档版本：`v1.2`
- 当前状态：`已完成并回填（Day03 focused regression + final acceptance 已完成）`

---

## 1. 本轮结论（focused regression + final acceptance）

1. 本轮先重跑 `npm.cmd run build`，得到 `pass`，build 证据落地到 `demo-user-ui/.tmp_runtime/2026-04-22-userfront-day03-focused-regression/frontend-build.log`。
2. 本轮以 Day03 同域 focused regression 复跑市场浏览 / 详情 / 收藏域：
   - `/market` 列表、筛选、分页、收藏状态读取均 `pass`
   - `/market/920086` 详情读取、评论第一页、收藏状态读取均 `pass`
   - 收藏 / 取消收藏最小闭环 `pass`
   - `/favorites` 最小映射 `pass`
3. 本轮以评论分页 `page=2` 定向验证复跑评论列表分页：
   - `/market/920086` 的 `page=1 -> page=2` 请求链路再次命中 `pass`
4. 本轮复跑 package2 既有三子流时，首次评论提交继续沿用旧脚本固定 `orderId=1`：
   - 请求命中 `POST /user/reviews`
   - 返回 `code=0,msg=该订单已评价`
   - 该现象被归类为 `environment/auth-or-data-precondition`，说明旧验证前置已失效，而不是代码回归
5. 随后补做只读候选订单探针：
   - `GET /user/orders/buy?page=1&pageSize=100&status=completed`
   - `GET /user/reviews/mine?page=1&pageSize=100`
   - 找到首个“已完成且未评价”候选：`orderId=907608`、`productId=920069`
6. 基于候选订单执行评论提交 targeted retry：
   - 路由：`/market/920069`
   - 请求：`POST /user/reviews`
   - 观察：`200/code=1`
   - 结果：`pass`
7. 举报提交与我的评价入口本轮继续保持 `pass`。
8. 因此 Day03 owned scope 已具备真实 final acceptance 证据，可由“已具备收口材料，待最终裁定”升级为 `已完成并回填`。

---

## 2. 子流结论明细（最终口径）

| 子流 | scope | observed behavior | evidence level | 结论 | owner | reason |
|---|---|---|---|---|---|---|
| 市场列表 / 筛选 / 分页 | `/market`；`GET /user/market/products?page=1&pageSize=12` + `page=2` + `keyword=DAY1` | `page=1.total=44`；`page=2` 命中；`keyword=DAY1` 过滤命中；收藏状态读取命中 `12` 次 | runtime（browser + network + screenshot） | `pass` | - | - |
| 商品详情 + 评论 page=1 + 收藏状态 | `/market/920086`；详情 / 评论第一页 / 收藏状态读取 | 三条读取链路均命中 `200/code=1` | runtime（browser + network + screenshot） | `pass` | - | - |
| 收藏 / 取消收藏 | `/market/920086`；`POST /user/favorites/920086` + `DELETE /user/favorites/920086` | 双写链路均命中 `200/code=1`；鉴权头正常携带 | runtime（browser + network + screenshot） | `pass` | - | - |
| 收藏列表最小映射 | `/favorites`；`GET /user/favorites` | 收藏动作后路由可达，列表 GET 命中 | runtime（browser + network + screenshot） | `pass` | - | - |
| 评论列表分页 `page=2` | `/market/920086`；`GET /user/market/products/920086/reviews?page=1&pageSize=10` + `...page=2&pageSize=10` | `page=1.total=12`；点击下一页后 `page=2` 命中并返回 `code=1,data.page=2` | runtime（browser + network + screenshot） | `pass` | - | - |
| 评论提交（固定 `orderId=1` 首次尝试） | `/market/920086`；`POST /user/reviews` | 返回 `code=0,msg=该订单已评价`；仅证明旧脚本固定前置失效 | runtime（browser + network + json） | `blocked` | `environment` | `auth-or-data-precondition` |
| 评论提交（动态候选订单 retry） | `/market/920069`；`orderId=907608`；`POST /user/reviews` | 候选订单探针命中；评论提交返回 `200/code=1` | runtime（api-probe + browser + network + screenshot） | `pass` | - | - |
| 举报提交 | `/market/920086`；`POST /user/market/products/920086/report` | 返回 `200/code=1`，工单号 `RPT-20260422-339194` | runtime（browser + network + screenshot） | `pass` | - | - |
| 我的评价入口 | `/reviews/mine`；`GET /user/reviews/mine` | 路由可达；请求命中 `200/code=1`；页面标题可见 | runtime（browser + network + screenshot） | `pass` | - | - |

---

## 3. 证据路径（本轮新增 / 更新）

1. build：
   - `demo-user-ui/.tmp_runtime/2026-04-22-userfront-day03-focused-regression/frontend-build.log`
2. package1 focused regression：
   - `demo-user-ui/.tmp_runtime/2026-04-22-userfront-day03-focused-regression/package1/userfront-day03-package1-focused-runtime-result.json`
   - `demo-user-ui/.tmp_runtime/2026-04-22-userfront-day03-focused-regression/package1/summary.md`
3. package2 既有三子流复跑：
   - `demo-user-ui/.tmp_runtime/2026-04-22-userfront-day03-focused-regression/package2/userfront-day03-package2-runtime-verify-result.json`
   - `demo-user-ui/.tmp_runtime/2026-04-22-userfront-day03-focused-regression/package2/network/review-post-response.json`
   - `demo-user-ui/.tmp_runtime/2026-04-22-userfront-day03-focused-regression/package2/network/report-post-response.json`
   - `demo-user-ui/.tmp_runtime/2026-04-22-userfront-day03-focused-regression/package2/network/my-reviews-get-response.json`
4. 评论分页 `page=2` 定向验证：
   - `demo-user-ui/.tmp_runtime/2026-04-22-userfront-day03-focused-regression/page2/userfront-day03-review-page2-targeted-verify-result.json`
   - `demo-user-ui/.tmp_runtime/2026-04-22-userfront-day03-focused-regression/page2/network/sample-1-product-920086-reviews-page1-response.json`
   - `demo-user-ui/.tmp_runtime/2026-04-22-userfront-day03-focused-regression/page2/network/sample-1-product-920086-reviews-page2-response.json`
5. 评论提交动态候选订单 retry：
   - `demo-user-ui/.tmp_runtime/2026-04-22-userfront-day03-focused-regression/review-submit-retry/userfront-day03-review-submit-candidate-runtime-result.json`
   - `demo-user-ui/.tmp_runtime/2026-04-22-userfront-day03-focused-regression/review-submit-retry/network/candidate-orders-response.json`
   - `demo-user-ui/.tmp_runtime/2026-04-22-userfront-day03-focused-regression/review-submit-retry/network/review-submit-response.json`

---

## 4. 本轮最终评估

- 最终评估状态：`已完成并回填`
- 结论依据：Day03 同域 focused regression 已真实执行；市场浏览/详情、收藏、评论分页、评论提交、举报、我的评价入口均已在本轮得到 `pass` 证据。
- 中途异常说明：固定 `orderId=1` 的旧脚本首次尝试返回“该订单已评价”，经动态候选订单复验后已确认这是环境/数据前置，不是代码缺陷。
- 边界声明：该结论仅覆盖 Day03 owned scope，不等于 Day04/Day05/整站联调已通过。
- 升级路由结论：`不触发 $drive-demo-user-ui-delivery`（本轮未识别出前后端契约冲突）。
