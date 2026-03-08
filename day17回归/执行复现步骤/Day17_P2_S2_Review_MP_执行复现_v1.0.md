# Day17 Step P2-S2：Review 模块 MP 迁移执行复现步骤

- 文档版本：v1.0
- 日期：2026-02-24
- 目标：验证 Review 模块迁移到 MP 后功能无回归。

---

## 1. 前置条件
1. 服务可正常启动。
2. 数据库存在可用订单、商品、用户数据（至少 1 条 `completed` 订单）。
3. 已准备可用用户 token（买家身份）。

---

## 2. 复现用接口
1. 创建评价：`POST /user/reviews`
2. 我的评价：`GET /user/reviews/mine?page=1&pageSize=10`
3. 商品评价列表：`GET /user/market/products/{productId}/reviews?page=1&pageSize=10`

---

## 3. 用例与预期
### 3.1 创建评价成功
1. 调用 `POST /user/reviews`，传入合法 `orderId/rating/content`。
2. 预期：
   - 返回 `reviewId`。
   - `reviews` 表新增记录。
   - `is_deleted = 0`。
   - `create_time/update_time` 正常写入。

### 3.2 重复评价幂等拦截
1. 对同一 `orderId + role` 再次调用创建评价。
2. 预期：
   - 返回“已评价/重复评价”业务错误。
   - 不产生第二条记录。

### 3.3 防刷限制
1. 构造同一买家 24 小时内连续创建评价达到阈值（20 条）。
2. 预期：
   - 第 21 次被业务规则拦截。

### 3.4 我的评价分页
1. 调用 `GET /user/reviews/mine?page=1&pageSize=10`。
2. 预期：
   - 返回 `PageResult`。
   - 仅当前买家数据。
   - 按 `createTime` 倒序。

### 3.5 商品评价分页
1. 调用 `GET /user/market/products/{productId}/reviews?page=1&pageSize=10`。
2. 预期：
   - 返回 `PageResult`。
   - 仅该商品数据。
   - 按 `createTime` 倒序。

---

## 4. SQL / 日志观察点
1. 分页链路不再依赖 `PageHelper.startPage`（Review 模块）。
2. Review 查询由 MP wrapper 生成 SQL，日志中可观察分页 SQL。
3. 未出现“PageHelper + MP 分页混用”异常。

---

## 5. 回归结论模板
1. 创建评价：通过 / 不通过
2. 幂等拦截：通过 / 不通过
3. 防刷限制：通过 / 不通过
4. 我的评价分页：通过 / 不通过
5. 商品评价分页：通过 / 不通过
6. 迁移后接口契约一致：通过 / 不通过

---

（文件结束）
