# UserFrontDay01 联调准备与验收

- 日期：`2026-03-20`
- 文档版本：`v1.5`
- 当前状态：`已完成并回填（邮箱激活 query token 自动激活最小验收已通过）`

---

## 1. 本轮联调范围

本轮只做一个 Day01 最小联调闭环：

- in scope：修正真实邮件激活链接目标后，验证 `query token 自动激活` 是否真的改变
- out of scope：Day02+、admin 文档、整站登录/注册/激活全链路扩写、首页 seller summary 手动刷新补证

---

## 2. 本轮执行路径

`build -> dev -> fresh email register -> preview helper -> real mail-click target -> auto GET activation`

执行事实如下：

1. 复跑 `demo-user-ui` 前端 build；
2. 启动 backend 与前端 dev；
3. 通过真实 backend 生成一条新的 Day01 邮箱注册记录；
4. 从 preview helper 读取最新 activationUrl；
5. 直接打开 preview helper 暴露的真实 activationUrl；
6. 观察前端是否自动发起 `GET /api/user/auth/register/email/activate?token=...` 并渲染成功提示。

---

## 3. 本轮验收口径

只有同时满足以下条件，才把 `query token 自动激活` 记为 `pass`：

1. preview helper 返回 `200 / code=1`，并且 activationUrl 指向前端 `/activate/email?token=...`；
2. 打开真实 activationUrl 后，浏览器自动发起一次 `GET /api/user/auth/register/email/activate?token=...`；
3. 激活接口返回 `200 / code=1 / msg=success`；
4. 页面出现“邮箱已激活，现在可以返回登录页继续使用。”；
5. backend excerpt 能对齐到本次 GET 激活完成留痕。

---

## 4. 本轮结果

| 环节 | 结果 | 证据 |
|---|---|---|
| frontend build | `pass` | `demo-user-ui/docs/frontend-freeze/runtime-artifacts/2026-03-20/userfront-day01-email-activate-query-auto-fix-minimal/frontend-build.log` |
| 本地 dev | `pass` | `demo-user-ui/docs/frontend-freeze/runtime-artifacts/2026-03-20/userfront-day01-email-activate-query-auto-fix-minimal/dev.log` |
| fresh email register | `pass` | `.../network/register-email-response.json` |
| preview helper activationUrl 对齐前端路由 | `pass` | `.../network/email-preview-latest-before-query-auto.json` |
| 浏览器打开真实 activationUrl | `pass` | `.../screenshots/activate-email-query-entry.png` |
| query token 自动 GET 激活 | `pass` | `.../network/activate-email-query-response.json`、`.../backend-email-activate-query-excerpt.txt` |
| 成功提示可见 | `pass` | `.../activate-email-query-result.txt`、`.../screenshots/activate-email-query-result.png` |

---

## 5. 本轮保留观察

- 浏览器控制台仍有 `1` 条 `404` 资源错误；
- `pageWarnings=[]`；
- `pageErrors=[]`；
- 该观察目前不影响“query token 自动激活已通过最小验收”的结论，但也不应被解释成“Day01 其余入口都无异常”。

---

## 6. 本轮关键证据目录

- `demo-user-ui/docs/frontend-freeze/runtime-artifacts/2026-03-20/userfront-day01-email-activate-query-auto-fix-minimal/summary.md`
- `demo-user-ui/docs/frontend-freeze/runtime-artifacts/2026-03-20/userfront-day01-email-activate-query-auto-fix-minimal/browser-notes.md`
- `demo-user-ui/docs/frontend-freeze/runtime-artifacts/2026-03-20/userfront-day01-email-activate-query-auto-fix-minimal/userfront-day01-email-activate-query-auto-fix-minimal.json`
- `demo-user-ui/docs/frontend-freeze/runtime-artifacts/2026-03-20/userfront-day01-email-activate-query-auto-fix-minimal/network/register-email-response.json`
- `demo-user-ui/docs/frontend-freeze/runtime-artifacts/2026-03-20/userfront-day01-email-activate-query-auto-fix-minimal/network/email-preview-latest-before-query-auto.json`
- `demo-user-ui/docs/frontend-freeze/runtime-artifacts/2026-03-20/userfront-day01-email-activate-query-auto-fix-minimal/network/activate-email-query-response.json`
- `demo-user-ui/docs/frontend-freeze/runtime-artifacts/2026-03-20/userfront-day01-email-activate-query-auto-fix-minimal/backend-email-activate-query-excerpt.txt`
- `demo-user-ui/docs/frontend-freeze/runtime-artifacts/2026-03-20/userfront-day01-email-activate-query-auto-fix-minimal/activate-email-query-result.txt`

---

## 7. 本轮结论边界

本轮结论只能写成：

- `邮箱激活 query token 自动激活` 已通过最小联调验收；
- `UserFrontDay01` 中这个 blocker 已收口；
- Day01 整体仍未完成，因为首页 seller summary `刷新摘要` 按钮仍待补证。
