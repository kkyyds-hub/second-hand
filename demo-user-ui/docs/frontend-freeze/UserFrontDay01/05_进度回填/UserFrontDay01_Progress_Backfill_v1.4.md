# UserFrontDay01 进度回填

- 日期：`2026-03-19`
- 文档版本：`v1.4`
- 当前状态：`进行中（真实 /login -> 首页摘要与真实 /logout 路由均已留证；手机注册 / 邮箱注册 / 邮箱激活仍未闭环）`
- 本轮范围：`只在 Day01 范围内回填新增 /logout runtime 证据，并沿用上一轮已留存的真实 /login -> 首页摘要证据；不修改 demo-user-ui/src/**，不修改 backend controller，不扩到 Day02+，也不把 Day01 写成已冻结完成或整站联调已通过`

---

## 1. 本轮一句话结论

在 `UserFrontDay01_Progress_Backfill_v1.3.md` 已写清真实 `/login` -> 首页摘要最小正向链路的基础上，本轮新增 `demo-user-ui/.tmp_runtime/userfront-day01-real-logout-route.{json,html,png,txt}`（文件时间 `2026-03-19 10:22:02 +08:00`，`observedAt=2026-03-19T02:22:02.309Z`）后，可以把 Day01 的“真实退出 `/logout`”从“代码已确认 + 待运行验证”升级为“运行态已确认（前端 `/logout` 路由清理本地 session、回跳 `/login`，且退出后访问 `/account` 会再次被守卫拦截）”；但这仍不等于后端独立 logout API 已联调通过，更不等于 Day01 已冻结完成。Day01 当前剩余未闭环项仍然只有手机注册真实提交、邮箱注册真实提交、邮箱激活真实提交。

---

## 2. 本轮检查了哪些文件

### 前端代码（仅复核，不改动）

- `demo-user-ui/src/router/index.ts`
- `demo-user-ui/src/utils/request.ts`
- `demo-user-ui/src/api/auth.ts`
- `demo-user-ui/src/pages/LoginPage.vue`
- `demo-user-ui/src/pages/LogoutPage.vue`
- `demo-user-ui/src/pages/RegisterPhonePage.vue`
- `demo-user-ui/src/pages/RegisterEmailPage.vue`
- `demo-user-ui/src/pages/EmailActivatePage.vue`
- `demo-user-ui/src/pages/HomePage.vue`
- `demo-user-ui/src/pages/AccountCenterPage.vue`

### 运行态证据（本轮新增重点）

- `demo-user-ui/.tmp_runtime/userfront-day01-real-login-probe.json`（沿用）
- `demo-user-ui/.tmp_runtime/userfront-day01-real-login-home.json`（沿用）
- `demo-user-ui/.tmp_runtime/userfront-day01-real-login-home.html`（沿用）
- `demo-user-ui/.tmp_runtime/userfront-day01-real-login-home.txt`（沿用）
- `demo-user-ui/.tmp_runtime/userfront-day01-real-login-home.png`（沿用）
- `demo-user-ui/.tmp_runtime/userfront-day01-real-logout-route.json`（本轮新增）
- `demo-user-ui/.tmp_runtime/userfront-day01-real-logout-route.html`（本轮新增）
- `demo-user-ui/.tmp_runtime/userfront-day01-real-logout-route.txt`（本轮新增）
- `demo-user-ui/.tmp_runtime/userfront-day01-real-logout-route.png`（本轮新增）
- `demo-user-ui/.tmp_runtime/userfront-day01-real-401-dump.html`（对照）
- `demo-user-ui/.tmp_runtime/userfront-day01-real-summary-dump.html`（对照）
- `demo-user-ui/.tmp_runtime/userfront-day01-non-seller-summary-dump.html`（对照）

### freeze docs

- `demo-user-ui/docs/frontend-freeze/README.md`
- `demo-user-ui/docs/frontend-freeze/00_Business_Coverage_Matrix.md`
- `demo-user-ui/docs/frontend-freeze/UserFrontDay01/README.md`
- `demo-user-ui/docs/frontend-freeze/UserFrontDay01/05_进度回填/UserFrontDay01_Progress_Backfill_v1.3.md`

---

## 3. 本轮新增直接证据

| 证据 | 时间 | 直接说明 |
|---|---|---|
| `demo-user-ui/.tmp_runtime/userfront-day01-real-logout-route.json` | `2026-03-19 10:22:02 +08:00`（文件时间） / `2026-03-19T02:22:02.309Z`（observedAt） | 浏览器记录 `beforeLogoutUrl=http://localhost:5175/`、`requestedLogoutUrl=http://localhost:5175/logout`、`afterLogoutUrl=http://localhost:5175/login`，且 `beforeLogoutStorage.user_token/authentication/user_profile=true`、`afterLogoutStorage.*=false`、`protectedInterceptUrl=http://localhost:5175/login?redirect=/account`，`verdict=pass` |
| `demo-user-ui/.tmp_runtime/userfront-day01-real-logout-route.txt`、`.html`、`.png` | `2026-03-19 10:22:02 +08:00`（文件时间） | 留存退出后回到登录页的实际页面文本 / DOM / 截图，可对照“登录到用户端工作台”“手机注册”“邮箱注册”“邮箱激活”等登录页文案 |

说明：

- 这批新证据验证的是**前端 `/logout` 路由最小运行链路**，即“已登录 -> `/logout` -> 清 session -> 回到 `/login` -> 再访受保护页继续被守卫拦截”；
- 它**不**证明存在后端独立 logout API，也**不**能外推为“注册 / 激活都已通过”；
- `userfront-day01-real-logout-route.json` 里记录了 `2` 条 `404` consoleErrors，但 `pageErrors=[]`，本轮仅把它如实记为运行观察，不把它写成阻塞 Day01 的新 blocker。

---

## 4. 本轮诚实状态判断

| 条目 | 本次可以升级到什么 | 直接依据 | 本次仍不能写什么 |
|---|---|---|---|
| 登录态壳 / 路由守卫 | `运行态已确认（真实守卫回跳 + 真实登录回跳 + 退出后受保护页再拦截）` | `userfront-day01-real-login-home.json` 与 `userfront-day01-real-logout-route.json` 中的 `guardRedirectUrl`、`finalUrl`、`protectedInterceptUrl` | “登录 / 退出之外的注册 / 激活也已通过” |
| 真实退出 /logout | `运行态已确认（前端 /logout 路由清 session + 回跳 /login）` | `userfront-day01-real-logout-route.json/.txt/.html/.png` 中的 `requestedLogoutUrl`、`afterLogoutUrl`、`afterLogoutStorage`、登录页文本留痕 | “后端 logout API 已联调通过” |
| 首页卖家摘要 | `进行中（真实 /login 起点自动加载已留证；本轮不扩写为手动刷新点击已验证）` | `userfront-day01-real-login-home.json/.txt/.png` + 既有 `userfront-day01-real-summary-dump.html` | “首页摘要加载 / 刷新全链路已通过” |
| 账户中心基础展示 | 保持 `已完成并回填`，本轮不再升级 | 仍沿用 `browser-check-dump.html` 与既有 Day01 文档证据；本轮无新增直接证据 | “资料接口读取 / 编辑已联调通过” |
| 共享 request / auth 治理 | `已完成并回填（真实登录落库 + 真实 /logout 清 session + authentication header + 401 清理已留证）` | `src/utils/request.ts`、`userfront-day01-real-login-home.json`、`userfront-day01-real-logout-route.json`、`userfront-day01-real-401-dump.html` | “所有 auth 相关页面都已联调通过” |
| 手机注册真实提交 | 保持 `代码已确认 + 待运行验证` | 只有页面与提交代码，无新增短信发送 / 注册成功证据 | “手机注册已通过” |
| 邮箱注册 / 激活真实提交 | 保持 `代码已确认 + 待运行验证` | 只有页面与提交代码，无新增注册成功 / 激活成功证据 | “邮箱注册 / 激活已通过” |

---

## 5. 本轮代码 / 文档改动

| 文件 | 改动 | 原因 |
|---|---|---|
| `demo-user-ui/docs/frontend-freeze/UserFrontDay01/05_进度回填/UserFrontDay01_Progress_Backfill_v1.4.md` | 新增第四轮诚实回填 | 把真实 `/logout` 运行态证据、状态升级边界与剩余缺口写清楚 |
| `demo-user-ui/docs/frontend-freeze/00_Business_Coverage_Matrix.md` | 更新 Day01 鉴权与 request/auth 相关行 | 同步 `/logout` route 运行态证据，移除“真实退出仍待验证”的旧口径 |
| `demo-user-ui/docs/frontend-freeze/UserFrontDay01/README.md` | 更新 Day01 总览、阅读顺序与当前证据分层 | 指向 `v1.4`，并把 Day01 剩余未闭环项收敛到注册 / 激活三项 |
| `demo-user-ui/docs/frontend-freeze/README.md` | 更新 Day01 顶层执行摘要 | 让主入口直接反映“真实 `/logout` 路由已留证”的最新结论 |

说明：本轮**没有修改** `demo-user-ui/src/**`，也**没有修改**任何 backend controller。

---

## 6. 构建结果

- 本轮**没有**新跑 `npm.cmd run build`；
- 仍沿用 `2026-03-18` 已记录的 build pass 作为“代码未变更前提下的既有基线”；
- 因为本轮没有新增前端代码改动，所以不能把旧 build 结果写成“本轮新增构建证据”。

---

## 7. 本轮最小 runtime 验证

### 7.1 真实登录 API 直连（沿用上一轮）

| 场景 | 证据 | 观察 |
|---|---|---|
| 真实 seller 凭证提交登录 | `demo-user-ui/.tmp_runtime/userfront-day01-real-login-probe.json` | `POST /user/auth/login/password` 返回 `HTTP 200`、`code=1`、`hasToken=true`、`isSeller=1` |

### 7.2 浏览器从 `/login` 到 `/` 的最小正向链路（沿用上一轮）

| 场景 | 证据 | 观察 |
|---|---|---|
| 未登录访问受保护首页 | `demo-user-ui/.tmp_runtime/userfront-day01-real-login-home.json` | `guardRedirectUrl=http://localhost:5175/login?redirect=/`，说明守卫生效 |
| 真实登录成功后回跳首页 | `demo-user-ui/.tmp_runtime/userfront-day01-real-login-home.json` | `finalUrl=http://localhost:5175/`，说明登录成功后回到了受保护页起点 |
| 登录后 token / profile 落库 | `demo-user-ui/.tmp_runtime/userfront-day01-real-login-home.json` | `user_token`、`authentication`、`user_profile` 均为 `true` |
| 登录后首页摘要自动加载 | `demo-user-ui/.tmp_runtime/userfront-day01-real-login-home.json`、`.txt`、`.png` | `summaryApi.code=1`，并展示 `103` 个商品、`71` 个订单、`25` 个在售商品、`22` 个已完成订单 |

### 7.3 浏览器从 `/logout` 回到 `/login` 的最小退出链路（本轮新增）

| 场景 | 证据 | 观察 |
|---|---|---|
| 已登录首页触发 `/logout` | `demo-user-ui/.tmp_runtime/userfront-day01-real-logout-route.json` | `beforeLogoutUrl=http://localhost:5175/`、`requestedLogoutUrl=http://localhost:5175/logout`、`afterLogoutUrl=http://localhost:5175/login` |
| 退出时清空本地 session | `demo-user-ui/.tmp_runtime/userfront-day01-real-logout-route.json` | `beforeLogoutStorage.user_token/authentication/user_profile=true`，`afterLogoutStorage.*=false` |
| 退出后再次访问受保护页 | `demo-user-ui/.tmp_runtime/userfront-day01-real-logout-route.json` | `protectedTarget=http://localhost:5175/account`，`protectedInterceptUrl=http://localhost:5175/login?redirect=/account`，且 `protectedAfterStorage.*=false` |
| 最终页面回到登录页 | `demo-user-ui/.tmp_runtime/userfront-day01-real-logout-route.txt`、`.html`、`.png` | 页面文本回到“登录到用户端工作台”，并重新显示“手机注册 / 邮箱注册 / 邮箱激活”入口 |
| 控制台 / 页面异常观察 | `demo-user-ui/.tmp_runtime/userfront-day01-real-logout-route.json` | `consoleErrors` 为两条 `404`，`pageErrors=[]`；本轮仅据此确认 `/logout` 最小链路 `pass`，不扩写其他结论 |

### 7.4 本轮仍未覆盖的 runtime

- 手机注册的真实短信发送与注册成功留痕；
- 邮箱注册成功与邮件到达留痕；
- 邮箱激活（手动 token / query token）成功留痕。

---

## 8. 当前 Day01 还能写成什么 / 不能写成什么

| 项目 | 当前可以写成什么 | 不能写成什么 |
|---|---|---|
| Day01 整体状态 | `进行中（真实 /login -> 首页摘要与真实 /logout 路由均已留证；手机注册 / 邮箱注册 / 邮箱激活待补）` | “Day01 已冻结完成” |
| 登录态壳 / 路由守卫 | `运行态已确认（真实守卫回跳 + 真实登录回跳 + 退出后受保护页再拦截）` | “登录 / 注册 / 激活 / 退出都已通过” |
| 真实退出 /logout | `运行态已确认（前端 route 清 session + 回跳 login + 退出后受保护页再拦截）` | “后端 logout API 已联调通过” |
| 首页卖家摘要 | `进行中（真实 /login 起点自动加载已留证；本轮不扩写手动刷新已验证）` | “首页摘要加载 / 刷新全链路都已通过” |
| 共享 request / auth 治理 | `已完成并回填（Day01 范围内）` | “所有鉴权相关页面都已联调通过” |
| 账户中心基础展示 | `已完成并回填（沿用既有证据）` | “账户资料接口已联调通过” |
| 手机注册真实提交 | `待验证` | “已联调通过” |
| 邮箱注册真实提交 | `待验证` | “已联调通过” |
| 邮箱激活真实提交 | `待验证` | “已联调通过” |

---

## 9. blocker owner / reason / 建议的下一步验证顺序

| 顺序 | 验证项 | owner | reason |
|---|---|---|---|
| 1 | 手机注册真实提交 | `frontend runtime + env/testdata` | 这是 `/logout` 闭合后的下一个最小业务缺口，仍缺短信发送与注册成功的直接证据 |
| 2 | 邮箱注册真实提交 | `frontend runtime + env/testdata` | 仍缺真实注册成功与邮件到达证据 |
| 3 | 邮箱激活（手动 token / query token） | `frontend runtime + env/testdata` | 激活依赖前一步产出的真实 token / 邮件内容，宜放在邮箱注册之后 |

说明：以上只是建议顺序，本轮不直接开始执行下一步验证。

---

## 10. 本次备注

1. 本轮回填日期是 `2026-03-19`；新增 `/logout` runtime 证据文件生成时间为 `2026-03-19 10:22:02 +08:00`，`observedAt` 为 `2026-03-19T02:22:02.309Z`，文中已按绝对时间写明。
2. 本轮如实记录了 `userfront-day01-real-logout-route.json` 中的两条 `404` consoleErrors；由于 `pageErrors=[]` 且退出链路判定为 `pass`，本次只把它记作运行观察，不把它升级成新的 blocker 或额外完成项。
3. 本轮没有改 `demo-user-ui/src/**`，没有改 backend controller，也没有触碰 `demo-admin-ui/docs/frontend-freeze/`。
4. 本轮最强新增证据是“真实 `/logout` 路由清 session + 回跳 `/login` + 退出后受保护页再拦截”的组合留痕；这仍然不等于“Day01 已冻结完成”或“整站联调已通过”。
