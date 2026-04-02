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

## 2. 当前推荐执行日（按 2026-03-30 同切片 focused regression + 地址只读 + 地址新增 create-only 最小运行态回填口径）

- 当前日期：`2026-03-30`
- 当前执行日：`UserFrontDay02`
- 当前执行主题：`账户中心补强与地址管理`
- 当前状态：`进行中（Day01 已完成并回填并退出当前执行日；Day02 已具备三个已运行回填切片：账户资料编辑同切片 focused regression 通过 + 地址管理只读起步切片最小运行态通过 + 地址新增 create-only 最小运行态通过）`
- 进入入口：`demo-user-ui/docs/frontend-freeze/UserFrontDay02/README.md`
- 接棒依据：
  1. `UserFrontDay01` 负责的鉴权 / 登录 / 退出、手机注册、邮箱注册与激活、路由与布局、首页卖家摘要、账户中心基础展示，均已在覆盖矩阵中收口为 `已完成并回填`；
  2. `demo-user-ui/docs/frontend-freeze/UserFrontDay01/05_进度回填/UserFrontDay01_Progress_Backfill_v1.10.md` 已正式写明 Day01 退出当前执行日，因此本线程不再回头重开 Day01 验证；
  3. `demo-user-ui/src/pages/AccountCenterPage.vue` 仍停留在本地 session 基础展示，而 `UserMeController`、`AddressController` 已提供 Day02 所需账户资料 / 安全 / 地址接口面，具备正式接棒条件。
- 当前边界：
  - Day02 当前可升级为“进行中（账户资料切片 + 地址只读切片 + 地址新增 create-only 切片均已运行通过）”，但仍不能写成“已完成并回填”“已冻结完成”或“整站联调已通过”；
  - 本轮为 docs 诚实回填线程：依据 2026-03-30 既有运行证据更新 Day02 文档，不在本线程改 `demo-user-ui/src/**`、不改 backend controller；
  - 不写“整站联调已通过”。

---

## 3. 当前前端基线（截至 2026-03-30）

| 层级 | 当前结论 | 证据等级 | 主要证据 | 明确边界 |
|---|---|---|---|---|
| Day01 基线 | Day01 最小用户端基建、鉴权壳、登录 / 退出、注册、激活、首页 seller summary、账户中心基础展示均已完成并回填 | `Day01 = 已完成并回填` | `demo-user-ui/docs/frontend-freeze/UserFrontDay01/README.md`、`demo-user-ui/docs/frontend-freeze/UserFrontDay01/05_进度回填/UserFrontDay01_Progress_Backfill_v1.10.md`、`demo-user-ui/docs/frontend-freeze/00_Business_Coverage_Matrix.md` | 不等于整站联调已通过，也不代表 Day02+ 已覆盖 |
| Day02 接棒状态 | Day02 已进入`进行中`：已完成三个已运行回填切片——① 账户资料编辑（昵称 / 简介）+ `PATCH /user/me/profile` + `saveCurrentUser()` 回写（同切片 focused regression 通过）；② 地址管理只读起步切片（`/account/addresses`、`GET /user/addresses`、`loading/empty/error/retry`、无写操作）通过；③ 地址新增 create-only 切片（`/account/addresses/new`、表单校验、`POST /user/addresses`、成功跳转、失败分支不持久化）通过；Day02 全量范围仍未完成 | 代码已确认 + 构建已通过 + 运行态已确认（账户资料切片 + 地址只读切片 + 地址新增 create-only 切片） | `demo-user-ui/docs/frontend-freeze/UserFrontDay02/03_API模块/UserFrontDay02_API_Module_Plan_v1.6.md`、`demo-user-ui/docs/frontend-freeze/UserFrontDay02/04_联调准备与验收/UserFrontDay02_Joint_Debug_Ready_v1.6.md`、`demo-user-ui/docs/frontend-freeze/UserFrontDay02/05_进度回填/UserFrontDay02_Progress_Backfill_v1.6.md`、`demo-user-ui/.tmp_runtime/2026-03-30-userfront-day02-account-profile-edit-focused-regression/summary.md`、`demo-user-ui/.tmp_runtime/2026-03-30-userfront-day02-address-readonly-minimal-runtime/summary.md`、`demo-user-ui/.tmp_runtime/2026-03-30-userfront-day02-address-create-only-minimal-runtime/summary.md` | 不得写成 Day02 已完成并回填，也不得写成整站联调已通过 |
| 当前最小起点 | 账户中心仍有 `AccountCenterPage.vue` 本地 session 展示基线，可直接承接 Day02 下一最小切片 | 代码已确认 + 文档已记录 | `demo-user-ui/src/pages/AccountCenterPage.vue`、`demo-user-ui/src/utils/request.ts`、`demo-service/src/main/java/com/demo/controller/user/UserMeController.java`、`demo-service/src/main/java/com/demo/controller/user/AddressController.java` | 只说明 Day02 仍有清晰入口，不等于 Day02 已完成 |

说明：

- Day01 的完成证据仍然来自 `2026-03-19 ~ 2026-03-21` 既有留证，本轮没有重开 Day01 验证；
- Day02 已补齐同切片 focused regression 运行回填（账户资料编辑），并新增地址只读起步切片 + 地址新增 create-only 切片最小运行态回填（两轮均 goal1~goal6 全 pass），但仍未覆盖地址编辑/删除/默认地址与 Day02 全量范围；
- 若后续实现涉及 Day01 基线变更，必须先在对应执行线程诚实回填影响，再决定是否补记 Day01。

---

## 4. 当前推荐动作

1. Day01 已完成并回填，`2026-03-23` 起不再作为当前执行日；（本轮未重开 Day01）
2. Day02 当前处于 `进行中`，且“账户资料编辑最小链路 + 地址只读起步切片 + 地址新增 create-only 切片”已具备 `代码已确认 + 构建已通过 + 运行态已确认` 证据；
3. 下一条线程可进入 Day02 第四个最小切片（建议地址编辑）；若需先提置信心，也可继续 verify 当前已回填切片（只读 + create-only）；
4. 继续保持用户端工作只回填到 `demo-user-ui/docs/frontend-freeze/`，不要写回 `demo-admin-ui/docs/frontend-freeze/`。
