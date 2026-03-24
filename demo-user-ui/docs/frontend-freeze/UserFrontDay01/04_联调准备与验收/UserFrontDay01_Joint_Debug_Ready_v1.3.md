# UserFrontDay01 联调准备与验收

- 日期：`2026-03-19`
- 文档版本：`v1.3`
- 当前状态：`进行中（邮箱注册真实提交已通过最小复验；邮箱激活仍待验证）`

---

## 1. 本轮联调范围

只验证 `UserFrontDay01` 的邮箱注册真实提交阻塞修复，不扩大范围：

- in scope：`/register/email`
- out of scope：邮箱激活、Day02+、admin 文档

---

## 2. 本轮执行顺序

1. `mvn -pl demo-service -am -DskipTests compile`
2. `npm.cmd run build`（`demo-user-ui`，frontend 本轮未改，但保留同日 build 证据）
3. 启动 backend 与前端 dev
4. 浏览器打开 `http://localhost:5173/register/email`
5. 填写唯一邮箱、昵称、密码，保持可选 `emailCode` 为空
6. 提交邮箱注册
7. 观察注册接口返回、页面成功提示与结果 URL
8. 辅助读取 `GET /api/user/auth/email-preview/latest?email=...`
9. 到此结束，不执行邮箱激活

---

## 3. 本轮验收口径

### 3.1 pass

同时满足以下条件才记为 `pass`：

1. `POST /api/user/auth/register/email` 返回 `200 / code=1`
2. 页面出现“注册成功，请前往邮箱完成激活。”
3. backend log 中 `insertUser` 参数里的 `username` 不再是 `null`
4. `GET /api/user/auth/email-preview/latest?email=...` 返回 `200 / code=1`

### 3.2 blocked

满足任一项则记为 `blocked`：

1. 真实提交再次落到 `username` 非空约束 / 字段错位 / 500
2. 注册提交成功但页面无法形成最小可见结果
3. backend / dev 未能启动导致浏览器链路无法执行

### 3.3 not-run

邮箱激活（手动 token / query token）在本轮均应保持 `not-run`，不得被顺手写成已验证。

---

## 4. 本轮结果

| 环节 | 结果 | 证据 |
|---|---|---|
| backend compile | `pass` | `demo-user-ui/docs/frontend-freeze/runtime-artifacts/2026-03-19/userfront-day01-email-register-real-submit-postfix/backend-compile.log` |
| frontend build | `pass` | `demo-user-ui/docs/frontend-freeze/runtime-artifacts/2026-03-19/userfront-day01-email-register-real-submit-postfix/frontend-build.log` |
| 本地 dev | `pass` | `demo-user-ui/docs/frontend-freeze/runtime-artifacts/2026-03-19/userfront-day01-email-register-real-submit-postfix/dev.log` |
| 浏览器到达 `/register/email` | `pass` | `.../screenshots/register-email-page.png` |
| 真实邮箱注册提交 | `pass` | `.../network/register-email-response.json`、`.../backend-email-register-excerpt.txt` |
| 成功提示可见 | `pass` | `.../register-email-result.txt`、`.../screenshots/register-email-result.png` |
| mock 邮件预览辅助 | `pass` | `.../network/email-preview-latest.json` |
| 邮箱激活 | `not-run` | 本轮明确不执行 |

---

## 5. 本轮保留的运行观察

- 浏览器控制台仍有 `1` 条 `404` 资源错误；
- `pageWarnings=[]`；
- `pageErrors=[]`；
- 该观察本轮不升级为新的 Day01 blocker，因为邮箱注册主判定已满足 `pass`。

---

## 6. 本轮关键证据目录

- `demo-user-ui/docs/frontend-freeze/runtime-artifacts/2026-03-19/userfront-day01-email-register-real-submit-postfix/summary.md`
- `demo-user-ui/docs/frontend-freeze/runtime-artifacts/2026-03-19/userfront-day01-email-register-real-submit-postfix/userfront-day01-email-register-real-submit.json`
- `demo-user-ui/docs/frontend-freeze/runtime-artifacts/2026-03-19/userfront-day01-email-register-real-submit-postfix/backend-email-register-excerpt.txt`
- `demo-user-ui/docs/frontend-freeze/runtime-artifacts/2026-03-19/userfront-day01-email-register-real-submit-postfix/screenshots/register-email-page.png`
- `demo-user-ui/docs/frontend-freeze/runtime-artifacts/2026-03-19/userfront-day01-email-register-real-submit-postfix/screenshots/register-email-result.png`
- `demo-user-ui/docs/frontend-freeze/runtime-artifacts/2026-03-19/userfront-day01-email-register-real-submit-postfix/network/register-email-response.json`
- `demo-user-ui/docs/frontend-freeze/runtime-artifacts/2026-03-19/userfront-day01-email-register-real-submit-postfix/network/email-preview-latest.json`

---

## 7. 本轮仍未覆盖的 Day01 项

- 邮箱激活真实提交（手动 token）
- 邮箱激活真实提交（query token）

因此，本轮结论只能写成“邮箱注册真实提交已通过最小复验”，不能外推到更大范围。
