# UserFrontDay03 文档总览

- 日期：`2026-04-17`
- 状态：`输入准备完成（待后续执行线程接手）`
- 主题：`市场浏览、详情、评论、举报、收藏`
- 当前执行日关系：`root README 当前执行日仍为 UserFrontDay02（待最终裁定），Day03 本次仅做 docs-only 输入准备`

---

## 1. 一句话结论

`UserFrontDay03` 已形成可执行输入包：范围、接口、API 分层、联调前置、回填规则已决策完整；但 Day03 尚未开始实现与运行验证。

---

## 2. Day03 Owned Scope

1. 市场浏览与详情：列表、筛选、分页、详情、评论列表展示链路。
2. 评论与举报：评论列表消费、举报提交入口、我的评价入口。
3. 收藏夹：收藏状态查询、收藏/取消收藏动作、收藏列表页。

---

## 3. 非 Day03 范围

1. Day02 账户资料/安全/地址（仍由 Day02 口径收口）。
2. Day04+ 的用户商品管理、订单、钱包、积分、信用、消息中心。
3. 任何“已实现/已联调/已完成并回填”结论（本线程不产出运行态证据）。

---

## 4. 推荐最小读取顺序（执行线程）

1. `demo-user-ui/docs/frontend-freeze/README.md`（确认 Day02 仍为当前执行日，Day03 只接输入包）
2. `demo-user-ui/docs/frontend-freeze/00_Business_Coverage_Matrix.md`（确认 Day03 三行 owner/status/next action）
3. `UserFrontDay03/01_冻结文档/UserFrontDay03_Scope_Freeze_v1.1.md`
4. `UserFrontDay03/02_接口对齐/UserFrontDay03_Interface_Alignment_v1.1.md`
5. `UserFrontDay03/03_API模块/UserFrontDay03_API_Module_Plan_v1.1.md`
6. `UserFrontDay03/04_联调准备与验收/UserFrontDay03_Joint_Debug_Ready_v1.1.md`
7. `demo-user-ui/src/router/index.ts`（新增市场域路由入口的唯一上游）
8. 最接近页面入口：`demo-user-ui/src/pages/HomePage.vue`（市场域导航承接点）
9. 最接近 API 入口：`demo-user-ui/src/api/seller.ts`（现有 API 模块风格参考，不复用业务语义）
10. 控制器入口：`MarketProductController.java`、`FavoriteController.java`、`ReviewController.java`

---

## 5. 先做顺序（执行建议）

1. 先落市场列表 + 详情只读链路（含评论列表只读）。
2. 再接收藏状态 + 收藏/取消收藏 + 收藏列表。
3. 最后接举报提交与“我的评价入口”；若发现订单前置条件，记录阻塞并最小拆分。

---

## 6. 结构约束（执行线程必须遵守）

1. 不要把市场/收藏/详情复杂逻辑继续堆进单个发胖页面。
2. endpoint 定义、字段适配、兼容映射归 `demo-user-ui/src/api/*.ts`。
3. `demo-user-ui/src/utils/request.ts` 只承接共享请求层行为。
4. 页面层只负责页面态、交互态、`loading/empty/error/submit`。

---

## 7. 必须验证 vs 暂不要求

1. 必须验证（执行线程）：列表加载、详情加载、收藏状态一致性、收藏动作成败态、举报提交成败态、鉴权守卫不回归。
2. 暂不要求（本输入线程）：build/dev/browser/runtime、跨线程 accept/gate 最终裁定。

---

## 8. 文档版本入口

1. 范围冻结：`v1.1`
2. 接口对齐：`v1.1`
3. API 模块：`v1.1`
4. 联调准备与验收：`v1.1`
5. 进度回填：`v1.1`

v1.0 作为历史计划建档记录保留，不删除。
