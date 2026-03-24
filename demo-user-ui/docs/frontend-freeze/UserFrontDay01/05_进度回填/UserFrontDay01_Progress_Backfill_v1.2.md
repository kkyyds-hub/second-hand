# UserFrontDay01 进度回填

- 日期：`2026-03-18`
- 文档版本：`v1.2`
- 当前状态：`进行中（已查清 seller summary 500 / auth header / real backend 401 真相；Day01 仍未冻结完成）`
- 本轮范围：`只处理 Day01 剩余真实联调阻塞，不扩到 Day02+，也不扩大成整站联调`

---

## 1. 本轮一句话结论

本轮已确认：`demo-user-ui` 的 request 层与 backend 当前真实协议一致，用户端应继续发送 `authentication` 请求头；真实 backend 下，无头 / `Authorization` / 无效 `authentication` 都会返回 `401`；当 `authentication` 携带有效 seller JWT 时，`GET /user/seller/summary` 能返回真实统计数据；当 token 对应非 seller 时，会返回业务拒绝“仅卖家可执行该操作”，不是 controller 稳定 500。先前 Day01 最小链路里看到的 `500` 本轮未在“backend 已运行 + 正确 header + 有效 seller 会话”条件下复现，因此当前 seller summary 的真实剩余阻塞，不再是简单的 backend 500，而是**缺少从真实登录页成功拿到 seller 会话并直接进入首页的运行证据**。

---

## 2. 本轮检查了哪些文件

### 前端

- `demo-user-ui/src/pages/HomePage.vue`
- `demo-user-ui/src/api/seller.ts`
- `demo-user-ui/src/api/auth.ts`
- `demo-user-ui/src/utils/request.ts`
- `demo-user-ui/src/router/index.ts`
- `demo-user-ui/vite.config.ts`
- `demo-user-ui/.tmp_runtime/userfront-day01-401-check.html`
- `demo-user-ui/.tmp_runtime/userfront-day01-real-summary-check.html`
- `demo-user-ui/.tmp_runtime/userfront-day01-non-seller-summary-check.html`

### 后端

- `demo-service/src/main/java/com/demo/interceptor/JwtTokenUserInterceptor.java`
- `demo-service/src/main/java/com/demo/controller/user/SellerController.java`
- `demo-service/src/main/java/com/demo/service/serviceimpl/SellerServiceImpl.java`
- `demo-service/src/main/java/com/demo/service/serviceimpl/UserServiceImpl.java`
- `demo-service/src/main/resources/application.yml`

### freeze docs

- `demo-user-ui/docs/frontend-freeze/README.md`
- `demo-user-ui/docs/frontend-freeze/00_Business_Coverage_Matrix.md`
- `demo-user-ui/docs/frontend-freeze/UserFrontDay01/README.md`
- `demo-user-ui/docs/frontend-freeze/UserFrontDay01/02_接口对齐/UserFrontDay01_Interface_Alignment_v1.0.md`
- `demo-user-ui/docs/frontend-freeze/UserFrontDay01/03_API模块/UserFrontDay01_API_Module_Plan_v1.0.md`
- `demo-user-ui/docs/frontend-freeze/UserFrontDay01/04_联调准备与验收/UserFrontDay01_Joint_Debug_Ready_v1.0.md`
- `demo-user-ui/docs/frontend-freeze/UserFrontDay01/05_进度回填/UserFrontDay01_Progress_Backfill_v1.1.md`

---

## 3. 本轮真实归因

| 问题 | 归因 | 说明 |
|---|---|---|
| 先前 seller summary `500` 现象 | `mixed` | 本轮在 backend 正常运行、真实 `authentication` 请求头和有效 seller JWT 条件下没有复现；因此旧 `500` 更接近环境 / 运行态夹层或无效会话下的历史观察，而不是当前 controller 固有 500 |
| 真实 backend auth/header 真相 | `contract + backend` | `JwtTokenUserInterceptor` 只认 `authentication`，`Authorization` 不生效 |
| 真实 backend 401 真相 | `backend` | 缺头、无效 token、只传 `Authorization` 都返回 HTTP `401` |
| 首页失败时顶部仍显示“已加载” | `frontend` | `HomePage.vue` 原先只看 `loading / hasLoadedOnce`，首轮失败后文案会误导联调判断；本轮已修正 |
| Day01 仍未闭环 | `mixed` | 缺少从真实 `/login` 提交拿到 seller 会话、再直接进入首页的浏览器证据 |

---

## 4. 本轮代码 / 文档改动

| 文件 | 改动 | 原因 |
|---|---|---|
| `demo-user-ui/src/pages/HomePage.vue` | 新增 `summaryStatusText`，在 seller summary 失败时优先展示失败态文案 | 避免接口失败后仍显示“卖家摘要已加载”，影响 Day01 阻塞判断 |
| `demo-user-ui/docs/frontend-freeze/UserFrontDay01/02_接口对齐/UserFrontDay01_Interface_Alignment_v1.1.md` | 新增真实 auth/header/401 与 seller summary 对齐结论 | 把 contract / backend 真相写清楚 |
| `demo-user-ui/docs/frontend-freeze/UserFrontDay01/03_API模块/UserFrontDay01_API_Module_Plan_v1.1.md` | 回填 request / seller / HomePage 的真实职责边界 | 让代码边界与联调事实一致 |
| `demo-user-ui/docs/frontend-freeze/UserFrontDay01/04_联调准备与验收/UserFrontDay01_Joint_Debug_Ready_v1.1.md` | 补最小验收结果与新 runtime 证据 | 区分 pass / not-run |
| `demo-user-ui/docs/frontend-freeze/UserFrontDay01/05_进度回填/UserFrontDay01_Progress_Backfill_v1.2.md` | 新增本轮 Day01 真实回填 | 保持 Day01 进行中口径但更新 blocker 真相 |
| `demo-user-ui/docs/frontend-freeze/UserFrontDay01/README.md` | 更新 Day01 总览与阅读顺序 | 指向最新 v1.1 / v1.2 台账 |
| `demo-user-ui/docs/frontend-freeze/README.md` | 更新 Day01 顶层执行摘要 | 把 seller summary / auth truth 同步到主入口 |
| `demo-user-ui/docs/frontend-freeze/00_Business_Coverage_Matrix.md` | 更新首页卖家摘要、共享 request/auth、鉴权与登录行 | 让矩阵和 Day01 当前事实一致 |

说明：本轮**没有修改** `demo-service` 下任何 backend controller。

---

## 5. 构建结果

- 命令：`npm.cmd run build`
- 时间：`2026-03-18`
- 结果：`pass`

---

## 6. 本轮最小 runtime 验证

### 6.1 直连接口真相

直接请求 `GET /user/seller/summary`：

| 调用方式 | 结果 |
|---|---|
| 无鉴权头 | HTTP `401` |
| `Authorization: Bearer <valid>` | HTTP `401` |
| `authentication: demo-token` | HTTP `401` |
| `authentication: <valid seller JWT>` | HTTP `200` + `code=1` + seller summary 数据 |
| `authentication: <valid non-seller JWT>` | HTTP `200` + `code=0` + “仅卖家可执行该操作” |

### 6.2 浏览器最小 runtime 观察

| 场景 | 证据 | 观察 |
|---|---|---|
| 有效 seller JWT + 首页摘要 | `demo-user-ui/.tmp_runtime/userfront-day01-real-summary-dump.html` | 首页壳正常，摘要无错误态，出现 `103` 个商品、`71` 个订单等真实统计 |
| 无效 token + 真实 backend 401 | `demo-user-ui/.tmp_runtime/userfront-day01-real-401-dump.html` | 页面回跳 `/login?redirect=%2F`，且 `user_token` / `authentication` / `user_profile` 全部被清空 |
| 有效 non-seller JWT + 首页摘要 | `demo-user-ui/.tmp_runtime/userfront-day01-non-seller-summary-dump.html` | 页面停留在首页，出现“仅卖家可执行该操作”，顶部状态文案显示“卖家摘要加载失败，请检查当前登录态或接口状态后重试。” |

### 6.3 证据边界

本轮 runtime 证据中的有效 JWT 用于**诊断 seller summary / auth/header/401 真相**，不等于“真实登录页提交流程已验证通过”。

---

## 7. 当前 Day01 还能写成什么 / 不能写成什么

| 项目 | 当前只能写成什么 | 不能写成什么 |
|---|---|---|
| seller summary | `进行中（真实 backend 下 valid seller token 可达；真实登录起点待补证）` | “首页摘要全链路联调已通过” |
| real backend 401 | `已确认` | “只有 mock 401” |
| auth header | `authentication 已确认` | “Authorization 也可直接替代” |
| Day01 整体状态 | `进行中` | “Day01 已冻结完成” |
| 登录 / 注册 / 激活 / 退出 | `待验证` | “已联调通过” |

---

## 8. blocker owner / reason / 下一步建议

| blocker | owner | reason | 下一步建议 |
|---|---|---|---|
| 缺少“真实登录成功 -> 进入首页 -> 自动拉 seller summary”证据 | `mixed（frontend runtime + env/testdata）` | 本轮 seller summary 成功依赖诊断用 seller JWT，本地尚未形成稳定的真实 seller 登录凭证或可复用测试账号证据 | 下一线程继续留在 Day01，优先补 seller 测试账号/凭证来源，再从 `/login` 出发做最小浏览器提交流程 |
| 登录 / 注册 / 激活 / 退出仍未形成真实提交记录 | `frontend runtime` | 当前只有页面可达、guard、real 401、diagnostic summary 证据，没有完整提交留痕 | 继续 Day01 最小 runtime 验证，不要跳 Day02+ |
| seller summary 历史 `500` 结论仍留在旧文档 / 旧产物中 | `frontend freeze docs` | 旧 artifact 捕获的是过时现象，已经不足以代表本轮真实结论 | 以后引用 Day01 结论时改看 `v1.2` 和新的 real-runtime 产物 |

---

## 9. 建议如何继续回填 Day01

- 继续把 Day01 写成“已完成第二轮诚实回填，但仍进行中”；
- seller summary 行不再写纯 `500` 阻塞，而改写成“真实 backend header/401/seller summary 真相已查清，真实登录起点待补证”；
- 若下一线程继续做 runtime，只补 Day01 的真实登录 / 退出 / 注册 / 激活提交证据，不要借机扩到 Day02+；
- 只有在真实登录起点和首页摘要链路都留证后，才讨论是否把 seller summary 从 `进行中` 升级。

---

## 10. 本次备注

1. `v1.1` 保留首轮诚实回填；本文件 `v1.2` 记录 seller summary / auth/header/401 真相查清后的第二轮回填。
2. 本轮没有改 `demo-service` backend controller，也没有修改 `demo-admin-ui/docs/frontend-freeze/`。
3. 当前最强新增证据是“真实 backend 401 + valid seller token 摘要成功 + non-seller 业务拒绝”的运行态证据，不等于真实登录页全链路成功。
