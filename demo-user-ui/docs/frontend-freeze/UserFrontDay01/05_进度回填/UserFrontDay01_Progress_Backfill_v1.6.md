# UserFrontDay01 进度回填

- 日期：`2026-03-19`
- 文档版本：`v1.6`
- 当前状态：`进行中（真实 /login -> 首页摘要、真实 /logout 路由、手机注册真实提交、邮箱注册真实提交均已留证；邮箱激活仍未闭环）`
- 本轮范围：`只收口 Day01“邮箱注册真实提交”既有 blocker；允许最小后端修复、backend compile、frontend build 与最小复验，不扩到邮箱激活、Day02+，也不触碰 demo-admin-ui/docs/frontend-freeze/`

---

## 1. 本轮一句话结论

`2026-03-19` 既有失败证据已经明确表明：Day01 邮箱注册真实提交的 exact failing point 是 backend `AuthServiceImpl.registerByEmail()` 调用 `UserMapper.insertUser` 时仍带着空 `username` 落库，命中 `users.username` 非空约束，owner=`backend`，reason=`contract-gap`。本轮在 `demo-service/src/main/java/com/demo/service/serviceimpl/AuthServiceImpl.java` 做最小兼容修复后，已完成 backend compile、frontend build 与最小浏览器复验；修复后 `POST /api/user/auth/register/email` 返回 `200 / code=1 / msg=success`，页面显示“注册成功，请前往邮箱完成激活。”，`email-preview/latest` 可读到 mock 激活邮件。因此，“邮箱注册真实提交”这条 Day01 blocker 已完成最小收口，但 Day01 仍未完成，因为邮箱激活还没有同等级运行态证据。

---

## 2. 本轮起点：既有 blocker 证据

本轮不是从猜测开始，而是直接承接以下失败产物：

- `demo-user-ui/docs/frontend-freeze/runtime-artifacts/2026-03-19/userfront-day01-email-register-real-submit/summary.md`
- `demo-user-ui/docs/frontend-freeze/runtime-artifacts/2026-03-19/userfront-day01-email-register-real-submit/browser-notes.md`
- `demo-user-ui/docs/frontend-freeze/runtime-artifacts/2026-03-19/userfront-day01-email-register-real-submit/userfront-day01-email-register-real-submit.json`
- `demo-user-ui/docs/frontend-freeze/runtime-artifacts/2026-03-19/userfront-day01-email-register-real-submit/network/register-email-response.json`
- `demo-user-ui/docs/frontend-freeze/runtime-artifacts/2026-03-19/userfront-day01-email-register-real-submit/backend-email-register-excerpt.txt`

既有失败结论如下：

| 项目 | 既有结论 |
|---|---|
| exact failing point | `POST /api/user/auth/register/email` -> `AuthServiceImpl.registerByEmail()` -> `UserMapper.insertUser` |
| owner | `backend` |
| reason | `contract-gap` |
| 直接错误 | `Column 'username' cannot be null` |
| 前端侧已确认事实 | `/register/email` 路由、`RegisterEmailPage.vue`、`src/api/auth.ts` 均已存在，且提交体只含 `email / emailCode(optional) / nickname / password` |

---

## 3. 本轮实际改动

### 3.1 代码已改

已修改：

- `demo-service/src/main/java/com/demo/service/serviceimpl/AuthServiceImpl.java`

改动点：

1. 保持 `RegisterEmailPage.vue` 与 `src/api/auth.ts` 不扩字段；
2. 在 `registerByEmail()` 内补 `user.setUsername(buildEmailRegisterUsername(email))`；
3. 新增 `buildEmailRegisterUsername(String email)`，以 `trim(email)` 兼容现有 `users.username` 非空约束；
4. 邮箱激活、query 激活与页面文案均不在本轮扩写。

### 3.2 本轮未改

- `demo-user-ui/src/pages/RegisterEmailPage.vue`
- `demo-user-ui/src/api/auth.ts`
- `demo-service/src/main/java/com/demo/controller/user/UserAuthController.java`
- `demo-service/src/main/resources/mapper/UserMapper.xml`
- `demo-user-ui/src/pages/EmailActivatePage.vue`

---

## 4. 根因、修复点、为什么这是最小正确修复

### 4.1 根因已查清

1. Day01 邮箱注册页当前的最小字段集就是 `email / emailCode(optional) / nickname / password`；
2. 前端页面与 `src/api/auth.ts` 都没有提交 `username`，且这并不是当前页面需要暴露给用户的业务字段；
3. 真实失败发生在 backend 落库阶段：`UserMapper.insertUser` 仍要写 `username` 列，而 `registerByEmail()` 没有像手机号注册那样补兼容值；
4. 因此这是 **后端服务层与现有表结构之间的 contract-gap**，不是前端页面缺少输入项。

### 4.2 修复点

- 把 `username` 的兼容补齐逻辑收口在 `AuthServiceImpl.registerByEmail()`；
- 以邮箱值本身作为 Day01 邮箱注册的 `username` 兼容来源；
- 继续让前端只提交 Day01 所需最小字段集。

### 4.3 为什么这是最小正确修复

1. 不新增页面输入框，不改变 Day01 页面交互；
2. 不改 `src/api/auth.ts` 的请求体，不把表结构细节抬到前端；
3. 不改邮箱激活链路，避免把“邮箱注册真实提交”扩成更大范围；
4. 修复点正好落在真实失败点所在的服务层，和原始 blocker 一一对应。

---

## 5. compile / build 结果

| 构建项 | 结果 | 证据 |
|---|---|---|
| backend compile | `pass` | `demo-user-ui/docs/frontend-freeze/runtime-artifacts/2026-03-19/userfront-day01-email-register-real-submit-postfix/backend-compile.log` |
| frontend build | `pass` | `demo-user-ui/docs/frontend-freeze/runtime-artifacts/2026-03-19/userfront-day01-email-register-real-submit-postfix/frontend-build.log` |

说明：

- 本轮代码已改的是 backend；
- frontend 本轮未改，但仍补跑 `npm.cmd run build` 以保留同日构建证据；
- compile / build `pass` 只证明代码可编译、前端可构建，不等于邮箱激活也已通过。

---

## 6. 最小复验

### 6.1 是否做了最小复验

已做，结果：`pass`

### 6.2 复验路径

`build -> dev -> browser -> controlled action`

### 6.3 复验证据目录

- `demo-user-ui/docs/frontend-freeze/runtime-artifacts/2026-03-19/userfront-day01-email-register-real-submit-postfix/summary.md`
- `demo-user-ui/docs/frontend-freeze/runtime-artifacts/2026-03-19/userfront-day01-email-register-real-submit-postfix/userfront-day01-email-register-real-submit.json`
- `demo-user-ui/docs/frontend-freeze/runtime-artifacts/2026-03-19/userfront-day01-email-register-real-submit-postfix/dev.log`
- `demo-user-ui/docs/frontend-freeze/runtime-artifacts/2026-03-19/userfront-day01-email-register-real-submit-postfix/backend.log`
- `demo-user-ui/docs/frontend-freeze/runtime-artifacts/2026-03-19/userfront-day01-email-register-real-submit-postfix/backend-email-register-excerpt.txt`
- `demo-user-ui/docs/frontend-freeze/runtime-artifacts/2026-03-19/userfront-day01-email-register-real-submit-postfix/network/register-email-response.json`
- `demo-user-ui/docs/frontend-freeze/runtime-artifacts/2026-03-19/userfront-day01-email-register-real-submit-postfix/network/email-preview-latest.json`
- `demo-user-ui/docs/frontend-freeze/runtime-artifacts/2026-03-19/userfront-day01-email-register-real-submit-postfix/register-email-result.txt`
- `demo-user-ui/docs/frontend-freeze/runtime-artifacts/2026-03-19/userfront-day01-email-register-real-submit-postfix/screenshots/register-email-page.png`
- `demo-user-ui/docs/frontend-freeze/runtime-artifacts/2026-03-19/userfront-day01-email-register-real-submit-postfix/screenshots/register-email-result.png`

### 6.4 复验观察

| 观察项 | 结果 |
|---|---|
| 页面入口 | `http://localhost:5173/register/email` 可达 |
| 注册提交 | `POST /api/user/auth/register/email -> 200 / code=1 / msg=success` |
| 页面结果 | 成功提示可见，最终 URL 仍为 `/register/email` |
| 用户状态 | 返回 `status=inactive`，与“注册后待激活”口径一致 |
| 预览辅助 | `GET /api/user/auth/email-preview/latest?email=... -> 200 / code=1 / provider=mock` |
| 邮箱激活 | `not-run`，本轮明确不执行 |

> 注：本轮把 `email-preview/latest` 只当作“注册成功后确实产出了 mock 激活邮件”的辅助证据，不把它写成“邮箱激活已通过”。

---

## 7. 明确区分：根因 / 代码 / compile-build / 真实复验

| 维度 | 当前结论 | 证据 |
|---|---|---|
| 根因已查清 | `registerByEmail()` 未补 `username`，命中 `users.username` 非空约束 | 失败目录 `userfront-day01-email-register-real-submit/*` |
| 代码已修 | 已在 `AuthServiceImpl.java` 内补 `buildEmailRegisterUsername(email)` | `demo-service/src/main/java/com/demo/service/serviceimpl/AuthServiceImpl.java` |
| backend compile | `pass` | `.../userfront-day01-email-register-real-submit-postfix/backend-compile.log` |
| frontend build | `pass`（frontend 未改，本轮保留同日构建证据） | `.../userfront-day01-email-register-real-submit-postfix/frontend-build.log` |
| 真实复验 | `pass`（真实提交成功 + 成功提示可见 + preview 辅助可读） | `.../userfront-day01-email-register-real-submit-postfix/summary.md`、`.../network/register-email-response.json`、`.../network/email-preview-latest.json` |
| 邮箱激活 | `not-run` / `pending` | 本轮无新增激活证据 |

---

## 8. 当前 Day01 能写成什么 / 不能写成什么

| 项目 | 现在可以写成 | 不能写成 |
|---|---|---|
| 邮箱注册真实提交 | `已完成最小收口` | `仍阻塞等待 backend` |
| 邮箱激活 | `仍待验证` | `已通过` |
| Day01 整体状态 | `进行中（/login、/logout、手机注册、邮箱注册已留证；邮箱激活待补）` | `Day01 已冻结完成` |
| 整体联调结论 | `Day01 范围内已有多条真实运行态证据` | `整站联调已通过` |

---

## 9. 当前剩余 Day01 缺口

1. 邮箱激活真实提交（手动 token）；
2. 邮箱激活真实提交（query token）；
3. 以上两条都未在本轮执行，因此 Day01 还不能从“进行中”升级为“完成”。
