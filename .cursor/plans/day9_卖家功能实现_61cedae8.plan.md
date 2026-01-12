---
name: Day9 卖家功能实现
overview: 实现 isSeller 字段、requireSeller 校验、卖家动作门槛和卖家统计接口
todos:
  - id: step1
    content: DB/实体准备：users 表新增 is_seller 字段，User.java 新增 isSeller 属性，UserMapper.xml 确保映射
    status: pending
  - id: step2
    content: requireSeller 服务收口：UserService/UserServiceImpl 新增 requireSeller 方法，UserMapper 新增 selectIsSellerById
    status: pending
    dependencies:
      - step1
  - id: step3
    content: 卖家动作入口加门槛：UserProductController 和 OrdersController 的卖家动作方法加 requireSeller 校验
    status: pending
    dependencies:
      - step2
  - id: step4
    content: Seller Summary：新增 SellerSummaryDTO、SellerService/ServiceImpl、SellerController，ProductMapper/OrderMapper 新增统计查询
    status: pending
    dependencies:
      - step2
---

# Day9：isSeller + 卖家动作校验 + 卖家 Summary

## Step1：DB/实体准备

- 输出 SQL：`ALTER TABLE users ADD COLUMN is_seller INT DEFAULT 0;`
- 修改 `User.java`：新增 `private Integer isSeller;`
- 修改 `UserMapper.xml`：确保 `selectById` 映射 `is_seller -> isSeller`

## Step2：requireSeller 服务收口

- 修改 `UserService.java`：新增 `void requireSeller(Long userId);`
- 修改 `UserServiceImpl.java`：实现 `requireSeller`，新增 `UserMapper.selectIsSellerById`，非 1 抛异常
- 修改 `UserMapper.java`：新增 `Integer selectIsSellerById(Long userId);`
- 修改 `UserMapper.xml`：新增 `selectIsSellerById` 查询

## Step3：卖家动作入口加门槛

- 修改 `UserProductController.java`：所有卖家动作方法加 `userService.requireSeller(currentUserId);`
- 修改 `OrdersController.java`：`/{orderId}/ship` 方法加 `userService.requireSeller(currentUserId);`

## Step4：Seller Summary

- 新增 `SellerSummaryDTO.java`（字段：totalProducts, onSaleProducts, soldProducts, totalOrders, pendingOrders, shippedOrders, completedOrders）
- 新增 `SellerService.java` + `SellerServiceImpl.java`（先 requireSeller，再统计）
- 新增 `SellerController.java`：`GET /user/seller/summary`
- 修改 `ProductMapper.xml`：新增 `countProductsBySellerId`（按 status 分组，is_deleted=0）
- 修改 `OrderMapper.xml`：新增 `countOrdersBySellerId`（按 status 分组）