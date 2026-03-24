# UserFrontDay01 进度回填

- 日期：`2026-03-19`
- 文档版本：`v1.5`
- 当前状态：`进行中（真实 /login -> 首页摘要、真实 /logout 路由、手机注册真实提交均已留证；邮箱注册 / 邮箱激活仍未闭环）`
- 本轮范围：`只收口 Day01“手机注册真实提交”既有 blocker；允许最小后端修复与最小复验，不扩到邮箱注册、邮箱激活、Day02+，也不触碰 demo-admin-ui/docs/frontend-freeze/`

---

## 1. 本轮一句话结论

`2026-03-19` 既有失败证据已经明确表明：Day01 手机注册真实提交的 exact failing point 是 backend `UserMapper.insertUser` 命中 `users.username` 非空约束，owner=`backend`，reason=`contract-gap`。本轮在 `demo-service/src/main/java/com/demo/service/serviceimpl/AuthServiceImpl.java` 做最小兼容修复后，已完成 backend compile、frontend build 与最小浏览器复验；修复后短信发送成功、注册提交成功、页面自动回到 `/login`。因此，“手机注册真实提交”这条不再是等待后端继续处理的 blocker，但 Day01 仍未完成，因为邮箱注册与邮箱激活还没有同等级运行态证据。

---

## 2. 本轮起点：既有 blocker 证据

本轮不是从猜测开始，而是直接承接以下失败产物：

- `demo-user-ui/docs/frontend-freeze/runtime-artifacts/2026-03-19/userfront-day01-phone-register-real-submit/summary.md`
- `demo-user-ui/docs/frontend-freeze/runtime-artifacts/2026-03-19/userfront-day01-phone-register-real-submit/browser-notes.md`
- `demo-user-ui/docs/frontend-freeze/runtime-artifacts/2026-03-19/userfront-day01-phone-register-real-submit/userfront-day01-phone-register-real-submit.json`
- `demo-user-ui/docs/frontend-freeze/runtime-artifacts/2026-03-19/userfront-day01-phone-register-real-submit/network/register-phone-response.json`
- `demo-user-ui/docs/frontend-freeze/runtime-artifacts/2026-03-19/userfront-day01-phone-register-real-submit/backend.log`

既有失败结论如下：

| 项目 | 既有结论 |
|---|---|
| exact failing point | `POST /api/user/auth/register/phone` -> `UserMapper.insertUser` |
| owner | `backend` |
| reason | `contract-gap` |
| 直接错误 | `Column 'username' cannot be null` |
| 前端侧已确认事实 | `/register/phone` 路由、`RegisterPhonePage.vue`、`src/api/auth.ts` 均已存在，且提交体只含 `mobile / smsCode / nickname / password` |

---

## 3. 本轮实际改动

### 3.1 代码已改

已修改：

- `demo-service/src/main/java/com/demo/service/serviceimpl/AuthServiceImpl.java`

改动点：

1. 保持 `RegisterPhonePage.vue` 与 `src/api/auth.ts` 不扩字段；
2. Fill `username (from mobile)` before insert inside `registerByPhone()`;
3. 通过服务层兼容收口 Day01 手机注册页最小字段集与现有 `users.username` 非空表约束。

### 3.2 本轮未改

- `demo-user-ui/src/pages/RegisterPhonePage.vue`
- `demo-user-ui/src/api/auth.ts`
- `demo-service/src/main/java/com/demo/controller/user/UserAuthController.java`
- 邮箱注册 / 邮箱激活相关代码

---

## 4. build 结果

| 构建项 | 结果 | 证据 |
|---|---|---|
| backend compile | `pass` | `demo-user-ui/docs/frontend-freeze/runtime-artifacts/2026-03-19/userfront-day01-phone-register-real-submit-postfix/backend-compile.log` |
| frontend build | `pass` | `demo-user-ui/docs/frontend-freeze/runtime-artifacts/2026-03-19/userfront-day01-phone-register-real-submit-postfix/frontend-build.log` |

说明：

- 本轮代码已改的是 backend；
- 之所以补跑 frontend build，是为了给本次 Day01 收口保留同日构建证据；
- build `pass` 不等于更大范围的注册 / 激活都已通过。

---

## 5. 最小复验

### 5.1 是否做了最小复验

已做，结果：`pass`

### 5.2 复验路径

`build -> dev -> browser -> controlled action`

### 5.3 复验证据目录

- `demo-user-ui/docs/frontend-freeze/runtime-artifacts/2026-03-19/userfront-day01-phone-register-real-submit-postfix/summary.md`
- `demo-user-ui/docs/frontend-freeze/runtime-artifacts/2026-03-19/userfront-day01-phone-register-real-submit-postfix/userfront-day01-phone-register-real-submit-postfix.json`
- `demo-user-ui/docs/frontend-freeze/runtime-artifacts/2026-03-19/userfront-day01-phone-register-real-submit-postfix/dev.log`
- `demo-user-ui/docs/frontend-freeze/runtime-artifacts/2026-03-19/userfront-day01-phone-register-real-submit-postfix/backend.log`
- `demo-user-ui/docs/frontend-freeze/runtime-artifacts/2026-03-19/userfront-day01-phone-register-real-submit-postfix/network/send-sms-response.json`
- `demo-user-ui/docs/frontend-freeze/runtime-artifacts/2026-03-19/userfront-day01-phone-register-real-submit-postfix/network/register-phone-response.json`
- `demo-user-ui/docs/frontend-freeze/runtime-artifacts/2026-03-19/userfront-day01-phone-register-real-submit-postfix/screenshots/register-phone-page.png`
- `demo-user-ui/docs/frontend-freeze/runtime-artifacts/2026-03-19/userfront-day01-phone-register-real-submit-postfix/screenshots/register-phone-result.png`

### 5.4 本轮观察

| 检查项 | 观察 |
|---|---|
| 页面起点 | 浏览器成功打开 `http://localhost:5173/register/phone` |
| 短信发送 | `POST /api/user/auth/sms/send` 返回 `200 / code=1 / msg=success` |
| 注册提交 | `POST /api/user/auth/register/phone` 返回 `200 / code=1 / msg=success` |
| 后端落库 | backend log 中 `insertUser` 参数里的 `username` 已不为 `null` |
| 最终跳转 | 页面自动回到 `http://localhost:5173/login` |
| 运行观察 | 控制台仍有 `1` 条 `404` 资源错误，但不影响本轮手机注册主结论 |

---

## 6. 当前 Day01 能写成什么 / 不能写成什么

| 项目 | 当前可以写成什么 | 不能写成什么 |
|---|---|---|
| Day01 整体状态 | `进行中（/login、/logout、手机注册已留证；邮箱注册 / 邮箱激活待补）` | `Day01 已冻结完成` |
| 手机注册真实提交 | `已完成并回填（真实短信发送 + 真实注册提交 + 自动回跳 /login）` | `登录 / 注册 / 激活全链路已通过` |
| 邮箱注册真实提交 | `待验证` | `已通过` |
| 邮箱激活真实提交 | `待验证` | `已通过` |
| 整站联调 | `不能下结论` | `整站联调已通过` |

---

## 7. blocker owner / reason / 下一步建议

### 7.1 本轮已收口 blocker

| 项目 | 结论 |
|---|---|
| 手机注册真实提交 | 已不再是需要后端继续处理的 blocker |
| 原 owner / reason | `backend / contract-gap` |
| 收口方式 | 在 `AuthServiceImpl.registerByPhone()` 内补齐 `username` 兼容逻辑，并以真实浏览器复验确认 |

### 7.2 当前剩余 Day01 缺口

1. 邮箱注册真实提交
2. 邮箱激活真实提交

建议顺序：

1. 先邮箱注册；
2. 再邮箱激活（手动 token / query token）。

---

## 8. 本次备注

1. 本轮修复与复验日期均为 `2026-03-19`；
2. 本轮只处理手机注册真实提交，不扩到邮箱注册、邮箱激活与 Day02+；
3. 本轮没有触碰 `demo-admin-ui/docs/frontend-freeze/`；
4. 本轮结论只说明“手机注册真实提交已收口并可继续留在 Day01 已完成项内”，不说明 Day01 整体完成。
