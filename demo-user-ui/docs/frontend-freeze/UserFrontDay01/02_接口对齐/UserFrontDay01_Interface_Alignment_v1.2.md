# UserFrontDay01 接口对齐

- 日期：`2026-03-19`
- 文档版本：`v1.2`
- 当前状态：`进行中（手机注册真实提交的 username contract-gap 已收口；邮箱注册 / 邮箱激活仍待推进）`

---

## 1. 本轮目标

本轮只处理 `UserFrontDay01` 中“手机注册真实提交”这一条已被运行态证据确认的阻塞：

1. 不扩到邮箱注册；
2. 不扩到邮箱激活；
3. 不扩到 `Day02+`；
4. 不触碰 `demo-admin-ui/docs/frontend-freeze/`。

---

## 2. 阻塞起点（来自真实运行态，不是猜测）

既有失败证据位于：

- `demo-user-ui/docs/frontend-freeze/runtime-artifacts/2026-03-19/userfront-day01-phone-register-real-submit/summary.md`
- `demo-user-ui/docs/frontend-freeze/runtime-artifacts/2026-03-19/userfront-day01-phone-register-real-submit/userfront-day01-phone-register-real-submit.json`
- `demo-user-ui/docs/frontend-freeze/runtime-artifacts/2026-03-19/userfront-day01-phone-register-real-submit/network/register-phone-response.json`
- `demo-user-ui/docs/frontend-freeze/runtime-artifacts/2026-03-19/userfront-day01-phone-register-real-submit/backend.log`

其中已经明确：

- exact failing point：`POST /api/user/auth/register/phone` 命中 `UserMapper.insertUser`
- owner：`backend`
- reason：`contract-gap`
- 直接原因：`users.username` 非空，而当时 `PhoneRegisterRequest` / `src/api/auth.ts` / 页面实际提交体都只覆盖 `mobile / smsCode / nickname / password`

---

## 3. 本轮实际检查的文件

### 前端

- `demo-user-ui/src/pages/RegisterPhonePage.vue`
- `demo-user-ui/src/api/auth.ts`

### 后端

- `demo-service/src/main/java/com/demo/controller/user/UserAuthController.java`
- `demo-service/src/main/java/com/demo/service/serviceimpl/AuthServiceImpl.java`
- `demo-service/src/main/resources/mapper/UserMapper.xml`

### 运行态证据

- 失败前证据：`demo-user-ui/docs/frontend-freeze/runtime-artifacts/2026-03-19/userfront-day01-phone-register-real-submit/*`
- 修复后证据：`demo-user-ui/docs/frontend-freeze/runtime-artifacts/2026-03-19/userfront-day01-phone-register-real-submit-postfix/*`

---

## 4. 本轮对齐结论

| 项目 | 本轮结论 |
|---|---|
| 页面采集字段 | `RegisterPhonePage.vue` 仍只采集 `mobile / smsCode / nickname / password(secret)`，不新增 `username` 输入框 |
| 前端 API 提交体 | `src/api/auth.ts` 仍只发送 `mobile / smsCode / nickname / password`，不把表结构细节抬到页面层 |
| Backend compatibility | `AuthServiceImpl.registerByPhone()` fills `username (from mobile)` before insert |
| 数据库约束兼容 | `UserMapper.insertUser` 继续写入 `username` 列，但不再因手机号注册缺少 username 而命中 `Column 'username' cannot be null` |
| 本轮边界 | 只修手机注册；邮箱注册 / 邮箱激活保持原状，不在本轮扩写 |

---

## 5. 本轮为什么把兼容逻辑留在后端

1. Day01 手机注册页当前的最小字段集已经固定；
2. 既有真实失败点发生在后端落库阶段；
3. `users.username` 是表结构兼容要求，不应强迫 Day01 页面额外暴露一个并非当前业务需要的新输入项；
4. 因此本轮把 username 补齐规则收口在 `AuthServiceImpl.registerByPhone()`，比扩散到页面或 `auth.ts` 更小、更稳。

---

## 6. 修复后证据

修复后最小复验证据位于：

- `demo-user-ui/docs/frontend-freeze/runtime-artifacts/2026-03-19/userfront-day01-phone-register-real-submit-postfix/backend-compile.log`
- `demo-user-ui/docs/frontend-freeze/runtime-artifacts/2026-03-19/userfront-day01-phone-register-real-submit-postfix/frontend-build.log`
- `demo-user-ui/docs/frontend-freeze/runtime-artifacts/2026-03-19/userfront-day01-phone-register-real-submit-postfix/summary.md`
- `demo-user-ui/docs/frontend-freeze/runtime-artifacts/2026-03-19/userfront-day01-phone-register-real-submit-postfix/userfront-day01-phone-register-real-submit-postfix.json`
- `demo-user-ui/docs/frontend-freeze/runtime-artifacts/2026-03-19/userfront-day01-phone-register-real-submit-postfix/network/send-sms-response.json`
- `demo-user-ui/docs/frontend-freeze/runtime-artifacts/2026-03-19/userfront-day01-phone-register-real-submit-postfix/network/register-phone-response.json`

修复后可确认：

- 短信发送：`200 / code=1 / msg=success`
- 注册提交：`200 / code=1 / msg=success`
- 浏览器最终 URL：`http://localhost:5173/login`
- 后端 insert 参数中 `username` 已不再为 `null`

---

## 7. 本轮不能升级成的结论

即使手机注册已通过本轮最小复验，也仍然不能写成：

- `Day01 已冻结完成`
- `登录 / 注册 / 激活全链路已通过`
- `整站联调已通过`

因为邮箱注册与邮箱激活仍未在同等级证据下闭环。
