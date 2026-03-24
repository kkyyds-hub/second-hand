# UserFrontDay01 进度回填

- 日期：`2026-03-18`
- 文档版本：`v1.1`
- 当前状态：`进行中（已完成首轮诚实回填；代码 / 构建 / dev / 浏览器最小链路已分层，真实登录 / 注册 / 激活与首页摘要联调仍未通过）`
- 最新回填日期：`2026-03-18`
- 回填依据：代码检查 + `npm.cmd run build` + `Invoke-WebRequest http://localhost:5175/login -UseBasicParsing` + `demo-user-ui/.tmp_runtime/` 临时验证产物
- 明确边界：未改 backend controller；401 回跳验证依赖临时 mock 服务 `demo-user-ui/.tmp_runtime/mock-401-server.cjs`，不等于真实后端联调

---

## 1. 当前判定

- 总结：`demo-user-ui` 已具备独立主工程与最小鉴权壳代码面，Day01 本轮新增的是“诚实回填”而不是“扩实现”。代码、构建、本地 dev、浏览器最小链路已经可以分层描述，但真实登录 / 注册 / 激活提交与首页摘要成功链路仍未被证明。
- Day01 当前应写成：`进行中`
- 证据等级：`代码已确认 + 构建已通过 + 运行态已确认（本地 dev / 浏览器最小链路） + 待验证（真实 auth 提交 / real backend 401 / 首页摘要成功）`
- 当日 handoff：下一线程若继续推进用户端，应优先补真实运行证据或定位首页摘要 `500`，而不是直接跳到地址 / 订单等后续业务域。

---

## 2. 已回填完成项（按证据层级）

| 层级 | 判定 | 证据路径 / 命令 | 回填说明 |
|---|---|---|---|
| 文档治理入口 | 文档已记录 | `demo-user-ui/docs/frontend-freeze/README.md`<br>`demo-user-ui/docs/frontend-freeze/00_Business_Coverage_Matrix.md`<br>`demo-user-ui/docs/frontend-freeze/UserFrontDay01/README.md`<br>`demo-user-ui/docs/frontend-freeze/UserFrontDay01/05_进度回填/UserFrontDay01_Progress_Backfill_v1.0.md`<br>`demo-user-ui/docs/frontend-freeze/UserFrontDay01/05_进度回填/UserFrontDay01_Progress_Backfill_v1.1.md` | 用户端已有自己的唯一 freeze 主入口；本次在保留 `v1.0` 初始骨架的前提下，新增 `v1.1` 记录首轮诚实回填。 |
| Day01 代码面 | 代码已确认 | `demo-user-ui/package.json`<br>`demo-user-ui/vite.config.ts`<br>`demo-user-ui/src/router/index.ts`<br>`demo-user-ui/src/layouts/UserLayout.vue`<br>`demo-user-ui/src/utils/request.ts`<br>`demo-user-ui/src/api/auth.ts`<br>`demo-user-ui/src/api/seller.ts`<br>`demo-user-ui/src/pages/LoginPage.vue`<br>`demo-user-ui/src/pages/RegisterPhonePage.vue`<br>`demo-user-ui/src/pages/RegisterEmailPage.vue`<br>`demo-user-ui/src/pages/EmailActivatePage.vue`<br>`demo-user-ui/src/pages/HomePage.vue`<br>`demo-user-ui/src/pages/AccountCenterPage.vue`<br>`demo-user-ui/src/pages/LogoutPage.vue` | 当前最成熟的能力已经被完整纳入 Day01 口径；本次没有扩实现，也没有改 backend controller。 |
| 构建层 | 构建已通过 | 命令 `npm.cmd run build`（`2026-03-18`，exit code `0`）<br>`demo-user-ui/package.json` | 当前主工程可完成 TypeScript 检查与生产构建；这只证明 build 通过，不代表运行联调通过。 |
| 本地 dev 层 | 运行态已确认 | 命令 `Invoke-WebRequest http://localhost:5175/login -UseBasicParsing`（`2026-03-18`）返回 `STATUS=200`，响应含 `/@vite/client`<br>`demo-user-ui/package.json` | 已能确认本地 dev 页面可访问；但当前没有把 dev 可访问直接升级成“业务已通过”。 |
| 浏览器最小链路层 | 运行态已确认 | `demo-user-ui/.tmp_runtime/userfront-day01-browser-check.html`<br>`demo-user-ui/.tmp_runtime/browser-check-dump.html`<br>`demo-user-ui/.tmp_runtime/userfront-day01-401-check.html`<br>`demo-user-ui/.tmp_runtime/401-check-dump.html`<br>`demo-user-ui/.tmp_runtime/mock-401-server.cjs`<br>`demo-user-ui/.tmp_runtime/mock-401.out.log` | 当前已证实 same-origin localStorage、路由守卫、guestOnly 回跳、账户中心本地 session 快照与 mock 401 清 session / 回登录页最小链路；但这仍不等于真实登录 / 注册 / 激活 / 首页摘要成功链路。 |

---

## 3. 已证实事实（可直接回填）

| 事实 | 当前判定 | 主要证据 | 结论边界 |
|---|---|---|---|
| `/login`、`/register/phone`、`/register/email`、`/activate/email` 页面可达 | 运行态已确认 | `demo-user-ui/.tmp_runtime/browser-check-dump.html` 中 `login_route`、`register_phone_route`、`register_email_route`、`activate_email_route` 结果均为 `ok: true` | 只证明页面与基础文案可达，不证明提交成功 |
| 未登录访问 `/account` 会带 `redirect` 参数跳回登录页 | 运行态已确认 | `demo-user-ui/.tmp_runtime/browser-check-dump.html` 中 `requiresAuth_redirect_account`，结果路径为 `/login?redirect=/account` | 只证明路由守卫可工作，不证明真实登录后一定成功回跳 |
| same-origin localStorage 会话能驱动 guestOnly 与登录后壳 | 运行态已确认 | `demo-user-ui/.tmp_runtime/browser-check-dump.html` 中 `guestOnly_redirect_login_with_auth`、`home_shell_with_auth`、`account_shell_with_auth` | 会话来源是手工写入 localStorage 的临时验证，不是真实登录接口返回 |
| 账户中心基础展示可读取本地 session 快照 | 运行态已确认 | `demo-user-ui/src/pages/AccountCenterPage.vue`（只读本地 session 设计）<br>`demo-user-ui/.tmp_runtime/browser-check-dump.html` 中 `account_shell_with_auth` | 只证明 Day01 的“基础展示”成立，不等于资料编辑 / 刷新接口已完成 |
| 前端 401 清 session / 回登录页逻辑可工作 | 运行态已确认 | `demo-user-ui/.tmp_runtime/userfront-day01-401-check.html`<br>`demo-user-ui/.tmp_runtime/401-check-dump.html` 中 `pathname=/login?redirect=%2F`、`tokenAfter=null`、`authAfter=null`、`profileAfter=null`<br>`demo-user-ui/.tmp_runtime/mock-401-server.cjs` | 这是临时 mock 401 验证，不是对真实 backend controller 的联调结论 |

---

## 4. 只能保守描述的事实

| 项目 | 只能写成什么 | 明确不能写成什么 | 依据 |
|---|---|---|---|
| 密码登录提交 | `待验证` | “登录已通过” / “登录联调已通过” | 当前只有 `src/pages/LoginPage.vue`、`src/api/auth.ts` 代码面与登录页可达证据，没有真实提交成功记录 |
| 手机注册（短信发送 / 注册） | `待验证` | “手机注册已通过” | `demo-user-ui/.tmp_runtime/browser-check-dump.html` 只证明注册页可达，没有短信与提交结果 |
| 邮箱注册 / 预览 / 激活 | `待验证` | “邮箱注册 / 激活全链路已通过” | 当前只有页面可达与代码面；没有真实邮件、token、激活成功证据 |
| 退出登录 | `待验证` | “退出登录已验证通过” | `src/pages/LogoutPage.vue` 与 `UserLayout.vue` 有代码，但本轮未保留单独的退出动作运行证据 |
| 首页摘要接口 | `阻塞 / 待联调` | “首页摘要接口已验证通过” | 浏览器最小链路已出现 `500`，当前只能写阻塞 |
| Day01 整体状态 | `进行中` | “Day01 已冻结完成” | 尚有真实 auth 提交未验证，且首页摘要被阻塞 |

---

## 5. 阻塞 / 待联调 / 下一技能

| 项目 | 当前判定 | 证据 | 建议下一步 |
|---|---|---|---|
| `GET /user/seller/summary` 当前本地最小链路出现 `500` | 阻塞 | `demo-user-ui/.tmp_runtime/browser-check-dump.html` 中 `home_shell_with_auth.hasErrorState = true`<br>补充直接请求侧证：命令 `Invoke-WebRequest http://localhost:5175/api/user/seller/summary -Headers @{ authentication = 'demo-token' } -UseBasicParsing` 返回 `STATUS=500` | 若要继续定位根因或核对真实 backend 行为，应切到 `drive-demo-user-ui-delivery`；本线程只做 honest backfill |
| 真实登录 / 手机注册 / 邮箱注册 / 邮箱激活 / 退出运行证据 | 待验证 | 当前仅有代码面、build、页面可达与 localStorage 驱动的最小壳证据，没有真实提交链路留痕 | 若下一步只补浏览器执行证据，应切到 `verify-demo-user-frontend-runtime` |
| real backend 401 行为 | 待联调 | 当前 401 证据来自 `demo-user-ui/.tmp_runtime/mock-401-server.cjs` 与 `401-check-dump.html` | 若要把 mock 401 升级为真实接口行为验证，应切到 `drive-demo-user-ui-delivery` |

---

## 6. 当日手工回填区（后续继续使用）

- 实际开始时间：`2026-03-18`
- 实际完成时间：`2026-03-18`
- 构建结果：`2026-03-18` 执行 `npm.cmd run build`，结果 `pass`
- dev 结果：`2026-03-18` 执行 `Invoke-WebRequest http://localhost:5175/login -UseBasicParsing`，结果 `STATUS=200`
- 浏览器最小链路结果：`2026-03-18` 已保留 `demo-user-ui/.tmp_runtime/browser-check-dump.html` 与 `demo-user-ui/.tmp_runtime/401-check-dump.html`
- 遗留问题：真实登录 / 注册 / 激活 / 退出未形成执行记录；首页摘要当前 `500`
- 明日 / 下一线程计划：先决定是补运行证据（`verify-demo-user-frontend-runtime`）还是跨边界定位 `500`（`drive-demo-user-ui-delivery`）

---

## 7. 本次回填备注

1. `v1.0` 保留 Day01 初始骨架结论，本文件 `v1.1` 负责记录首轮诚实回填，不把未验证事实升级成“联调已通过”。
2. 当前最强运行证据来自本地 dev 与浏览器最小链路；这只能证明前端壳、路由守卫、same-origin localStorage 会话与 mock 401 清 session 逻辑，不可外推为真实业务链路成功。
3. 本次没有改 `demo-service` 下任何 backend controller；涉及 401 的补充验证只新增了 `demo-user-ui/.tmp_runtime/` 下的临时验证产物。
4. 若后续运行结果或阻塞结论发生明显变化，请继续升级版本号，而不是直接覆盖本文件判断。
