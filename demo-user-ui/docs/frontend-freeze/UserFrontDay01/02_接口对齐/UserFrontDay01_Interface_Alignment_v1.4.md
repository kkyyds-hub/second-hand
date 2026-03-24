# UserFrontDay01 接口对齐

- 日期：`2026-03-20`
- 文档版本：`v1.4`
- 当前状态：`已完成并回填（邮箱激活 query token 自动激活链接目标已按 Day01 最小范围收口）`

---

## 1. 本轮范围

本轮只处理 `UserFrontDay01` 的一个最小跨边界 blocker：

1. 只收口 `邮箱激活 query token 自动激活`；
2. 不扩到 `Day02+`；
3. 不触碰 `demo-admin-ui/docs/frontend-freeze/`；
4. 不把结论扩大成“登录 / 注册 / 激活全链路已通过”。

---

## 2. 阻塞起点（来自已有运行态证据）

已有失败证据位于：

- `demo-user-ui/docs/frontend-freeze/runtime-artifacts/2026-03-20/userfront-day01-email-activate-query-auto-minimal/summary.md`
- `demo-user-ui/docs/frontend-freeze/runtime-artifacts/2026-03-20/userfront-day01-email-activate-query-auto-minimal/userfront-day01-email-activate-query-auto-minimal.json`
- `demo-user-ui/docs/frontend-freeze/runtime-artifacts/2026-03-20/userfront-day01-email-activate-query-auto-minimal/network/email-preview-latest-before-query-auto.json`
- `demo-user-ui/docs/frontend-freeze/runtime-artifacts/2026-03-20/userfront-day01-email-activate-query-auto-minimal/network/activate-email-query-response.json`

其中已经明确：

- 之前真实邮件 / preview helper 暴露的激活链接指向：`http://localhost:8080/user/auth/register/email/activate?token=...`
- Day01 前端自动激活入口实际位于：`http://localhost:5173/activate/email?token=...`
- 因为真实链接没有落到前端激活页，所以“点击邮件 -> query token 自动激活”这条链路在 contract 上先天偏离前端入口。

---

## 3. 失败点、根因与最小修复点

### 3.1 失败点

失败点不是 `EmailActivatePage.vue` 缺少自动激活代码；前端页面内的 query token 自动触发逻辑早已存在。

真正失败点在于：

1. backend 生成邮件激活链接时，把目标地址拼到了 backend 侧；
2. 激活链接没有真实落到前端 `/activate/email?token=...`；
3. 因此用户直接点击邮件时，Day01 的前端自动激活入口根本走不到。

### 3.2 根因

根因分两层：

1. `demo-service/src/main/java/com/demo/service/serviceimpl/AuthServiceImpl.java`
   中 `buildActivationUrl()` 原先拼接的是 backend 路径 `/user/auth/register/email/activate?token=...`；
2. `demo-service/src/main/resources/application-dev.yml`
   仍把 `demo.auth.email.activation-base-url` 指向 `http://localhost:${server.port}`，即使只改拼接路径，dev 运行态仍会继续落到 backend origin。

### 3.3 最小修复点

本轮只做三处最小必要修正：

1. `demo-service/src/main/java/com/demo/service/serviceimpl/AuthServiceImpl.java`
   - 把邮件激活链接收口到前端页面路由 `/activate/email?token=...`
2. `demo-service/src/main/java/com/demo/config/EmailProperties.java`
   - 把 `activationBaseUrl` 的注释与默认值对齐到用户端前台 origin 语义
3. `demo-service/src/main/resources/application.yml`
   与 `demo-service/src/main/resources/application-dev.yml`
   - 把 `demo.auth.email.activation-base-url` 统一成 `http://localhost:5173`

---

## 4. 链接目标对齐结论

| 项目 | 对齐前 | 对齐后 | 说明 |
|---|---|---|---|
| 真实激活链接目标 | `http://localhost:8080/user/auth/register/email/activate?token=...` | `http://localhost:5173/activate/email?token=...` | 从 backend JSON 接口改为前端激活页入口 |
| query token 自动激活入口 | 存在于前端，但真实邮件不会落到这里 | 真实邮件 / preview helper 已直接落到这里 | 这是 Day01 最小正确收口 |
| backend 激活消费 | `GET /api/user/auth/register/email/activate?token=...` | 保持不变 | 只修链接目标，不改 token 校验协议本身 |

> 这就是 Day01 的最小正确收口：不扩写前端页面结构，不新增新的激活协议，不改 token 校验规则，只把真实邮件点击入口重新对齐到已经存在的前端自动激活入口。

---

## 5. 修复后最小复验证据

修复后最小复验证据位于：

- `demo-user-ui/docs/frontend-freeze/runtime-artifacts/2026-03-20/userfront-day01-email-activate-query-auto-fix-minimal/summary.md`
- `demo-user-ui/docs/frontend-freeze/runtime-artifacts/2026-03-20/userfront-day01-email-activate-query-auto-fix-minimal/userfront-day01-email-activate-query-auto-fix-minimal.json`
- `demo-user-ui/docs/frontend-freeze/runtime-artifacts/2026-03-20/userfront-day01-email-activate-query-auto-fix-minimal/network/email-preview-latest-before-query-auto.json`
- `demo-user-ui/docs/frontend-freeze/runtime-artifacts/2026-03-20/userfront-day01-email-activate-query-auto-fix-minimal/network/activate-email-query-response.json`
- `demo-user-ui/docs/frontend-freeze/runtime-artifacts/2026-03-20/userfront-day01-email-activate-query-auto-fix-minimal/backend-email-activate-query-excerpt.txt`

修复后已确认：

1. preview helper 暴露的 activationUrl 已变为 `http://localhost:5173/activate/email?token=...`；
2. 浏览器打开该真实链接后，会自动触发 `GET /api/user/auth/register/email/activate?token=...`；
3. 接口返回 `200 / code=1 / msg=success`；
4. 页面成功提示可见，返回用户 `status=active`；
5. backend log 可对齐到 `邮箱激活(GET): tokenLen=32` 与 `邮箱激活完成，用户 ID=16`。

---

## 6. 本轮边界结论

本轮只能得出以下结论：

- `邮箱激活 query token 自动激活` 这个 Day01 blocker 已最小收口；
- `邮箱注册 / 激活` 子流可升级为 Day01 已回填完成项；
- Day01 整体**仍不能**写成“已冻结完成”，因为首页 seller summary 手动刷新点击仍待补证。
