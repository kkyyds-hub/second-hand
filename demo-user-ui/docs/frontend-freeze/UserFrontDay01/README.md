# UserFrontDay01 文档总览

- 日期：`2026-03-23`
- 状态：`已完成并回填（首页 seller summary 手动刷新点击最小运行态证据已补齐；2026-03-23 已正式退出当前执行日并接棒给 UserFrontDay02）`
- 主题：`独立主工程基建与鉴权壳冻结`
- 当日目标：把用户端独立主工程、构建入口、`request / router / UserLayout`、登录 / 注册 / 激活 / 退出、首页 / 账户中心基础壳，统一成一套可继续推进的冻结口径，并把“代码已确认 / 构建已通过 / 运行态已确认 / 已完成并回填”的层次写清楚。

---

## 1. 当天一句话结论

`demo-user-ui` 的 Day01 已在既有 `2026-03-19~2026-03-21` 登录 / 注册 / 退出 / 激活 / 首页 seller summary 证据基础上完成正式收口，并于 `2026-03-23` 退出当前执行日，把后续账户资料 / 安全 / 地址工作移交给 Day02；但该结论只覆盖 Day01 的最小用户端基建与鉴权壳，不等于整站联调已通过。

---

## 2. 为什么 Day01 现在可以收口并退出当前执行日

1. `demo-admin-ui` 已在 `2026-03-16` 完成 `FrontDay10` 收口，用户端工作必须继续留在 `demo-user-ui` 自己的 `UserFrontDay` 体系内推进；
2. `2026-03-21` 已存在首页 seller summary 手动刷新点击最小运行态证据，补齐了 Day01 最后一项待验证尾项；
3. 按当前覆盖矩阵，Day01 负责的鉴权、登录 / 退出、手机注册、邮箱注册与激活、首页卖家摘要、账户中心基础展示均已完成并回填；
4. 因此 Day01 现在既可以保持正式收口，也可以在 `2026-03-23` 正式退出当前执行日，把当前入口切换给 Day02，而无需回头重开 Day01 验证。

---

## 3. Day01 做什么 / 不做什么

| 分类 | 内容 |
|---|---|
| Day01 要做什么 | 冻结独立主工程边界、冻结 request / router / layout / auth shell 口径、记录构建基线、记录最小运行态事实、把真实 `/login` 起点 / seller summary 自动加载 + 手动刷新 / `/logout` 路由 / 手机注册真实提交 / 邮箱注册真实提交 / 邮箱激活手动 token POST / 邮箱激活 query token 自动激活证据回填进 freeze、建立覆盖矩阵。 |
| Day01 不做什么 | 不直接开启地址 / 收藏 / 订单 / 钱包等新业务页面实现；不把局部 runtime 结论升级成整站联调通过；不把用户端工作写回 `demo-admin-ui` 文档。 |
| Day01 输出物 | `README`、`01_冻结文档`、`02_接口对齐`、`03_API模块`、`04_联调准备与验收`、`05_进度回填` 六类入口仍聚焦“独立主工程基建与鉴权壳冻结”。 |

---

## 4. 推荐阅读顺序

1. `01_冻结文档/UserFrontDay01_Scope_Freeze_v1.0.md`
2. `02_接口对齐/UserFrontDay01_Interface_Alignment_v1.4.md`
3. `03_API模块/UserFrontDay01_API_Module_Plan_v1.2.md`
4. `04_联调准备与验收/UserFrontDay01_Joint_Debug_Ready_v1.6.md`
5. `05_进度回填/UserFrontDay01_Progress_Backfill_v1.10.md`
6. 若要看跨日覆盖归属，再回看 `demo-user-ui/docs/frontend-freeze/00_Business_Coverage_Matrix.md`

---

## 5. 当天模块清单

| 模块 | 作用 | 当前状态 |
|---|---|---|
| 冻结文档 | 定义 Day01 做什么、不做什么、退出标准 | `v1.0 已创建` |
| 接口对齐 | 冻结 auth / seller summary / token header / 401 / preview helper / 邮箱注册 compatibility / 邮箱激活链接目标口径 | `v1.4 已完成既有收口口径保留` |
| API 模块 | 固定 `auth.ts`、`seller.ts`、`request.ts`、`router/index.ts`、`HomePage.vue`、`EmailActivatePage.vue` 的职责边界 | `v1.2 已完成既有收口口径保留` |
| 联调准备与验收 | 固定 build、header、401、seller summary、登录 / 注册 / 激活 / 退出的最小验收边界 | `v1.6 已保留既有 Day01 最小验收结果` |
| 进度回填 | 记录本轮检查、证据分层、runtime 回填、剩余缺口与下一步建议 | `v1.10 已补 Day01 退出当前执行日与 Day02 正式接棒说明` |

---

## 6. 当前结论边界（截至 2026-03-23）

| 层级 | 当前结论 | 主要证据 | 不应升级成 |
|---|---|---|---|
| 代码状态 | Day01 代码面已确认 | `demo-user-ui/src/router/index.ts`、`demo-user-ui/src/utils/request.ts`、`demo-user-ui/src/api/auth.ts`、`demo-user-ui/src/api/seller.ts`、`demo-user-ui/src/pages/*.vue` | “业务联调已通过” |
| 构建状态 | `2026-03-20` build 通过证据已被 `2026-03-21` 首页 seller summary 手动刷新补证复用 | `UserFrontDay01_Progress_Backfill_v1.9.md`、`runtime-artifacts/2026-03-20/userfront-day01-email-activate-query-auto-fix-minimal/frontend-build.log` | “Day02+ 已开始执行” |
| 运行态状态 | 登录 / 退出、手机注册、邮箱注册与激活、首页 seller summary 自动加载 + 手动刷新点击均已完成最小留证 | `UserFrontDay01_Progress_Backfill_v1.9.md`、`runtime-artifacts/2026-03-19/`、`runtime-artifacts/2026-03-20/`、`runtime-artifacts/2026-03-21/` | “整站联调已通过” |
| Day01 执行口径 | 已完成并回填，且已正式退出当前执行日 | `UserFrontDay01_Progress_Backfill_v1.10.md`、`README.md`、`../README.md` | “Day02 已实现” |

---

## 7. 下一步衔接

- `2026-03-23` 已完成 Day01 正式退出当前执行日的书面交接，当前入口切换至 `UserFrontDay02`；
- 若后续只是推进账户资料 / 安全 / 地址，不需要回头重开 Day01 验证；
- 仅当 `request / router / authentication / session` 等 Day01 基线被实际改动时，才在对应执行线程先回填影响，再决定是否补记 Day01；
- 继续保持用户端工作只回填到 `demo-user-ui/docs/frontend-freeze/`，不要写回 `demo-admin-ui/docs/frontend-freeze/`。