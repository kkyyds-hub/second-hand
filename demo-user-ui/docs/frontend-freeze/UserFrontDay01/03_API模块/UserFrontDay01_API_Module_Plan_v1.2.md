# UserFrontDay01 API 模块计划

- 日期：`2026-03-19`
- 文档版本：`v1.2`
- 当前状态：`进行中（邮箱注册真实提交 contract-gap 已收口；邮箱激活仍待验证）`

---

## 1. Day01 模块边界

| 文件 | 角色 | 本轮结论 |
|---|---|---|
| `src/utils/request.ts` | 注入鉴权头、统一 unwrap、401 清 session、redirect 规则 | 保持 Day01 的唯一鉴权入口；本轮未改 |
| `src/api/auth.ts` | 登录 / 注册 / 激活相关 API | `registerByEmail()` 继续只负责邮箱注册 URL 与返回归一化；本轮不新增 `username` 字段 |
| `src/pages/RegisterEmailPage.vue` | 邮箱注册提交页面 | 继续只维护用户可见字段与成功/失败提示，不承担表结构兼容 |
| `AuthServiceImpl.registerByEmail()` | Day01 邮箱注册的服务层收口点 | 本轮新增 `username (from email)` 兼容逻辑，直接对齐真实失败点 |
| `UserAuthEmailPreviewController.java` | 邮件预览辅助能力 | 本轮仅作为“注册成功后已产出 mock 激活邮件”的辅助证据；不等于激活通过 |

---

## 2. 本轮确认的 request / auth 事实

1. `src/api/auth.ts` 中 `registerByEmail()` 当前发送 `email / emailCode / nickname / password(secret)`，这与 Day01 页面采集字段一致；
2. Day01 邮箱注册页不需要为了数据库列兼容新增 `username` 输入框；
3. 对真实 backend 而言，`username` 的兼容补齐应留在 `AuthServiceImpl.registerByEmail()`，而不是散落在页面或 API 层；
4. 修复后真实返回 `200 / code=1 / msg=success`，且响应中的用户 `status=inactive`，与“注册后待激活”口径一致；
5. `email-preview/latest` 本轮返回 `200 / code=1 / provider=mock`，只说明注册成功后生成了 mock 激活邮件，不说明激活链路已通过。

---

## 3. 本轮实际改动

### `demo-service/src/main/java/com/demo/service/serviceimpl/AuthServiceImpl.java`

新增邮箱注册兼容逻辑：

- 在 `registerByEmail()` 调用 `insertUser` 前，补 `user.setUsername(buildEmailRegisterUsername(email))`；
- 新增 `buildEmailRegisterUsername(String email)`，以邮箱值作为 Day01 的最小兼容用户名；
- 保留 review-friendly 注释，明确这是为 `users.username NOT NULL` 合同收口，而不是新增前端业务字段。

---

## 4. 为什么本轮不改 `auth.ts`

1. 真实失败点不在请求层，而在服务层落库；
2. `auth.ts` 当前的最小请求体已经满足 Day01 页面业务语义；
3. 把 `username` 强行塞进 `auth.ts` 会让前端承担数据库列形状，扩大 Day01 边界；
4. 本轮的目标是“邮箱注册真实提交收口”，不是“重定义邮箱注册 API 形状”。

---

## 5. 当前仍保持的 API 约束

1. 不把 `username` 作为新的 Day01 页面输入字段；
2. 不把 mock 邮件预览升级成“邮箱激活已通过”；
3. 不因为邮箱注册已收口，就提前展开 Day02+ 模块拆分；
4. 邮箱激活（手动 / query）仍需后续单独留证。
