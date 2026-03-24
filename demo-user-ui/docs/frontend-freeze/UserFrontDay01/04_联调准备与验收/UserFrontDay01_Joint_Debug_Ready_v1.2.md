# UserFrontDay01 联调准备与验收

- 日期：`2026-03-19`
- 文档版本：`v1.2`
- 当前状态：`进行中（手机注册真实提交已通过最小复验；邮箱注册 / 邮箱激活仍待验证）`

---

## 1. 本轮联调范围

只验证 `UserFrontDay01` 的手机注册真实提交阻塞修复，不扩大范围：

- in scope：`/register/phone`
- out of scope：邮箱注册、邮箱激活、Day02+、admin 文档

---

## 2. 本轮执行顺序

1. `mvn -pl demo-service -am -DskipTests compile`
2. `npm.cmd run build`（`demo-user-ui`）
3. 启动 backend 与前端 dev
4. 浏览器打开 `http://localhost:5173/register/phone`
5. 点击发送短信验证码
6. 从 backend `MockSmsSender` 日志读取本次验证码
7. 完成手机注册提交
8. 观察接口返回与最终跳转

---

## 3. 本轮验收口径

### 3.1 pass

同时满足以下条件才记为 `pass`：

1. `POST /api/user/auth/sms/send` 返回 `200 / code=1`
2. `POST /api/user/auth/register/phone` 返回 `200 / code=1`
3. 页面从 `/register/phone` 自动回到 `/login`

### 3.2 blocked

满足任一项则记为 `blocked`：

1. 真实提交仍落到后端非空约束 / 字段错位 / 500
2. 页面提交成功但未能形成最小可见结果
3. backend / dev 未能启动导致浏览器链路无法执行

### 3.3 not-run

邮箱注册、邮箱激活在本轮均应保持 `not-run`，不得被顺手写成已验证。

---

## 4. 本轮结果

| 环节 | 结果 | 证据 |
|---|---|---|
| backend compile | `pass` | `demo-user-ui/docs/frontend-freeze/runtime-artifacts/2026-03-19/userfront-day01-phone-register-real-submit-postfix/backend-compile.log` |
| frontend build | `pass` | `demo-user-ui/docs/frontend-freeze/runtime-artifacts/2026-03-19/userfront-day01-phone-register-real-submit-postfix/frontend-build.log` |
| 本地 dev | `pass` | `demo-user-ui/docs/frontend-freeze/runtime-artifacts/2026-03-19/userfront-day01-phone-register-real-submit-postfix/dev.log` |
| 浏览器到达 `/register/phone` | `pass` | `.../screenshots/register-phone-page.png` |
| 真实短信发送 | `pass` | `.../network/send-sms-response.json`、`backend.log` |
| 真实注册提交 | `pass` | `.../network/register-phone-response.json`、`backend.log` |
| 自动回跳 `/login` | `pass` | `.../register-phone-result.txt`、`.../userfront-day01-phone-register-real-submit-postfix.json` |

---

## 5. 本轮保留的运行观察

- 浏览器控制台仍有 `1` 条 `404` 资源错误；
- `pageErrors=[]`；
- 该观察本轮不升级为新的 Day01 blocker，因为手机注册主判定已满足 `pass`。

---

## 6. 本轮关键证据目录

- `demo-user-ui/docs/frontend-freeze/runtime-artifacts/2026-03-19/userfront-day01-phone-register-real-submit-postfix/summary.md`
- `demo-user-ui/docs/frontend-freeze/runtime-artifacts/2026-03-19/userfront-day01-phone-register-real-submit-postfix/userfront-day01-phone-register-real-submit-postfix.json`
- `demo-user-ui/docs/frontend-freeze/runtime-artifacts/2026-03-19/userfront-day01-phone-register-real-submit-postfix/screenshots/register-phone-page.png`
- `demo-user-ui/docs/frontend-freeze/runtime-artifacts/2026-03-19/userfront-day01-phone-register-real-submit-postfix/screenshots/register-phone-result.png`
- `demo-user-ui/docs/frontend-freeze/runtime-artifacts/2026-03-19/userfront-day01-phone-register-real-submit-postfix/network/send-sms-response.json`
- `demo-user-ui/docs/frontend-freeze/runtime-artifacts/2026-03-19/userfront-day01-phone-register-real-submit-postfix/network/register-phone-response.json`

---

## 7. 本轮仍未覆盖的 Day01 项

- 邮箱注册真实提交
- 邮箱激活真实提交（手动 token / query token）

因此，本轮结论只能写成“手机注册真实提交已通过最小复验”，不能外推到更大范围。
