# Day8 回归测试使用指南

## 📁 文件说明

- **Day8_Regression.postman_collection.json** - Postman Collection 测试集合（v2.1 格式）
- **Day8_Local.postman_environment.json** - Postman Environment 环境变量配置

## 🚀 快速开始

### 1. 导入到 Postman

1. 打开 Postman
2. 点击 **Import** 按钮
3. 导入 `Day8_Regression.postman_collection.json` 和 `Day8_Local.postman_environment.json`
4. 在右上角环境选择器中选择 **Day8 Local**

### 2. 环境变量配置

Environment 中已预配置以下变量（可在 Postman 中手动修改）：

- `baseUrl`: `http://localhost:8080` （服务器地址）
- `buyer_username`: `13800000001` （买家登录账号）
- `buyer_password`: `123456` （买家密码）
- `seller_username`: `13800000002` （卖家登录账号）
- `seller_password`: `seller123` （卖家密码）

**自动填充变量**（由测试脚本自动设置）：

- `token_buyer` / `token_seller` - 登录后自动保存
- `productId` - 从市场列表获取
- `addressId` / `shippingAddress` - 从地址列表获取
- `orderId` - 从创建订单响应获取

### 3. 执行测试

#### 方式一：按文件夹顺序执行（推荐）

1. **01-Auth** - 先执行登录获取 token
   - Buyer Login
   - Seller Login

2. **02-Search** - 搜索功能测试
   - Search by Keyword in Title
   - Search by Keyword in Description
   - Get Market Product List (No Keyword) - 自动提取 productId

3. **03-Order-E2E** - 完整订单流程
   - Get Buyer Address List - 自动提取地址信息
   - Create Order
   - Pay Order
   - Get Order Detail (After Pay)
   - Ship Order（使用 seller token）
   - Get Order Detail (After Ship)
   - Confirm Receipt
   - Get Order Detail (After Confirm)

4. **04-Idempotency** - 幂等性测试（核心测试）
   - Create Order for Idempotency
   - Pay Order (First Time)
   - Pay Order (Second Time - Idempotency Test) - **断言返回 "订单已支付，无需重复操作"**
   - Ship Order (First Time)
   - Ship Order (Second Time - Idempotency Test) - **断言返回 "订单已发货，无需重复操作"**
   - Confirm Receipt (First Time)
   - Confirm Receipt (Second Time - Idempotency Test) - **断言返回 "订单已确认收货，无需重复操作"**

5. **05-Negative** - 异常场景测试
   - Create Order for Negative Test
   - Cancel Order
   - Ship Cancelled Order (Should Fail) - **断言失败**
   - Create Order for Unauthorized Ship Test
   - Pay Order for Unauthorized Test
   - Ship Order with Buyer Token (Should Fail) - **断言失败（非卖家发货）**

#### 方式二：运行整个 Collection

1. 在 Postman 中右键点击 **Day8 Regression** Collection
2. 选择 **Run collection**
3. 确保已选择 **Day8 Local** 环境
4. 点击 **Run Day8 Regression**

## ✅ 关键测试点

### 幂等性测试（Day8 核心功能）

以下接口的重复调用应返回幂等提示信息（精确匹配源码）：

- **Pay Order**: `"订单已支付，无需重复操作"` （来源：`OrderServiceImpl.java:303`）
- **Ship Order**: `"订单已发货，无需重复操作"` （来源：`OrderServiceImpl.java:96`）
- **Confirm Order**: `"订单已确认收货，无需重复操作"` （来源：`OrderServiceImpl.java:163,197`）

### 搜索功能测试

- 当前实现：`ProductMapper.xml` 使用 `LIKE` 查询（第149-151行）
- **注意**：Day8 计划中提到应使用 FULLTEXT 索引（ngram 分词器）和 `MATCH...AGAINST`，但实际代码仍为 `LIKE`
- 测试用例按 `LIKE` 实现编写，如已升级为 FULLTEXT，测试仍可正常运行

### 状态流转验证

订单状态应遵循以下流转：

- `pending` → `paid` → `shipped` → `completed`
- 每个状态转换都有相应的接口测试和断言

## 📝 注意事项

1. **Token Header**: 使用 `authentication` header（非 `Authorization`）
2. **执行顺序**: 必须先执行 01-Auth 获取 token，否则后续请求会失败
3. **地址字段**: `CreateOrderRequest` 使用 `shippingAddress` 字符串（非 `addressId`），测试脚本会自动构造或从地址列表获取
4. **productId**: 如果市场列表为空，需要手动在 Environment 中设置 `productId` 变量
5. **异常测试**: 05-Negative 文件夹中的失败测试期望返回错误码或错误消息，断言已相应调整

## 🔧 故障排查

### 问题：401 Unauthorized

- **原因**: Token 未获取或已过期
- **解决**: 重新执行 01-Auth 文件夹中的登录请求

### 问题：productId 为空

- **原因**: 市场列表中没有商品
- **解决**: 手动在 Environment 中设置 `productId` 变量，或先创建测试商品

### 问题：地址相关错误

- **原因**: 买家没有地址记录
- **解决**: 测试脚本会自动使用默认地址字符串，或手动设置 `shippingAddress` 变量

## 📊 测试覆盖

- ✅ 认证登录（买家/卖家）
- ✅ 搜索功能（keyword 匹配 title/description）
- ✅ 订单完整流程（创建→支付→发货→确认收货）
- ✅ 幂等性测试（pay/ship/confirm 重复调用）
- ✅ 异常场景（已取消订单发货、非卖家发货）

---

**创建时间**: 2024-01-01  
**版本**: v1.0  
**Postman 版本要求**: v10.0.0+

## 运行前置条件（避免跑一半才失败）

1. **seller 账号必须至少有 1 个可在市场展示的商品**（status=on_sale / ON_SHELF 且 is_deleted=0），否则 E2E 的发货步骤会因为“不是该订单卖家”而失败。
2. 市场列表必须能查到商品：请先确认 `/user/market/products?page=1&pageSize=10` 返回 `data.list` 非空。
3. buyer 账号必须至少有 1 条收货地址（`/user/addresses` 返回数组非空）。如果为空，本集合会回退写入默认地址字符串，但仍建议补齐真实地址数据以贴近验收。

## 环境变量补充

- 新增：`buyerId` / `sellerId`（登录后自动写入，用于从市场列表选择 seller 的商品）
- 新增：`orderId_idempotency` / `orderId_negative` / `orderId_unauth`（运行中自动写入，便于排错）