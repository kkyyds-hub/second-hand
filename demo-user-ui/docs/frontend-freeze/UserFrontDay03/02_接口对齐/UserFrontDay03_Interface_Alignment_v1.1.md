# UserFrontDay03 前后端接口对齐

- 日期：`2026-04-17`
- 文档版本：`v1.1`
- 当前状态：`输入准备完成（接口口径可执行，待实现与验证）`

---

## 1. Day03 接口面（已核最小 controller 事实）

1. `GET /user/market/products`（市场列表）
2. `GET /user/market/products/{productId}`（商品详情）
3. `GET /user/market/products/{productId}/reviews`（商品评论列表）
4. `POST /user/market/products/{productId}/report`（商品举报）
5. `POST /user/favorites/{productId}`（收藏商品）
6. `DELETE /user/favorites/{productId}`（取消收藏）
7. `GET /user/favorites`（收藏列表）
8. `GET /user/favorites/{productId}/status`（收藏状态）
9. `POST /user/reviews`（创建评价入口）
10. `GET /user/reviews/mine`（我的评价）

---

## 2. 控制器入口（执行线程最小读取）

1. `demo-service/src/main/java/com/demo/controller/user/MarketProductController.java`
2. `demo-service/src/main/java/com/demo/controller/user/FavoriteController.java`
3. `demo-service/src/main/java/com/demo/controller/user/ReviewController.java`

---

## 3. 对齐规则

1. 页面不得直写 endpoint，统一由 `src/api/*.ts` 出口承接。
2. 字段兼容与映射在 API 层处理，不在页面层散写转换。
3. 举报/评论是显式提交动作，必须保留失败态分支和证据入口。
4. 评论创建若受订单前置限制，按阻塞回填，不把入口可见等同于可提交。

---

## 4. 待执行阶段确认项

1. `MarketProductQueryDTO` 对应的筛选字段、分页参数、默认排序。
2. 详情与评论列表并发/串行加载策略及失败隔离策略。
3. 举报接口的幂等与重复提交提示策略。
4. 评价提交前置（订单完成）是否需要 Day03 内拆分为“入口先行、提交后置”。

---

## 5. 本轮边界声明

1. 本轮没有新增前端/后端代码、没有 runtime 证据。
2. 本轮结论仅为接口输入可执行，不是联调通过结论。
