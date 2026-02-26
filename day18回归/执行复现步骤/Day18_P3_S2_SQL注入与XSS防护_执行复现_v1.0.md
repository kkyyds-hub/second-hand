# Day18 P3-S2 SQL 注入与 XSS 防护 执行复现 v1.0

- 日期：2026-02-25
- 目标：验证排序白名单、参数化查询基线与 XSS 输入守卫生效。

---

## 1. 前置条件

1. 服务已启动：`http://localhost:8080`
2. 测试账号可登录：`13800000001 / 123456`
3. 已完成 P3-S2 代码改造并重启服务。

---

## 2. 场景 A：SQL 注入静态扫描

1. 扫描 Mapper 中是否存在 `${}`：

```powershell
rg -n "\$\{" -S demo-service/src/main/resources/mapper
```

2. 扫描动态排序是否已接入白名单守卫：

```powershell
rg -n "normalizeSortField|normalizeSortOrder" -S demo-service/src/main/java/com/demo/controller/admin demo-service/src/main/java/com/demo/service/serviceimpl/OrderServiceImpl.java
```

3. 预期：
   - Mapper 无 `${}` 命中；
   - 排序参数在入口统一校验。

---

## 3. 场景 B：排序注入动态验证

1. 登录获取用户 token：`POST /user/auth/login/password`
2. 使用恶意 `sortField` 调用买家订单分页：

```http
GET /user/orders/buy?page=1&pageSize=5&sortField=createTime%20desc,(select%201)&sortOrder=desc
```

3. 预期：
   - 返回失败（`code=0`）；
   - 提示 `sortField` 非法（仅允许白名单字段）。

---

## 4. 场景 C：XSS 输入动态验证

1. 使用用户 token 调用更新资料接口：
   - `PATCH /user/me/profile`
   - `nickname="<script>alert(1)</script>"`
2. 调用商品举报接口：
   - `POST /user/market/products/{productId}/report`
   - `description="<img src=x onerror=alert(1)>"`
3. 预期：
   - 均返回失败（`code=0`）；
   - 提示字段包含非法脚本/HTML 片段。

---

## 5. DoD 勾选

- [ ] 不存在动态 SQL 拼接注入入口。  
- [ ] 关键富文本/字符串入口具备 XSS 处理规则并运行生效。  
- [ ] 执行记录已回填。  

---

（文件结束）
