# Day17 Step P2-S4：冗余路径清理执行复现步骤

- 文档版本：v1.0
- 日期：2026-02-24
- 目标：验证 P2-S4 清理后接口行为一致且重复查询路径下降。

---

## 1. 复现接口
1. `GET /user/reviews/mine?page=1&pageSize=10`
2. `GET /user/market/products/{productId}/reviews?page=1&pageSize=10`

---

## 2. 验证步骤
### 2.1 我的评价列表
1. 用同一买家账号调用 `GET /user/reviews/mine`。
2. 对比清理前后返回：
   - 列表条数
   - 分页字段（`total/page/pageSize`）
   - 每条 `buyerDisplayName/buyerAvatar/productTitle/productCover`
3. 预期：返回数据业务语义不变。

### 2.2 商品评价列表
1. 调用 `GET /user/market/products/{productId}/reviews`。
2. 对比清理前后返回：
   - 列表条数与排序
   - 匿名展示规则
   - 商品展示字段
3. 预期：返回数据业务语义不变。

### 2.3 查询路径检查（日志观察）
1. 开启 dev SQL 日志。
2. 调用上述两个接口，观察查询特征：
   - 不再出现“每条 review 重复查用户/商品”的模式。
   - 我的评价列表应表现为：主查询 + 用户批量查 + 商品批量查。
   - 商品评价列表应表现为：主查询 + 用户批量查 + 商品单查。

---

## 3. 通过标准
1. 接口契约一致：通过 / 不通过
2. 匿名与商品展示逻辑一致：通过 / 不通过
3. 重复查询路径下降：通过 / 不通过
4. 无异常回归（空数据、匿名数据、用户已删除）：通过 / 不通过

---

（文件结束）
