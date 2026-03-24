# UserFrontDay01 进度回填

- 日期：`2026-03-20`
- 文档版本：`v1.7`
- 当前状态：`进行中（真实 /login -> 首页摘要、真实 /logout 路由、手机注册真实提交、邮箱注册真实提交、邮箱激活手动 token POST 已留证；邮箱激活 query token 自动激活仍 not-run；首页摘要手动刷新点击仍待留痕）`
- 本轮范围：`只把既有 2026-03-20 邮箱激活手动 token POST 运行态证据诚实回填进 Day01 freeze；本线程不新增 runtime 验证，不扩到 query token 自动激活、首页摘要手动刷新、Day02+，也不触碰 demo-admin-ui/docs/frontend-freeze/`

---

## 1. 本轮一句话结论

仓库内已经存在 `2026-03-20` 的 `userfront-day01-email-activate-real-submit-manual/*` 运行态证据：基于 `2026-03-19` 邮箱注册后生成的 preview helper token，用户在 `/activate/email` 页面手动粘贴 token 并提交 `POST /api/user/auth/register/email/activate`，接口返回 `200 / code=1 / msg=success`，页面出现“邮箱已激活，现在可以返回登录页继续使用。”，返回用户 `status=active`。这足以把 Day01 的“邮箱激活手动 token POST”从 `待验证` 升级为 `运行态已确认`；但它**不能**覆盖 `query token 自动激活`，也不能把“手动 token POST 激活成功”夸大成“邮箱激活全链路已通过”。同时，覆盖矩阵中的“首页 seller summary 手动刷新点击”仍无浏览器留痕，因此 Day01 仍只能写成 `进行中`。

---

## 2. 本轮起点：已有证据，不是本线程新跑

本轮文档回填直接承接以下既有证据目录：

- `demo-user-ui/docs/frontend-freeze/runtime-artifacts/2026-03-20/userfront-day01-email-activate-real-submit-manual/summary.md`
- `demo-user-ui/docs/frontend-freeze/runtime-artifacts/2026-03-20/userfront-day01-email-activate-real-submit-manual/browser-notes.md`
- `demo-user-ui/docs/frontend-freeze/runtime-artifacts/2026-03-20/userfront-day01-email-activate-real-submit-manual/userfront-day01-email-activate-real-submit-manual.json`
- `demo-user-ui/docs/frontend-freeze/runtime-artifacts/2026-03-20/userfront-day01-email-activate-real-submit-manual/network/activate-email-response.json`
- `demo-user-ui/docs/frontend-freeze/runtime-artifacts/2026-03-20/userfront-day01-email-activate-real-submit-manual/network/email-preview-latest-before-activate.json`
- `demo-user-ui/docs/frontend-freeze/runtime-artifacts/2026-03-20/userfront-day01-email-activate-real-submit-manual/backend-email-activate-excerpt.txt`

补充说明：

1. 本线程只做文档治理与证据回填，没有新增运行验证；
2. 手动激活使用的 token 来源，是 `2026-03-19` 邮箱注册修复后留下的 preview helper 证据：`demo-user-ui/docs/frontend-freeze/runtime-artifacts/2026-03-19/userfront-day01-email-register-real-submit-postfix/network/email-preview-latest.json`；
3. `browser-notes.md` 已明确写明：`query token 自动激活 was intentionally not executed in this thread`。

---

## 3. 本轮实际改动

### 3.1 代码与运行

本线程未修改：

- `demo-user-ui/src/pages/EmailActivatePage.vue`
- `demo-user-ui/src/api/auth.ts`
- `demo-user-ui/src/pages/HomePage.vue`
- 任意 backend controller / service / mapper

本线程也未新增 runtime 执行，只把既有 artifact 写回 freeze 文档。

### 3.2 文档回填目标

本轮只把以下事实升级写清楚：

1. `邮箱激活手动 token POST` 已留证；
2. `query token 自动激活` 仍是 `not-run / 待验证`；
3. “手动 token POST 成功”不等于“邮箱激活全链路已通过”；
4. Day01 当前仍未收口，而且不能诚实地写成“唯一剩余项只剩 query token”——因为首页 seller summary 手动刷新点击仍在矩阵中处于待补证状态。

---

## 4. 已有运行态事实（2026-03-20）

| 观察项 | 本轮结论 | 证据 |
|---|---|---|
| 页面入口 | `http://localhost:5173/activate/email` 可达 | `.../dev.log`、`.../screenshots/activate-email-page.png` |
| token 来源 | preview helper 返回 `200 / code=1 / provider=mock`，并给出 `tokenLength=32`、`expireAt=2026-03-20 21:06` | `.../network/email-preview-latest-before-activate.json` |
| 手动激活提交 | `POST /api/user/auth/register/email/activate -> 200 / code=1 / msg=success` | `.../network/activate-email-response.json` |
| 页面结果 | 成功提示可见，最终 URL 仍为 `/activate/email` | `.../activate-email-result.txt`、`.../screenshots/activate-email-result.png` |
| 激活后用户状态 | 返回用户 `status=active`，用户 ID=`12` | `.../network/activate-email-response.json`、`.../userfront-day01-email-activate-real-submit-manual.json` |
| backend 留痕 | backend log 记录 `邮箱激活: tokenLen=32` 与 `邮箱激活完成，用户 ID=12` | `.../backend-email-activate-excerpt.txt` |
| query token 自动激活 | `not-run` | `.../browser-notes.md`、`.../userfront-day01-email-activate-real-submit-manual.json` |

保留观察：

- 浏览器控制台仍有 `404` 资源错误；
- `pageWarnings=[]`；
- `pageErrors=[]`；
- 该观察目前不足以推翻“手动 token POST 已留证”的结论，但也不应被忽略成“全链路无异常”。

---

## 5. 本轮关键证据目录

- `demo-user-ui/docs/frontend-freeze/runtime-artifacts/2026-03-20/userfront-day01-email-activate-real-submit-manual/summary.md`
- `demo-user-ui/docs/frontend-freeze/runtime-artifacts/2026-03-20/userfront-day01-email-activate-real-submit-manual/browser-notes.md`
- `demo-user-ui/docs/frontend-freeze/runtime-artifacts/2026-03-20/userfront-day01-email-activate-real-submit-manual/userfront-day01-email-activate-real-submit-manual.json`
- `demo-user-ui/docs/frontend-freeze/runtime-artifacts/2026-03-20/userfront-day01-email-activate-real-submit-manual/network/email-preview-latest-before-activate.json`
- `demo-user-ui/docs/frontend-freeze/runtime-artifacts/2026-03-20/userfront-day01-email-activate-real-submit-manual/network/activate-email-response.json`
- `demo-user-ui/docs/frontend-freeze/runtime-artifacts/2026-03-20/userfront-day01-email-activate-real-submit-manual/backend-email-activate-excerpt.txt`
- `demo-user-ui/docs/frontend-freeze/runtime-artifacts/2026-03-20/userfront-day01-email-activate-real-submit-manual/activate-email-result.txt`
- `demo-user-ui/docs/frontend-freeze/runtime-artifacts/2026-03-20/userfront-day01-email-activate-real-submit-manual/screenshots/activate-email-page.png`
- `demo-user-ui/docs/frontend-freeze/runtime-artifacts/2026-03-20/userfront-day01-email-activate-real-submit-manual/screenshots/activate-email-result.png`
- `demo-user-ui/docs/frontend-freeze/runtime-artifacts/2026-03-19/userfront-day01-email-register-real-submit-postfix/network/email-preview-latest.json`

---

## 6. 明确区分：哪些说法可以升级，哪些不能写

| 项目 | 现在可以写成 | 绝对不能写成 |
|---|---|---|
| 邮箱激活手动 token POST | `已留证` / `运行态已确认` | `邮箱激活全链路已通过` |
| query token 自动激活 | `not-run` / `待验证` | `已通过` |
| preview helper | `手动 token 来源辅助证据` | `邮件点击链路也已通过` |
| Day01 整体状态 | `进行中` | `Day01 已冻结完成` |
| 站点联调结论 | `Day01 已有多条真实运行态证据` | `登录 / 注册 / 激活全链路已通过`、`整站联调已通过` |

---

## 7. 当前剩余 Day01 缺口

1. 邮箱激活 `query token 自动激活` 仍无直接运行态证据；
2. 首页 seller summary `刷新摘要` 按钮仍只有代码绑定与旧文档口径，尚无单独浏览器留痕；
3. 因此，当前不能把 Day01 诚实地写成“唯一剩余项只剩 query token 自动激活未验证”。

---

## 8. 下一步建议

1. 按你给定的 Day01 范围，下一步优先回 `verify-demo-user-frontend-runtime` 补 `query token 自动激活` 直接留痕；
2. 若 Day01 要按当前覆盖矩阵完全收口，还需同步处理首页 seller summary 手动刷新点击的补证或 scope 调整；
3. 在 query token 自动激活尚未留证前，不要把“手动 token POST 激活成功”升级成“邮箱激活已全部通过”。
