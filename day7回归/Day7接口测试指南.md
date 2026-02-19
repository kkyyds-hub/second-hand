# Day 7 接口测试指南

## 📋 测试环境准备

### 1. 启动服务
```bash
cd demo-service
mvn spring-boot:run
```

### 2. 访问 Knife4j 文档
浏览器打开：`http://localhost:8080/doc.html`

### 3. 获取管理员Token（如需要）
- 登录管理员账号获取Token
- Token需要放在请求头：`authentication: <token>`

---

## 🧪 接口测试清单

### ✅ 1. 管理员待审列表接口

#### 测试1.1：默认查询（应只返回 under_review 状态）
```
GET /admin/products/pending-approval?page=1&pageSize=10
Headers: { "authentication": "<admin_token>" }
```

**预期结果：**
- ✅ 只返回 `status = under_review` 的商品
- ✅ 过滤了 `is_deleted = 0` 的记录
- ✅ 分页信息正确

**验证SQL逻辑：**
```sql
-- 应执行类似：
SELECT * FROM products 
WHERE is_deleted = 0 
  AND status = 'under_review'
ORDER BY create_time DESC
LIMIT 10 OFFSET 0;
```

---

#### 测试1.2：查询全部状态
```
GET /admin/products/pending-approval?page=1&pageSize=10&status=全部
Headers: { "authentication": "<admin_token>" }
```

**预期结果：**
- ✅ 返回所有状态的商品（under_review、on_sale、off_shelf、sold）
- ✅ 仍然过滤 `is_deleted = 0`

**验证SQL逻辑：**
```sql
-- 应执行类似：
SELECT * FROM products 
WHERE is_deleted = 0 
  -- 注意：没有 status 条件
ORDER BY create_time DESC;
```

---

#### 测试1.3：按具体状态查询
```
GET /admin/products/pending-approval?page=1&pageSize=10&status=on_sale
Headers: { "authentication": "<admin_token>" }
```

**预期结果：**
- ✅ 只返回 `status = on_sale` 的商品

**验证SQL逻辑：**
```sql
-- 应执行类似：
SELECT * FROM products 
WHERE is_deleted = 0 
  AND status = 'on_sale'
ORDER BY create_time DESC;
```

---

### ✅ 2. 审核通过接口

#### 测试2.1：审核通过
```
PUT /admin/products/{productId}/approve
Headers: { "authentication": "<admin_token>" }
Path参数: productId = <某个 under_review 状态的商品 ID>
```

**测试步骤：**
1. 先查询一个 `status = under_review` 的商品 ID（如 ID=2）
2. 调用审核通过接口
3. 查询数据库验证

**预期结果：**
- ✅ 返回成功：`{ "code": 1, "msg": "商品审核通过", "data": "商品审核通过" }`
- ✅ 数据库状态变为：`status = 'on_sale'`
- ✅ **reason 字段变为 NULL**（清空）

**验证SQL：**
```sql
-- 执行前
SELECT id, status, reason FROM products WHERE id = <productId>;
-- status = 'under_review', reason = NULL 或某个值

-- 执行后
SELECT id, status, reason FROM products WHERE id = <productId>;
-- status = 'on_sale', reason = NULL  ✅
```

---

### ✅ 3. 审核驳回接口

#### 测试3.1：审核驳回（正常情况）
```
PUT /admin/products/{productId}/reject
Headers: { 
  "authentication": "<admin_token>",
  "Content-Type": "application/json"
}
Path参数: productId = <某个 under_review 状态的商品 ID>
Body:
{
  "reason": "商品信息不符合规范，请重新填写"
}
```

**测试步骤：**
1. 先查询一个 `status = under_review` 的商品 ID
2. 调用审核驳回接口，传入 reason
3. 查询数据库验证

**预期结果：**
- ✅ 返回成功：`{ "code": 1, "msg": "商品审核驳回", "data": "商品审核驳回" }`
- ✅ 数据库状态变为：`status = 'off_shelf'`
- ✅ **reason 字段有值**：`reason = "商品信息不符合规范，请重新填写"`

**验证SQL：**
```sql
-- 执行后
SELECT id, status, reason FROM products WHERE id = <productId>;
-- status = 'off_shelf', reason = '商品信息不符合规范，请重新填写'  ✅
```

---

#### 测试3.2：审核驳回（reason为空 - 应失败）
```
PUT /admin/products/{productId}/reject
Body:
{
  "reason": ""
}
```

**预期结果：**
- ✅ 返回错误：`{ "code": 0, "msg": "驳回原因不能为空" }`
- ✅ 数据库状态不变

---

### ✅ 4. 卖家重提审接口

#### 测试4.1：重提审（off_shelf → under_review）
```
PUT /user/products/{productId}/resubmit
Headers: { "authentication": "<user_token>" }
Path参数: productId = <某个 off_shelf 状态的商品 ID，且ownerId = 当前用户>
```

**测试步骤：**
1. 使用卖家账号登录，获取Token
2. 查询一个自己的 `status = off_shelf` 的商品 ID
3. 调用重提审接口
4. 查询数据库验证

**预期结果：**
- ✅ 返回成功，包含商品详情
- ✅ 数据库状态变为：`status = 'under_review'`
- ✅ **reason 字段变为 NULL**（清空）

**验证SQL：**
```sql
-- 执行前
SELECT id, status, reason FROM products WHERE id = <productId>;
-- status = 'off_shelf', reason = '某个驳回原因'

-- 执行后
SELECT id, status, reason FROM products WHERE id = <productId>;
-- status = 'under_review', reason = NULL  ✅
```

---

### ✅ 5. 卖家撤回审核接口

#### 测试5.1：撤回审核（under_review → off_shelf）
```
PUT /user/products/{productId}/withdraw
Headers: { "authentication": "<user_token>" }
Path参数: productId = <某个 under_review 状态的商品 ID，且ownerId = 当前用户>
```

**测试步骤：**
1. 使用卖家账号登录
2. 查询一个自己的 `status = under_review` 的商品 ID
3. 调用撤回审核接口
4. 查询数据库验证

**预期结果：**
- ✅ 返回成功，包含商品详情
- ✅ 数据库状态变为：`status = 'off_shelf'`
- ✅ **reason 字段为固定值**：`reason = 'seller_withdraw'`

**验证SQL：**
```sql
-- 执行前
SELECT id, status, reason FROM products WHERE id = <productId>;
-- status = 'under_review', reason = NULL

-- 执行后
SELECT id, status, reason FROM products WHERE id = <productId>;
-- status = 'off_shelf', reason = 'seller_withdraw'  ✅
```

---

### ✅ 6. 卖家编辑商品联动

#### 测试6.1：编辑商品后自动进入审核
```
PUT /user/products/{productId}
Headers: { 
  "authentication": "<user_token>",
  "Content-Type": "application/json"
}
Body:
{
  "title": "修改后的商品标题",
  "description": "修改后的描述",
  "price": 299.00,
  "category": "电子产品"
}
```

**测试步骤：**
1. 查询一个自己的商品（任意状态，除了sold）
2. 调用编辑接口修改商品信息
3. 查询数据库验证

**预期结果：**
- ✅ 返回成功，包含更新后的商品详情
- ✅ **数据库状态自动变为**：`status = 'under_review'`
- ✅ **reason 字段变为 NULL**（清空历史驳回原因）

**验证SQL：**
```sql
-- 执行后
SELECT id, status, reason FROM products WHERE id = <productId>;
-- status = 'under_review', reason = NULL  ✅
```

---

## 🔍 数据库验证SQL

### 查询所有商品状态分布
```sql
SELECT status, COUNT(*) as count 
FROM products 
WHERE is_deleted = 0 
GROUP BY status;
```

### 查询某个商品的完整信息
```sql
SELECT id, title, status, reason, owner_id, create_time, update_time 
FROM products 
WHERE id = <productId> AND is_deleted = 0;
```

---

## ⚠️ 注意事项

1. **Token认证**：所有管理员接口需要有效的管理员Token
2. **权限校验**：卖家接口只能操作自己的商品（ownerId校验）
3. **状态流转**：确保状态流转符合业务规则
4. **并发测试**：如果有并发场景，注意SQL条件更新的安全性

---

## 📊 测试结果记录表

| 测试项 | 接口 | 状态 | 备注 |
|--------|------|------|------|
| 1.1 默认查询 | GET /admin/products/pending-approval | ⬜ | |
| 1.2 查询全部 | GET /admin/products/pending-approval?status=全部 | ⬜ | |
| 1.3 具体状态 | GET /admin/products/pending-approval?status=on_sale | ⬜ | |
| 2.1 审核通过 | PUT /admin/products/{id}/approve | ⬜ | 验证reason=NULL |
| 3.1 审核驳回 | PUT /admin/products/{id}/reject | ⬜ | 验证reason有值 |
| 3.2 驳回空reason | PUT /admin/products/{id}/reject | ⬜ | 应返回错误 |
| 4.1 重提审 | PUT /user/products/{id}/resubmit | ⬜ | 验证reason=NULL |
| 5.1 撤回审核 | PUT /user/products/{id}/withdraw | ⬜ | 验证reason=seller_withdraw |
| 6.1 编辑商品 | PUT /user/products/{id} | ⬜ | 验证自动under_review |

---

**测试完成后，请勾选 ✅ 标记已通过的测试项！**
