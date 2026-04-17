# UserFrontDay03 API 模块规划

- 日期：`2026-04-17`
- 文档版本：`v1.1`
- 当前状态：`输入准备完成（待执行线程落代码）`

---

## 1. 模块归属冻结

1. `src/api/market.ts`：市场列表、详情、评论列表、举报。
2. `src/api/favorite.ts`：收藏状态、收藏/取消收藏、收藏列表。
3. `src/api/review.ts`：创建评价、我的评价。

---

## 2. 页面/API/request 分层约束

1. endpoint 定义、字段适配、兼容映射归 `src/api/*.ts`。
2. `src/utils/request.ts` 只承接共享请求行为（header/401/unwrap），不塞业务映射。
3. 页面仅负责页面态、交互态、`loading/empty/error/submit`。
4. 不把市场/收藏/详情复杂逻辑堆进单个页面。

---

## 3. 最小页面入口建议

1. `src/pages/MarketListPage.vue`（计划新增）
2. `src/pages/MarketDetailPage.vue`（计划新增）
3. `src/pages/FavoriteListPage.vue`（计划新增）
4. `src/pages/MyReviewsPage.vue`（计划新增，可先入口后提交）

---

## 4. 执行优先级

1. P0：`market.ts` + 列表/详情只读链路。
2. P1：`favorite.ts` + 收藏状态与收藏动作链路。
3. P2：`review.ts` + 举报与我的评价入口；评论提交按前置条件决定是否延后。

---

## 5. 未来回填钩子

1. 每个 API 模块需在 `05_进度回填` 留下“代码路径 + 构建结果 + 运行态状态”。
2. 若出现 endpoint 或字段歧义，先回填 `02_接口对齐` 再继续实现。
