# 用户端前端冻结文档主入口

> 启用日期：`2026-03-18`
> 适用项目：`demo-user-ui`
> 当前唯一主入口：`demo-user-ui/docs/frontend-freeze/README.md`

这套文档统一管理 `demo-user-ui` 的用户端计划、冻结、联调、回填与移交。

`demo-admin-ui` 已在 `2026-03-16` 完成 `FrontDay10` 收口；用户端必须使用自己的 `UserFrontDay` 体系推进，不能把用户端工作继续写回管理端冻结文档。

---

## 1. 这套体系解决什么问题

1. 解决用户端计划、范围、证据散落的问题；
2. 解决“已有主工程，但没有唯一推进入口”的问题；
3. 解决“先做哪个业务域、做到哪一层算完成”的问题；
4. 解决联调、freeze、handoff 缺少统一口径的问题。

---

## 2. 当前推荐执行日（按 2026-03-23 口径）

- 当前日期：`2026-03-23`
- 当前执行日：`UserFrontDay02`
- 当前执行主题：`账户中心补强与地址管理`
- 当前状态：`已正式接棒（Day01 已完成并回填并退出当前执行日；Day02 当前仅完成文档接棒整理与首个最小切片冻结，尚未进入实现 / 联调）`
- 进入入口：`demo-user-ui/docs/frontend-freeze/UserFrontDay02/README.md`
- 接棒依据：
  1. `UserFrontDay01` 负责的鉴权 / 登录 / 退出、手机注册、邮箱注册与激活、路由与布局、首页卖家摘要、账户中心基础展示，均已在覆盖矩阵中收口为 `已完成并回填`；
  2. `demo-user-ui/docs/frontend-freeze/UserFrontDay01/05_进度回填/UserFrontDay01_Progress_Backfill_v1.10.md` 已正式写明 Day01 退出当前执行日，因此本线程不再回头重开 Day01 验证；
  3. `demo-user-ui/src/pages/AccountCenterPage.vue` 仍停留在本地 session 基础展示，而 `UserMeController`、`AddressController` 已提供 Day02 所需账户资料 / 安全 / 地址接口面，具备正式接棒条件。
- 当前边界：
  - Day02 当前只能写成“已正式接棒”，不能写成“已实现”“已联调通过”或“已冻结完成”；
  - 本轮只做 Day01 -> Day02 的书面接棒整理，不改 `demo-user-ui/src/**`，不改 backend controller；
  - 不写“整站联调已通过”。

---

## 3. 当前前端基线（截至 2026-03-23）

| 层级 | 当前结论 | 证据等级 | 主要证据 | 明确边界 |
|---|---|---|---|---|
| Day01 基线 | Day01 最小用户端基建、鉴权壳、登录 / 退出、注册、激活、首页 seller summary、账户中心基础展示均已完成并回填 | `Day01 = 已完成并回填` | `demo-user-ui/docs/frontend-freeze/UserFrontDay01/README.md`、`demo-user-ui/docs/frontend-freeze/UserFrontDay01/05_进度回填/UserFrontDay01_Progress_Backfill_v1.10.md`、`demo-user-ui/docs/frontend-freeze/00_Business_Coverage_Matrix.md` | 不等于整站联调已通过，也不代表 Day02+ 已覆盖 |
| Day02 接棒状态 | Day02 已切为当前执行日，并冻结首个最小切片为“账户资料编辑（昵称 / 简介） + `PATCH /user/me/profile` + session 回写” | 文档已记录 | `demo-user-ui/docs/frontend-freeze/UserFrontDay02/README.md`、`demo-user-ui/docs/frontend-freeze/UserFrontDay02/01_冻结文档/UserFrontDay02_Scope_Freeze_v1.1.md`、`demo-user-ui/docs/frontend-freeze/UserFrontDay02/03_API模块/UserFrontDay02_API_Module_Plan_v1.1.md`、`demo-user-ui/docs/frontend-freeze/UserFrontDay02/05_进度回填/UserFrontDay02_Progress_Backfill_v1.1.md` | 不得写成已实现、已构建通过、已联调通过或已冻结完成 |
| 当前最小起点 | 账户中心仍有 `AccountCenterPage.vue` 本地 session 展示基线，可直接承接资料编辑首切片 | 代码已确认 + 文档已记录 | `demo-user-ui/src/pages/AccountCenterPage.vue`、`demo-user-ui/src/utils/request.ts`、`demo-service/src/main/java/com/demo/controller/user/UserMeController.java`、`demo-pojo/src/main/java/com/demo/dto/user/UpdateProfileRequest.java` | 只说明 Day02 第一刀有清晰入口，不等于功能已完成 |

说明：

- Day01 的完成证据仍然来自 `2026-03-19 ~ 2026-03-21` 既有留证，本轮没有重开 Day01 验证；
- Day02 当前只完成文档层接棒整理，尚未新增前端代码、构建或联调证据；
- 若后续实现涉及 Day01 基线变更，必须先在对应执行线程诚实回填影响，再决定是否补记 Day01。

---

## 4. 当前推荐动作

1. Day01 已完成并回填，`2026-03-23` 起不再作为当前执行日；
2. Day02 已正式接棒，但当前证据等级仍只有 `文档已记录`；
3. 下一条线程应从 docs 线程切换到实现线程，优先推进 `AccountCenterPage` 的资料编辑（昵称 / 简介）+ `PATCH /user/me/profile` + `saveCurrentUser()` 回写；
4. 继续保持用户端工作只回填到 `demo-user-ui/docs/frontend-freeze/`，不要写回 `demo-admin-ui/docs/frontend-freeze/`。