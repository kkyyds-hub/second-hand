# Day18 P3-S3 CSRF 边界与请求防伪策略 执行复现 v1.0

- 日期：2026-02-25
- 目标：验证“受保护写接口必须 Header Token、白名单匿名写接口按业务防伪收口”的边界。

---

## 1. 前置条件

1. 服务可访问：`http://localhost:8080`
2. 当前代码已应用拦截器配置：
   - `/admin/**`（排除 `/admin/employee/login`）
   - `/user/**`（排除 `/user/auth/**`）

---

## 2. 场景 A：静态边界核查

1. 检查拦截器范围与豁免路径：

```powershell
rg -n "admin/employee/login|/user/auth/\\*\\*|/user/\\*\\*|/admin/\\*\\*|user/shop/status" demo-service/src/main/java/com/demo/config/WebMvcConfiguration.java
```

2. 检查 token header 名称：

```powershell
rg -n "admin-token-name|user-token-name" demo-service/src/main/resources/application.yml
```

3. 检查匿名回调入口：

```powershell
rg -n "/payment|/callback|不需要登录鉴权" demo-service/src/main/java/com/demo/controller/PaymentController.java
```

预期：
1. 鉴权/豁免边界与策略文档一致；
2. token 头名称固定；
3. 回调接口存在且定位明确。

---

## 3. 场景 B：运行态边界验证

1. 不带 token 调受保护写接口（应 401）：
   - `POST /user/orders`
   - `PATCH /user/me/profile`
   - `POST /admin/orders/{id}/flags`
2. 不带 token 调匿名白名单写接口（应非 401）：
   - `POST /user/auth/login/password`
   - `POST /admin/employee/login`
   - `POST /payment/callback`

预期：
1. 受保护写接口统一 401；
2. 白名单匿名写接口可到达业务层（可成功或业务失败，但非鉴权 401）。

---

## 4. DoD 勾选

- [ ] 团队对 CSRF 边界与处置策略无歧义。  
- [ ] 安全评审可按文档直接判定接口风险。  
- [ ] 执行记录已回填。  

---

（文件结束）
