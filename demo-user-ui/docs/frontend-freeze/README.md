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

## 2. 当前推荐执行日（按 2026-04-17 Day02 docs-only 收口评估结论汇总）

- 当前日期：`2026-04-17`
- 当前执行日：`UserFrontDay02`
- 当前执行主题：`账户中心补强与地址管理`
- 当前状态：`待最终裁定（Day01 已完成并回填并退出当前执行日；Day02 已具备十个已运行回填切片证据并完成 docs-only 收口评估，当前进入“可收口/待裁定”阶段）`
- 进入入口：`demo-user-ui/docs/frontend-freeze/UserFrontDay02/README.md`
- 接棒依据：
  1. `UserFrontDay01` 负责的鉴权 / 登录 / 退出、手机注册、邮箱注册与激活、路由与布局、首页卖家摘要、账户中心基础展示，均已在覆盖矩阵中收口为 `已完成并回填`；
  2. `demo-user-ui/docs/frontend-freeze/UserFrontDay01/05_进度回填/UserFrontDay01_Progress_Backfill_v1.10.md` 已正式写明 Day01 退出当前执行日，因此本线程不再回头重开 Day01 验证；
  3. `demo-user-ui/src/pages/AccountCenterPage.vue` 仍停留在本地 session 基础展示，而 `UserMeController`、`AddressController` 已提供 Day02 所需账户资料 / 安全 / 地址接口面，具备正式接棒条件。
- 当前边界：
  - Day02 当前口径可提升为“已具备收口材料，待最终裁定（账户资料切片 + 地址只读切片 + 地址新增 create-only 切片 + 地址编辑 edit-only 切片 + 地址默认切换 set-default 切片 + 地址删除 delete-only 切片 + 修改密码 current-password 切片 + 手机绑定/解绑切片 + 邮箱绑定/解绑切片 + 头像上传切片均已运行通过）”，但仍不能写成“已完成并回填”“已冻结完成”或“整站联调已通过”；
  - 历史 blocked 根因应统一更正为 `environment / wrong-dev-instance-and-backend-not-listening`，不再沿用“功能未打通”口径；
  - 地址编辑/edit-only、地址默认切换/set-default、地址删除/delete-only 本轮结论均属于浏览器可控 mock 运行态验证，不是后端真实联调通过结论；不写“整站联调已通过”。

---

## 3. 当前前端基线（截至 2026-04-17）

| 层级 | 当前结论 | 证据等级 | 主要证据 | 明确边界 |
|---|---|---|---|---|
| Day01 基线 | Day01 最小用户端基建、鉴权壳、登录 / 退出、注册、激活、首页 seller summary、账户中心基础展示均已完成并回填 | `Day01 = 已完成并回填` | `demo-user-ui/docs/frontend-freeze/UserFrontDay01/README.md`、`demo-user-ui/docs/frontend-freeze/UserFrontDay01/05_进度回填/UserFrontDay01_Progress_Backfill_v1.10.md`、`demo-user-ui/docs/frontend-freeze/00_Business_Coverage_Matrix.md` | 不等于整站联调已通过，也不代表 Day02+ 已覆盖 |
| Day02 接棒状态 | Day02 关键子流证据已齐备，当前口径为`已具备收口材料，待最终裁定`：① 账户资料编辑（昵称 / 简介）+ `PATCH /user/me/profile` + `saveCurrentUser()` 回写（同切片 focused regression 通过）；② 地址管理只读起步切片（`/account/addresses`、`GET /user/addresses`、`loading/empty/error/retry`、无写操作）通过；③ 地址新增 create-only 切片（`/account/addresses/new`、表单校验、`POST /user/addresses`、成功跳转、失败分支不持久化）通过；④ 地址编辑 edit-only 切片（`/account/addresses/:id/edit`、详情 GET+回填、校验/提交态、`PUT` 成功回列表、失败分支恢复）通过；⑤ 地址默认切换 set-default 切片（`PUT /user/addresses/{id}/default` + 刷新 GET + 失败恢复）通过；⑥ 地址删除 delete-only 切片（`DELETE /user/addresses/{id}` + 刷新 GET + 失败恢复 + 与 set-default 互斥）通过；⑦ 修改密码 current-password 路径最小闭环通过（真实登录拿 token -> `/account/security/password` -> `POST /api/user/me/password` -> `code=1` -> 改密后改回）；⑧ 手机绑定/解绑最小运行态通过（`/account/security/phone`、`POST/DELETE /api/user/me/bindings/phone`、`localStorage.user_profile.mobile` 写回确认）；⑨ 邮箱绑定/解绑最小运行态通过（`/account/security/email`、`POST/DELETE /api/user/me/bindings/email`、`localStorage.user_profile.email` 写回确认）；⑩ 头像上传最小真实闭环通过（`POST /api/user/me/upload-config` -> `PUT /user/me/avatar/upload` -> `PATCH /api/user/me/profile`，session/avatar 写回确认）。Day02 全量范围仍未完成 | 代码已确认 + 构建已通过 + 运行态已确认（账户资料切片 + 地址只读切片 + 地址新增 create-only 切片 + 地址编辑 edit-only 切片 + 地址默认切换 set-default 切片 + 地址删除 delete-only 切片 + 修改密码 current-password 最小链路 + 手机绑定/解绑最小链路 + 邮箱绑定/解绑最小链路 + 头像上传最小真实闭环；其中 edit-only / set-default / delete-only 为浏览器可控 mock 验证） | `demo-user-ui/docs/frontend-freeze/UserFrontDay02/03_API模块/UserFrontDay02_API_Module_Plan_v2.0.md`、`demo-user-ui/docs/frontend-freeze/UserFrontDay02/04_联调准备与验收/UserFrontDay02_Joint_Debug_Ready_v2.4.md`、`demo-user-ui/docs/frontend-freeze/UserFrontDay02/05_进度回填/UserFrontDay02_Progress_Backfill_v2.4.md`、`demo-user-ui/.tmp_runtime/2026-04-17-userfront-day02-email-avatar-runtime-v3/email-binding/email-minimal-chain-result.json`、`demo-user-ui/.tmp_runtime/2026-04-17-userfront-day02-avatar-runtime-v4/avatar-upload/userfront-day02-avatar-minimal-runtime.json`、`demo-user-ui/src/router/index.ts`、`demo-user-ui/src/pages/AccountAvatarUploadPage.vue`、`demo-user-ui/src/pages/AccountEmailBindingPage.vue`、`demo-user-ui/src/api/profile.ts`、`demo-service/src/main/java/com/demo/config/WebMvcConfiguration.java` | 不得写成 Day02 已完成并回填，也不得写成整站联调已通过 |
| 当前最小起点 | 账户中心仍有 `AccountCenterPage.vue` 本地 session 展示基线，可直接承接 Day02 下一最小切片 | 代码已确认 + 文档已记录 | `demo-user-ui/src/pages/AccountCenterPage.vue`、`demo-user-ui/src/utils/request.ts`、`demo-service/src/main/java/com/demo/controller/user/UserMeController.java`、`demo-service/src/main/java/com/demo/controller/user/AddressController.java` | 只说明 Day02 仍有清晰入口，不等于 Day02 已完成 |

说明：

- Day01 的完成证据仍然来自 `2026-03-19 ~ 2026-03-21` 既有留证，本轮没有重开 Day01 验证；
- Day02 已补齐同切片 focused regression 运行回填（账户资料编辑），并新增地址只读起步切片 + 地址新增 create-only 切片 + 地址编辑 edit-only 切片 + 地址默认切换 set-default 切片 + 地址删除 delete-only 切片最小运行态回填（delete-only goal1~goal6 全 pass）；`2026-04-16` 已补齐修改密码 current-password 路径最小运行态闭环（真实登录 token、页面提交、`POST /api/user/me/password`、`code=1`、改密后改回）；`2026-04-17` 已补齐手机绑定/解绑最小运行态 pass（`/account/security/phone` + `POST/DELETE /api/user/me/bindings/phone` + `localStorage.user_profile.mobile` 写回确认）与邮箱绑定/解绑最小运行态 pass（`/account/security/email` + `POST/DELETE /api/user/me/bindings/email` + `localStorage.user_profile.email` 写回确认）；
- `2026-04-17` 头像上传子流 `/account/avatar` 已在 delivery 修复后取得最小真实闭环 pass：`POST /api/user/me/upload-config`、`PUT /user/me/avatar/upload`、`PATCH /api/user/me/profile` 均命中成功，session/profile 头像写回已确认；
- 地址编辑/edit-only、地址默认切换/set-default、地址删除/delete-only 切片本轮均为浏览器可控 mock 运行态验证，不构成后端真实联调通过结论；
- 若后续实现涉及 Day01 基线变更，必须先在对应执行线程诚实回填影响，再决定是否补记 Day01。

---

## 4. 当前推荐动作

1. Day01 已完成并回填，`2026-03-23` 起不再作为当前执行日；（本轮未重开 Day01）
2. Day02 当前已由“进行中执行态”收敛为 `已具备收口材料，待最终裁定`，且“账户资料编辑最小链路 + 地址只读起步切片 + 地址新增 create-only 切片 + 地址编辑 edit-only 切片 + 地址默认切换 set-default 切片 + 地址删除 delete-only 切片 + 修改密码 current-password 最小链路 + 手机绑定/解绑最小链路 + 邮箱绑定/解绑最小链路 + 头像上传最小真实闭环”均有 `代码已确认 + 构建已通过 + 运行态已确认` 证据；
3. 头像上传历史 `backend/contract-gap` 已通过最小后端修复关闭；Day02 目前仅提升到“待最终裁定/待收口确认”，不替代 accept/gate 最终裁定；
4. 继续保持用户端工作只回填到 `demo-user-ui/docs/frontend-freeze/`，不要写回 `demo-admin-ui/docs/frontend-freeze/`。
5. `UserFrontDay03` 已完成 docs-only 输入准备（`v1.1`），可直接交由后续执行线程接手；但 root 当前执行日仍保持 `UserFrontDay02` 待最终裁定，不做切换。
