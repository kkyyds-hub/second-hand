# UserFrontDay03 范围冻结（Scope Freeze）

- 日期：`2026-04-17`
- 文档版本：`v1.1`
- 当前状态：`输入准备完成（待执行线程接手）`
- 结论边界：`本文件只定义执行输入，不代表 Day03 已开始实现`

---

## 1. Day03 Owned Scope（冻结）

1. 市场浏览与详情：市场列表、筛选、分页、详情、评论列表展示。
2. 评论与举报：举报提交入口、评论列表消费、我的评价入口。
3. 收藏夹：收藏状态、收藏/取消收藏、收藏列表与跳转。

---

## 2. 非 Day03 范围（冻结）

1. Day02 账户资料/安全/地址的收口裁定。
2. Day04+ 用户商品管理、订单主链、资产域、消息中心。
3. 本线程的运行态验证、最终 acceptance、active day 切换动作。

---

## 3. 最小代码与接口入口（执行线程）

1. 路由入口：`demo-user-ui/src/router/index.ts`
2. 页面承接点：`demo-user-ui/src/pages/HomePage.vue`
3. API 参考入口：`demo-user-ui/src/api/seller.ts`
4. 计划新增 API：`src/api/market.ts`、`src/api/favorite.ts`、`src/api/review.ts`
5. controller 入口：`MarketProductController.java`、`FavoriteController.java`、`ReviewController.java`

---

## 4. 执行顺序建议（冻结）

1. 先做只读主链：列表 -> 详情 -> 评论列表。
2. 再做收藏主链：状态查询 -> 收藏/取消收藏 -> 收藏列表。
3. 最后做举报与我的评价入口；如遇订单前置依赖，保留阻塞并拆分。

---

## 5. 风险与前置条件

1. 评论提交可能依赖订单完成状态，需在执行时核对，不提前宣称闭环。
2. 列表筛选字段与分页参数仍需按真实 DTO 对齐，不能靠页面猜字段。
3. 收藏状态与详情返回体可能分离，必须以显式状态接口为准。

---

## 6. 回填要求（先写规则，不写结果）

1. Day03 执行结果先回填 `05_进度回填`，再回写 matrix/root 摘要。
2. 若仅代码完成未跑运行态，状态只能写 `代码已完成待运行验证`。
3. 若执行中出现 contract/controller 口径冲突，标记为“待 delivery 澄清项”。
