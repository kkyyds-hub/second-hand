# UserFrontDay03 文档总览

- 日期：`2026-04-22`
- 状态：`已完成并回填（2026-04-22 Day03 同域 focused regression + final acceptance 已完成；评论提交通过动态候选订单复验关闭最后一个旧脚本前置阻塞）`
- 主题：`市场浏览、详情、评论、举报、收藏`
- 当前执行日关系：`root README 当前执行日已切换为 UserFrontDay04（Day02 已完成并回填后顺延）；Day03 已完成并回填，仍不切换 active day`

---

## 1. 一句话结论

`UserFrontDay03` 已于 `2026-04-22` 升级为 `已完成并回填`：本轮 focused regression 重新覆盖市场浏览/详情、收藏、评论分页、评论提交、举报、我的评价入口；评论提交首轮命中的固定 `orderId=1` 阻塞已通过动态候选订单复验确认为旧验证前置失效，而非代码缺陷。

---

## 2. Day03 Owned Scope

1. 市场浏览与详情：列表、筛选、分页、详情、评论列表展示链路。
2. 评论与举报：评论列表消费、举报提交入口、我的评价入口。
3. 收藏夹：收藏状态查询、收藏/取消收藏动作、收藏列表页。

---

## 3. 当前已回填与未回填边界

1. 已回填（最终口径）：
   - `/market` 列表读取/筛选/分页 + 收藏状态读取
   - `/market/:productId` 详情读取 + 收藏状态读取
   - 收藏/取消收藏最小闭环
   - `/favorites` 最小读取映射
   - `/market/:productId` 评论提交表单可见 + `orderId` 前置校验 + `POST /user/reviews` 提交成功（最终以动态候选订单 retry 为准）
   - `/market/:productId` 举报表单可见 + `POST /user/market/products/{productId}/report` 提交成功
   - `/reviews/mine` 路由可达 + `GET /user/reviews/mine` 请求成功
2. 当前 blocker：无未消除 blocker；固定 `orderId=1` 的“该订单已评价”现象已在本轮被界定为旧验证前置失效，并通过动态候选订单复验关闭。
3. Day03 现在可以写成“已完成并回填”，但仍不能写成“整站联调已通过”。

---

## 4. 推荐最小读取顺序（后续执行线程）

1. `demo-user-ui/docs/frontend-freeze/README.md`
2. `demo-user-ui/docs/frontend-freeze/00_Business_Coverage_Matrix.md`
3. `UserFrontDay03/04_联调准备与验收/UserFrontDay03_Joint_Debug_Ready_v1.2.md`
4. `UserFrontDay03/05_进度回填/UserFrontDay03_Progress_Backfill_v1.2.md`
5. `demo-user-ui/.tmp_runtime/2026-04-22-userfront-day03-focused-regression/package1/userfront-day03-package1-focused-runtime-result.json`
6. `demo-user-ui/.tmp_runtime/2026-04-22-userfront-day03-focused-regression/package2/userfront-day03-package2-runtime-verify-result.json`
7. `demo-user-ui/.tmp_runtime/2026-04-22-userfront-day03-focused-regression/page2/userfront-day03-review-page2-targeted-verify-result.json`
8. `demo-user-ui/.tmp_runtime/2026-04-22-userfront-day03-focused-regression/review-submit-retry/userfront-day03-review-submit-candidate-runtime-result.json`

---

## 5. 后续优先顺序（执行建议）

1. Day03 已完成并回填，后续仅在实现或环境再次变动时做定向回归。
2. root active day 仍保持 Day02，后续线程不应把 Day03 的 `pass` 外推成 Day02/Day04+/整站完成。
3. 若后续 Day03 再出现运行态回归，优先回填 Day03 自身 `04/05` 与覆盖矩阵，再决定是否进入 Day09 汇总。

---

## 6. 结构约束（执行线程必须遵守）

1. 不要把市场/收藏/详情复杂逻辑继续堆进单个发胖页面。
2. endpoint 定义、字段适配、兼容映射归 `demo-user-ui/src/api/*.ts`。
3. `demo-user-ui/src/utils/request.ts` 只承接共享请求层行为。
4. 页面层只负责页面态、交互态、`loading/empty/error/submit`。

---

## 7. 文档版本入口

1. 范围冻结：`v1.1`
2. 接口对齐：`v1.1`
3. API 模块：`v1.1`
4. 联调准备与验收：`v1.2`
5. 进度回填：`v1.2`

v1.0 作为历史计划建档记录保留，不删除。
