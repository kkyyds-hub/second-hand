# Day18 P3-S1 敏感数据脱敏与二次验证 执行复现 v1.0

- 日期：2026-02-25
- 目标：验证敏感信息不以明文写入日志，并验证高风险操作存在二次验证拦截路径。

---

## 1. 前置条件

1. 服务可访问：`http://localhost:8080`
2. 测试账号可用：
   - 买家：`13800000001 / 123456`
3. 可执行代码扫描命令（`rg`）。

---

## 2. 场景 A：敏感日志静态扫描

1. 扫描 token/验证码相关日志输出：

```powershell
rg -n "log\\.(info|warn|error|debug).*token|验证码:|token=|jwt校验:" -S demo-service/src/main/java --glob "*.java"
```

2. 扫描整包 request 日志（避免密码/验证码 DTO 被 toString 输出）：

```powershell
rg -n "log\\.(info|warn|error|debug).*request\\}" -S demo-service/src/main/java --glob "*.java"
```

3. 预期：
   - 不存在明文密码、验证码、完整 token 输出；
   - 认证/账号安全链路无整包敏感 request 日志。

---

## 3. 场景 B：改密二次验证拦截

1. 登录拿用户 token。
2. 调用 `POST /user/me/password`，仅传 `newPassword`，不传旧密码/验证码。
3. 预期：
   - 返回失败；
   - 提示“请提供当前密码或验证码进行验证”。

---

## 4. 场景 C：绑定/解绑二次验证拦截

1. 调用 `DELETE /user/me/bindings/phone`，空请求体。
2. 调用 `POST /user/me/bindings/phone`，传错误验证码。
3. 调用 `POST /user/me/bindings/email`，传错误验证码。
4. 预期：
   - 解绑空校验失败（必须二次验证）；
   - 绑定操作验证码错误时失败，不允许绕过验证。

---

## 5. DoD 勾选

- [ ] 不在日志中泄露明文敏感字段。  
- [ ] 高风险操作具备二次验证路径。  
- [ ] 执行记录已回填。  

---

（文件结束）
