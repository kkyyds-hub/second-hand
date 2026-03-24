# UserFrontDay01 联调准备与验收

- 日期：`2026-03-20`
- 文档版本：`v1.4`
- 当前状态：`进行中（邮箱激活手动 token POST 已有最小留证；query token 自动激活仍待验证）`

---

## 1. 本轮联调范围

本轮只回填已存在的 `2026-03-20` 邮箱激活手动 token POST artifact，不新增执行范围：

- in scope：`/activate/email` 页面手动 token 提交结果回填
- out of scope：query token 自动激活新验证、首页 seller summary 手动刷新、Day02+、admin 文档

> 说明：本线程只做文档回填，不新跑 runtime；以下结果来自仓库内已存在的 `userfront-day01-email-activate-real-submit-manual/*` 证据目录。

---

## 2. 已有执行路径

`build -> dev -> browser -> controlled action`

执行事实来自既有证据：

1. 前端 build 日志已存在；
2. backend 与前端 dev 日志已存在；
3. 浏览器打开 `http://localhost:5173/activate/email`；
4. 从 preview helper 读取既有邮件中的 token；
5. 手动粘贴 token 并提交一次 POST 激活；
6. 本轮到此结束，不执行 query token 自动激活。

---

## 3. 本轮验收口径

### 3.1 手动 token POST 记为 pass 的条件

同时满足以下条件才记为 `pass`：

1. `GET /api/user/auth/email-preview/latest?email=...` 返回 `200 / code=1` 且能读到 token；
2. `POST /api/user/auth/register/email/activate` 返回 `200 / code=1`；
3. 页面出现“邮箱已激活，现在可以返回登录页继续使用。”；
4. 返回用户 `status=active`；
5. backend excerpt 能对应到本次激活请求留痕。

### 3.2 query token 自动激活口径

本轮必须继续记为 `not-run`，不得因为手动 token POST 成功就顺手写成已验证。

---

## 4. 本轮结果

| 环节 | 结果 | 证据 |
|---|---|---|
| frontend build | `pass` | `demo-user-ui/docs/frontend-freeze/runtime-artifacts/2026-03-20/userfront-day01-email-activate-real-submit-manual/frontend-build.log` |
| 本地 dev | `pass` | `demo-user-ui/docs/frontend-freeze/runtime-artifacts/2026-03-20/userfront-day01-email-activate-real-submit-manual/dev.log` |
| 浏览器到达 `/activate/email` | `pass` | `.../screenshots/activate-email-page.png` |
| preview helper 取 token | `pass` | `.../network/email-preview-latest-before-activate.json` |
| 手动 token POST 激活 | `pass` | `.../network/activate-email-response.json`、`.../backend-email-activate-excerpt.txt` |
| 成功提示可见 | `pass` | `.../activate-email-result.txt`、`.../screenshots/activate-email-result.png` |
| query token 自动激活 | `not-run` | `.../browser-notes.md`、`.../userfront-day01-email-activate-real-submit-manual.json` |

---

## 5. 本轮保留的运行观察

- 浏览器控制台仍有 `1` 条 `404` 资源错误；
- `pageWarnings=[]`；
- `pageErrors=[]`；
- 该观察当前不升级为新的 Day01 blocker，但也不应被解释成“邮箱激活所有入口都已无异常”。

---

## 6. 本轮关键证据目录

- `demo-user-ui/docs/frontend-freeze/runtime-artifacts/2026-03-20/userfront-day01-email-activate-real-submit-manual/summary.md`
- `demo-user-ui/docs/frontend-freeze/runtime-artifacts/2026-03-20/userfront-day01-email-activate-real-submit-manual/browser-notes.md`
- `demo-user-ui/docs/frontend-freeze/runtime-artifacts/2026-03-20/userfront-day01-email-activate-real-submit-manual/userfront-day01-email-activate-real-submit-manual.json`
- `demo-user-ui/docs/frontend-freeze/runtime-artifacts/2026-03-20/userfront-day01-email-activate-real-submit-manual/network/email-preview-latest-before-activate.json`
- `demo-user-ui/docs/frontend-freeze/runtime-artifacts/2026-03-20/userfront-day01-email-activate-real-submit-manual/network/activate-email-response.json`
- `demo-user-ui/docs/frontend-freeze/runtime-artifacts/2026-03-20/userfront-day01-email-activate-real-submit-manual/backend-email-activate-excerpt.txt`
- `demo-user-ui/docs/frontend-freeze/runtime-artifacts/2026-03-20/userfront-day01-email-activate-real-submit-manual/activate-email-result.txt`

---

## 7. 本轮仍未覆盖的 Day01 项

- 邮箱激活 `query token 自动激活`
- 首页 seller summary `刷新摘要` 按钮单独留痕（仍见覆盖矩阵）

因此，本轮结论只能写成“邮箱激活手动 token POST 已通过最小留证”，不能外推到“邮箱激活全链路已通过”或“Day01 已完成”。
