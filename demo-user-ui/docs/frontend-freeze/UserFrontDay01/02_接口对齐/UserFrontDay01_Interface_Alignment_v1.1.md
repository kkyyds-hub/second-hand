# UserFrontDay01 接口对齐

- 日期：`2026-03-18`
- 文档版本：`v1.1`
- 当前状态：`进行中（已补 Day01 seller summary / auth header / real backend 401 的真实联调结论）`

---

## 1. 本轮对齐目标

本次只聚焦 `UserFrontDay01` 剩余的真实联调阻塞，不扩到 `Day02+`，也不把任务升级成整站联调。

核心目标只有两项：

1. 查清首页 `GET /user/seller/summary` 先前 `500` 现象的真实来源；
2. 查清用户端真实 backend 的鉴权头、401 行为，以及 seller summary 在真实后端下的最小可联调结论。

---

## 2. 本轮实际检查的前后端文件

### 前端

- `demo-user-ui/src/utils/request.ts`
- `demo-user-ui/src/api/seller.ts`
- `demo-user-ui/src/api/auth.ts`
- `demo-user-ui/src/router/index.ts`
- `demo-user-ui/src/pages/HomePage.vue`
- `demo-user-ui/.tmp_runtime/userfront-day01-401-check.html`
- `demo-user-ui/.tmp_runtime/userfront-day01-real-summary-check.html`
- `demo-user-ui/.tmp_runtime/userfront-day01-non-seller-summary-check.html`

### 后端

- `demo-service/src/main/java/com/demo/interceptor/JwtTokenUserInterceptor.java`
- `demo-service/src/main/java/com/demo/controller/user/SellerController.java`
- `demo-service/src/main/java/com/demo/service/serviceimpl/SellerServiceImpl.java`
- `demo-service/src/main/java/com/demo/service/serviceimpl/UserServiceImpl.java`
- `demo-service/src/main/resources/application.yml`

---

## 3. 本轮确认的真实协议

| 场景 | 真实结果 | 结论 |
|---|---|---|
| 无鉴权头调用 `GET /user/seller/summary` | HTTP `401` | backend 会直接拒绝 |
| 只传 `Authorization: Bearer TOKEN_REDACTED` | HTTP `401` | 当前用户端后端**不接受** `Authorization` 作为 Day01 鉴权头 |
| 传 `authentication: demo-token` 之类无效 token | HTTP `401` | 401 来自真实 backend 鉴权，不是前端 mock |
| 传 `authentication: <有效 seller JWT>` | HTTP `200`，`{"code":1,"msg":"success"...}` | seller summary 接口本身可用 |
| 传 `authentication: <有效但非 seller 的 JWT>` | HTTP `200`，`code=0`，业务消息为“仅卖家可执行该操作” | controller 没有 500，非 seller 会走业务拒绝 |

**结论：** Day01 当前真实协议仍然是 `authentication` 请求头，不应改写成 `Authorization: Bearer`。

---

## 4. seller summary 的本轮真相

1. `SellerController#getSummary()` 从 `BaseContext.getCurrentId()` 取当前用户；
2. `JwtTokenUserInterceptor` 只读取 `application.yml` 中配置的 `user-token-name`，当前值就是 `authentication`；
3. `SellerServiceImpl#getSummary()` 会先调用 `userService.requireSeller(userId)`；
4. 非 seller 不会触发 500，而是返回业务拒绝；
5. 在 backend 存活且请求头正确时，seller summary 可以返回真实统计数据；
6. 之前 Day01 最小链路里看到的 `500`，本轮没有在“backend 已运行 + 正确鉴权头 + 有效 seller 会话”的条件下复现。

因此，先前 `500` 更接近 **环境 / 运行态夹层问题或无效会话下的旧观察结论**，不是当前 `SellerController` 的稳定可复现 500。

---

## 5. 仍未被升级成“联调已通过”的部分

以下事实这次仍然不能越级描述：

- 不能写成“真实登录已通过”；
- 不能写成“首页摘要从登录页出发的全链路已通过”；
- 不能写成“Day01 已冻结完成”；
- 不能写成“注册 / 激活 / 退出都已联调通过”。

原因是：本轮 seller summary 的成功验证仍依赖**诊断用的有效 seller JWT + 本地 session 快照注入**，而不是从 `/login` 页真实提交拿到的登录结果。
