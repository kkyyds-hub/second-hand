# UserFrontDay01 进度回填

- 日期：`2026-03-19`
- 文档版本：`v1.3`
- 当前状态：`进行中（真实 /login -> 首页摘要已留证；真实退出 / 手机注册 / 邮箱注册 / 激活仍未闭环）`
- 本轮范围：`只在 Day01 范围内回填新增 runtime 证据；不修改 demo-user-ui/src/**，不修改 backend controller，不扩到 Day02+，也不把 Day01 写成已冻结完成或整站联调已通过`

---

## 1. 本轮一句话结论

新增 `demo-user-ui/.tmp_runtime/userfront-day01-real-login-probe.json` 与 `demo-user-ui/.tmp_runtime/userfront-day01-real-login-home.{json,html,png,txt}` 后，Day01 已拿到从真实 `/login` 出发的最小正向证据：未登录访问 `/` 会先被守卫带到 `/login?redirect=/`，真实 seller 账号提交后返回 `200 / code=1`，`user_token`、`authentication`、`user_profile` 均已落库，页面回跳 `/`，并自动拉起 seller summary 真实统计。因此本次可以把“登录态壳 / 路由守卫”的运行态表述升级到真实登录起点，把“首页卖家摘要”升级到真实登录后自动加载已留证，把“共享 request / auth 治理”升级到真实登录落库 + `authentication` 请求头 + `401` 清理都已留证；但 `/logout`、手机注册、邮箱注册、邮箱激活仍无新增直接提交证据，Day01 继续进行中。

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

- `demo-user-ui/.tmp_runtime/userfront-day01-real-login-probe.json`
- `demo-user-ui/.tmp_runtime/userfront-day01-real-login-home.json`
- `demo-user-ui/.tmp_runtime/userfront-day01-real-login-home.html`
- `demo-user-ui/.tmp_runtime/userfront-day01-real-login-home.txt`
- `demo-user-ui/.tmp_runtime/userfront-day01-real-login-home.png`
- `demo-user-ui/.tmp_runtime/userfront-day01-real-401-dump.html`（对照）
- `demo-user-ui/.tmp_runtime/userfront-day01-real-summary-dump.html`（对照）
- `demo-user-ui/.tmp_runtime/userfront-day01-non-seller-summary-dump.html`（对照）

### freeze docs

- `demo-user-ui/docs/frontend-freeze/README.md`
- `demo-user-ui/docs/frontend-freeze/00_Business_Coverage_Matrix.md`
- `demo-user-ui/docs/frontend-freeze/UserFrontDay01/README.md`
- `demo-user-ui/docs/frontend-freeze/UserFrontDay01/05_进度回填/UserFrontDay01_Progress_Backfill_v1.2.md`

---

## 3. 本轮新增直接证据

| 证据 | 时间 | 直接说明 |
|---|---|---|
| `demo-user-ui/.tmp_runtime/userfront-day01-real-login-probe.json` | `2026-03-18 22:31:51 +08:00`（文件时间） / `2026-03-18T14:31:51.240740Z`（observedAt） | 直连 `POST /user/auth/login/password` 返回 `HTTP 200`、`code=1`、`hasToken=true`、`isSeller=1`、`status=active`，说明真实 seller 凭证可成功登录 |
| `demo-user-ui/.tmp_runtime/userfront-day01-real-login-home.json` | `2026-03-18 22:37:10 +08:00`（文件时间） / `2026-03-18T14:37:07.939Z`（observedAt） | 浏览器记录 `guardRedirectUrl=http://localhost:5175/login?redirect=/`、`finalUrl=http://localhost:5175/`、`tokenStored.user_token/authentication/user_profile=true`、`loginApi.code=1`、`summaryApi.code=1` |
| `demo-user-ui/.tmp_runtime/userfront-day01-real-login-home.html`、`.txt`、`.png` | `2026-03-18 22:37:09 +08:00`（文件时间） | 留存登录后首页壳、顶部身份、退出入口与 `103 / 71 / 25 / 22` 等卖家摘要卡片实际界面 |

说明：本轮新增证据都指向“真实登录起点 -> 首页摘要自动加载”的最小正向链路；其中不包含 `/logout`、手机注册、邮箱注册、邮箱激活的直接提交流程。

---

## 4. 本轮诚实状态判断

| 条目 | 本次可以升级到什么 | 直接依据 | 本次仍不能写什么 |
|---|---|---|---|
| 登录态壳 / 路由守卫 | `运行态已确认（真实守卫回跳 + 真实登录回跳）` | `userfront-day01-real-login-home.json` 中的 `guardRedirectUrl`、`finalUrl`、`tokenStored` | “真实退出 /logout 也已验证通过” |
| 首页卖家摘要 | `进行中（真实 /login 起点自动加载已留证；手动刷新待补）` | `userfront-day01-real-login-home.json/.txt/.png` + 既有 `userfront-day01-real-summary-dump.html` | “首页摘要加载 / 刷新全链路已通过” |
| 账户中心基础展示 | 保持 `已完成并回填`，本轮不再升级 | 仍沿用 `browser-check-dump.html` 与既有 Day01 文档证据；本轮无新增直接证据 | “资料接口读取 / 编辑已联调通过” |
| 共享 request / auth 治理 | `已完成并回填（真实登录落库 + authentication header + 401 清理已留证）` | `src/utils/request.ts`、`userfront-day01-real-login-home.json`、`userfront-day01-real-401-dump.html` | “/logout 页面动作也已验证通过” |
| 真实退出 /logout | 保持 `代码已确认 + 待运行验证` | 只有 `LogoutPage.vue` 代码与首页可见退出入口，没有点击执行留痕 | “真实退出已联调通过” |
| 手机注册真实提交 | 保持 `代码已确认 + 待运行验证` | 只有页面与提交代码，无新增短信发送 / 注册成功证据 | “手机注册已通过” |
| 邮箱注册 / 激活真实提交 | 保持 `代码已确认 + 待运行验证` | 只有页面与提交代码，无新增注册成功 / 激活成功证据 | “邮箱注册 / 激活已通过” |

---

## 5. 本轮代码 / 文档改动

| 文件 | 改动 | 原因 |
|---|---|---|
| `demo-user-ui/docs/frontend-freeze/UserFrontDay01/05_进度回填/UserFrontDay01_Progress_Backfill_v1.3.md` | 新增第三轮诚实回填 | 把真实 `/login` -> 首页摘要证据与状态判断写清楚 |
| `demo-user-ui/docs/frontend-freeze/00_Business_Coverage_Matrix.md` | 仅更新 Day01 相关行的证据等级与风险 | 同步登录壳 / 路由守卫 / 首页摘要 / request-auth 治理的新证据 |
| `demo-user-ui/docs/frontend-freeze/UserFrontDay01/README.md` | 更新 Day01 总览与阅读顺序 | 指向 `v1.3` 并写清剩余缺口 |
| `demo-user-ui/docs/frontend-freeze/README.md` | 更新 Day01 顶层执行摘要 | 让主入口直接反映最新真实回填结论 |

说明：本轮**没有修改** `demo-user-ui/src/**`，也**没有修改**任何 backend controller。

---

## 6. 构建结果

- 本轮**没有**新跑 `npm.cmd run build`；
- 仍沿用 `2026-03-18` 已记录的 build pass 作为“代码未变更前提下的既有基线”；
- 因为本轮没有新增前端代码改动，所以不能把旧 build 结果写成“本轮新增构建证据”。

---

## 7. 本轮最小 runtime 验证

### 7.1 真实登录 API 直连

| 场景 | 证据 | 观察 |
|---|---|---|
| 真实 seller 凭证提交登录 | `demo-user-ui/.tmp_runtime/userfront-day01-real-login-probe.json` | `POST /user/auth/login/password` 返回 `HTTP 200`、`code=1`、`hasToken=true`、`isSeller=1` |

### 7.2 浏览器从 `/login` 到 `/` 的最小正向链路

| 场景 | 证据 | 观察 |
|---|---|---|
| 未登录访问受保护首页 | `demo-user-ui/.tmp_runtime/userfront-day01-real-login-home.json` | `guardRedirectUrl=http://localhost:5175/login?redirect=/`，说明守卫生效 |
| 真实登录成功后回跳首页 | `demo-user-ui/.tmp_runtime/userfront-day01-real-login-home.json` | `finalUrl=http://localhost:5175/`，说明登录成功后回到了受保护页起点 |
| 登录后 token / profile 落库 | `demo-user-ui/.tmp_runtime/userfront-day01-real-login-home.json` | `user_token`、`authentication`、`user_profile` 均为 `true` |
| 登录后首页摘要自动加载 | `demo-user-ui/.tmp_runtime/userfront-day01-real-login-home.json`、`.txt`、`.png` | `summaryApi.code=1`，并展示 `103` 个商品、`71` 个订单、`25` 个在售商品、`22` 个已完成订单 |

### 7.3 本轮仍未覆盖的 runtime

- `/logout` 点击后的真实清理与跳转留痕；
- 手机注册的真实短信发送与注册成功留痕；
- 邮箱注册成功与邮件激活成功留痕；
- 账户中心基础展示本轮没有新增直接证据，仍沿用上一轮“本地 session 快照展示”结论。

---

## 8. 当前 Day01 还能写成什么 / 不能写成什么

| 项目 | 当前可以写成什么 | 不能写成什么 |
|---|---|---|
| Day01 整体状态 | `进行中（真实登录 -> 首页摘要已留证；退出 / 注册 / 激活待补）` | “Day01 已冻结完成” |
| 登录态壳 / 路由守卫 | `运行态已确认（真实守卫回跳 + 真实登录回跳）` | “登录 / 退出整条链路都已通过” |
| 首页卖家摘要 | `进行中（真实登录起点自动加载已留证；手动刷新待补）` | “首页摘要加载 / 刷新全链路都已通过” |
| 共享 request / auth 治理 | `已完成并回填（Day01 范围内）` | “/logout 也已验证通过” |
| 账户中心基础展示 | `已完成并回填（沿用既有证据）` | “账户资料接口已联调通过” |
| 真实退出 /logout | `待验证` | “已联调通过” |
| 手机注册真实提交 | `待验证` | “已联调通过” |
| 邮箱注册 / 激活真实提交 | `待验证` | “已联调通过” |

---

## 9. blocker owner / reason / 建议的下一步验证顺序

| 顺序 | 验证项 | owner | reason |
|---|---|---|---|
| 1 | 真实退出 `/logout` | `frontend runtime` | 这是当前 Day01 最靠前、最小且直接可补的缺口；登录与摘要已留证后，应先闭合退出动作 |
| 2 | 手机注册真实提交 | `frontend runtime + env/testdata` | 仍缺短信发送与注册成功的直接证据 |
| 3 | 邮箱注册真实提交 | `frontend runtime + env/testdata` | 仍缺真实注册成功与邮件到达证据 |
| 4 | 邮箱激活（手动 token / query token） | `frontend runtime + env/testdata` | 激活依赖前一步产出的真实 token / 邮件内容，宜放在邮箱注册之后 |

说明：以上只是建议顺序，本轮不直接开始执行下一步验证。

---

## 10. 本次备注

1. 本轮回填日期是 `2026-03-19`，但新增 runtime 证据文件生成时间集中在 `2026-03-18 22:31~22:37`；文中已按绝对时间写明，避免把昨晚产物误写成本日执行结果。
2. 本轮没有改 `demo-user-ui/src/**`，没有改 backend controller，也没有触碰 `demo-admin-ui/docs/frontend-freeze/`。
3. 本轮最强新增证据是“真实 `/login` 成功 + 守卫回跳 + token/profile 落库 + 首页摘要自动加载”的组合留痕；这仍然不等于“Day01 已冻结完成”或“整站联调已通过”。