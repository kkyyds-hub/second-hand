# Day 8: 系统加固与搜索优化（增强版）

## 1. 幂等性复查与修复（双重保障）

### 1.1 shipOrder 幂等性增强（前置校验 + rows==0）

**文件**: `demo-service/src/main/java/com/demo/service/serviceimpl/OrderServiceImpl.java`

**改进点1：前置状态校验幂等化**

- 在第一次查询 `detail` 后（约 81 行），立即检查状态
- 如果状态已经是 `SHIPPED` 或 `COMPLETED`，直接返回（不执行 update）
- 避免不必要的数据库更新操作

**改进点2：rows==0 幂等化**

- 当 `rows==0` 时，重新查询最新状态
- 如果状态为 `SHIPPED`、`COMPLETED`，返回成功提示
- 如果状态为 `CANCELLED`，抛异常

**改进点3：返回值改动**

- Service 方法签名：`void shipOrder(...)` → `String shipOrder(...)`
- 返回提示信息："发货成功" 或 "订单已发货，无需重复操作"
- Controller 层（64-71行）使用 service 返回的信息

### 1.2 confirmOrder 幂等性增强（前置校验 + rows==0）

**文件**: `demo-service/src/main/java/com/demo/service/serviceimpl/OrderServiceImpl.java`

**改进点1：前置状态校验幂等化**

- 在第一次查询 `detail` 后（约 142 行），立即检查状态
- 如果状态已经是 `COMPLETED`，直接返回

**改进点2：rows==0 幂等化**

- 当 `rows==0` 时，重新查询最新状态
- 如果状态为 `COMPLETED`，返回成功提示
- 如果状态为 `CANCELLED`，抛异常

**改进点3：返回值改动**

- Service 方法签名：`void confirmOrder(...)` → `String confirmOrder(...)`
- 返回提示信息："确认收货成功" 或 "订单已确认收货，无需重复操作"
- Controller 层（73-79行）使用 service 返回的信息

### 1.3 幂等性测试集

创建 Postman 测试集合，覆盖以下场景：

- 重复调用 `payOrder`（应返回"订单已支付，无需重复操作"）
- 重复调用 `shipOrder`（应返回"订单已发货，无需重复操作"）
- 重复调用 `confirmReceipt`（应返回"订单已确认收货，无需重复操作"）
- 状态流转异常场景（如对已取消订单发货）

## 2. 复杂查询增强

### 2.1 ProductMapper 模糊搜索索引优化（FULLTEXT ngram）

**文件**: `demo-service/src/main/resources/mapper/ProductMapper.xml`

**重要修正**：前缀索引对 `%keyword%` 前后都有通配符的模糊查询无效，必须使用全文索引。

当前实现：`getMarketProductList`（136-149行）使用 LIKE：

```xml
AND (p.title LIKE CONCAT('%', #{keyword}, '%')
OR p.description LIKE CONCAT('%', #{keyword}, '%'))
```

**优化方案**：

1. **创建 FULLTEXT 索引（ngram 分词器）**：
   ```sql
   -- MySQL 5.7.6+ 支持中文全文索引（ngram）
   ALTER TABLE products ADD FULLTEXT INDEX ft_title_desc_ngram (title, description) WITH PARSER ngram;
   ```

2. **修改 SQL 使用 MATCH...AGAINST**：
   ```xml
   <if test="keyword != null and keyword != ''">
       AND MATCH(p.title, p.description) AGAINST(#{keyword} IN NATURAL LANGUAGE MODE)
   </if>
   ```


**注意**：如果 ngram 不支持，可以回退到 LIKE，但需要明确说明性能限制。

3. **更新 schema.sql**：在数据库初始化脚本中记录索引变更

### 2.2 is_deleted=0 收口检查

**文件**: `demo-service/src/main/resources/mapper/OrderMapper.xml`

**问题**：所有涉及 users 表的查询必须校验 `is_deleted=0`，确保与关键约束一致。

**需要检查的查询**：

1. **listBuyerOrders**（80-131行）：

   - JOIN `users u ON o.seller_id = u.id`（101行）
   - **缺失**：`AND u.is_deleted = 0`

2. **listSellerOrders**（133-163行）：

   - JOIN `users u ON u.id = o.buyer_id`（155行）
   - **缺失**：`AND u.is_deleted = 0`

3. **getOrderDetail**（165-209行）：

   - JOIN `users bu ON bu.id = o.buyer_id`（200行）
   - JOIN `users su ON su.id = o.seller_id`（201行）
   - **缺失**：`AND bu.is_deleted = 0 AND su.is_deleted = 0`

**修复方案**：在所有 JOIN users 的地方添加 `AND u.is_deleted = 0`（或对应的别名）

## 3. 实施步骤

1. **幂等性修复**（按顺序）：

   - 修改 `shipOrder` 方法签名：`void` → `String`
   - 在 `shipOrder` 前置校验中添加幂等判断（第一次查询后）
   - 在 `shipOrder` rows==0 处理中添加幂等判断
   - 修改 `confirmOrder` 方法签名：`void` → `String`
   - 在 `confirmOrder` 前置校验中添加幂等判断
   - 在 `confirmOrder` rows==0 处理中添加幂等判断
   - 修改 `OrderService` 接口签名
   - 修改 Controller 层使用 service 返回值

2. **索引优化**：

   - 检查 MySQL 版本是否支持 ngram（5.7.6+）
   - 创建 FULLTEXT 索引（ngram 分词器）
   - 修改 `ProductMapper.xml` 使用 MATCH...AGAINST
   - 更新 `schema.sql` 记录索引

3. **is_deleted=0 收口**：

   - 在 `listBuyerOrders` 添加 `AND u.is_deleted = 0`
   - 在 `listSellerOrders` 添加 `AND u.is_deleted = 0`
   - 在 `getOrderDetail` 添加 `AND bu.is_deleted = 0 AND su.is_deleted = 0`

4. **测试**：

   - 创建 Postman 幂等性测试集（覆盖前置校验和 rows==0 两种场景）
   - 执行回归测试

## 4. 注意事项

- **幂等性双重保障**：前置校验 + rows==0 处理，确保所有场景都幂等
- **返回值改动**：Service 和 Controller 都要修改，保持一致性
- **索引选择**：前缀索引对 `%keyword%` 无效，必须用 FULLTEXT（最好 ngram）
- **is_deleted 收口**：所有涉及 users 表的查询必须校验，确保与关键约束一致
- **保持代码风格**：使用 `OrderStatus` 枚举判断状态
- **所有修改遵循"条件更新"原则**：不破坏现有乐观锁机制