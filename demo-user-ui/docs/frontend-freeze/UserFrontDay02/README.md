# UserFrontDay02 文档总览

- 日期：`2026-03-23`
- 状态：`待最终裁定（Day02 关键子流证据已齐备；本线程仅完成 docs-only 收口评估）`
- 主题：`账户中心补强与地址管理`
- 当日目标：在不打破 Day01 鉴权基线的前提下，推进账户资料、账号安全、联系方式绑定解绑与收货地址管理；当前十个关键切片证据已齐备，Day02 进入“待最终裁定/待收口确认”口径。

---

## 1. 当天一句话结论

`UserFrontDay02` 已在 `2026-03-23` 从 Day01 正式接棒，并在 `2026-04-17` 形成十个已运行回填切片：账户资料编辑 focused regression、地址只读起步、地址新增 create-only、地址编辑 edit-only、地址默认切换 set-default、地址删除 delete-only、修改密码 current-password 路径最小闭环、手机绑定/解绑最小运行态 pass、邮箱绑定/解绑最小运行态 pass、头像上传最小真实闭环 pass（`POST /api/user/me/upload-config` -> `PUT /user/me/avatar/upload` -> `PATCH /api/user/me/profile`，session/avatar 写回确认）。基于 docs-only 收口评估，Day02 当前口径调整为 `已具备收口材料，待最终裁定`。

---

## 2. 为什么 Day02 现在可以接棒

1. Day01 负责的鉴权 / 登录 / 退出、手机注册、邮箱注册与激活、路由与布局、首页卖家摘要、账户中心基础展示，均已在覆盖矩阵中收口为 `已完成并回填`；
2. `AccountCenterPage.vue` 当前仍只读取本地 session 快照，天然就是 Day02 继续补强账户域的现成起点；
3. `UserMeController`、`AddressController` 已提供 Day02 需要承接的账户资料 / 安全 / 地址接口面，因此无需再把这些业务继续挂在 Day01 名下。

---

## 3. Day02 做什么 / 不做什么

| 分类 | 内容 |
|---|---|
| Day02 要做什么 | 继续以 `UserMeController` 与 `AddressController` 为主线推进账户资料、账号安全、绑定解绑与地址管理；当前已完成账户资料编辑 focused regression、地址五个最小运行切片、修改密码最小运行态、手机绑定/解绑最小运行态、邮箱绑定/解绑最小运行态，并补齐头像上传最小真实闭环运行态。 |
| Day02 当前已推进切片 | 已完成十个已运行回填切片：资料编辑（昵称 / 简介）+ `PATCH /user/me/profile` + session 回写 focused regression；地址只读起步；地址新增 create-only；地址编辑 edit-only；地址默认切换 set-default；地址删除 delete-only；修改密码 current-password 路径（`POST /api/user/me/password`，`code=1`，改密后改回闭环）；手机绑定/解绑最小运行态（`/account/security/phone`、`POST/DELETE /api/user/me/bindings/phone`、`localStorage.user_profile.mobile` 写回确认）；邮箱绑定/解绑最小运行态（`/account/security/email`、`POST/DELETE /api/user/me/bindings/email`、`localStorage.user_profile.email` 写回确认）；头像上传最小真实闭环（`POST /api/user/me/upload-config` -> `PUT /user/me/avatar/upload` -> `PATCH /api/user/me/profile`，`Avatar updated.` + session/avatar 写回确认）。 |
| Day02 不做什么 | 不改写 Day01 鉴权基线；不把 Day02 写成“已完成并回填”“已联调通过”或“已冻结完成”；不把地址、安全、绑定等能力继续堆回 `AccountCenterPage.vue`；不越级吸收市场、用户商品、订单、钱包等业务域。 |
| Day02 输出物 | `README`、`01_冻结文档`、`02_接口对齐`、`03_API模块`、`04_联调准备与验收`、`05_进度回填` 六个入口统一到“账户中心补强与地址管理”主题。 |

---

## 4. 推荐阅读顺序

1. `05_进度回填/UserFrontDay02_Progress_Backfill_v2.4.md`
2. `03_API模块/UserFrontDay02_API_Module_Plan_v2.0.md`
3. `04_联调准备与验收/UserFrontDay02_Joint_Debug_Ready_v2.4.md`
4. `01_冻结文档/UserFrontDay02_Scope_Freeze_v1.2.md`
5. `02_接口对齐/UserFrontDay02_Interface_Alignment_v1.3.md`
6. 若要看跨日归属，再回看 `demo-user-ui/docs/frontend-freeze/00_Business_Coverage_Matrix.md`

---

## 5. 当天模块清单

| 模块 | 作用 | 当前状态 |
|---|---|---|
| 冻结文档 | 定义 Day02 范围、非目标、退出口径与最小切片边界 | `v1.2 已完成正式接棒整理；当前总状态以 README + Progress v2.4 的“待最终裁定”口径为准` |
| 接口对齐 | 冻结 `UserMeController`、`AddressController` 的前后端契约入口与优先级 | `v1.3 新增“修改密码”子流 contract 对齐（old/current 双口径兼容）；邮箱绑定/解绑最小运行态已在 v2.2 回填；头像上传子流已在 v2.3 回填为 runtime pass（backend CORS 修复后）` |
| API 模块 | 规划 `profile / address / security` 模块与页面消费边界 | `v2.0 已支撑账户资料编辑 + 地址五个最小运行切片 + 修改密码代码闭环` |
| 联调准备与验收 | 固定资料编辑、安全操作、地址 CRUD 的验证路径与边界 | `v2.4 已补充 docs-only 收口评估结论：关键证据齐备，待最终裁定` |
| 进度回填 | 记录 Day02 正式接棒与后续真实执行回填 | `v2.4 已显式回答收口三问：无新增关键缺口、具备收口材料、仍待最终裁定` |

---

## 6. 覆盖业务域

| 业务域 | 当前前端基线 | Day02 承担 |
|---|---|---|
| 账户资料补强 | `AccountCenterPage.vue` 仍是账户总览 / 入口基线，不承担把 Day02 全量能力继续堆入单页 | 已完成资料编辑（昵称 / 简介）+ session 回写 focused regression；`/account/avatar` 已补齐最小真实闭环 pass（`upload-config -> PUT upload -> PATCH profile`）。 |
| 账号安全与绑定 | Day01 只覆盖登录 / 注册 / 激活 / 退出壳，Day02 已进入安全子流 | 修改密码 current-password 子流已运行态最小闭环；手机绑定/解绑子流已最小运行态 pass；邮箱绑定/解绑子流已最小运行态 pass（`/account/security/email` + `POST/DELETE /api/user/me/bindings/email` + `localStorage.user_profile.email` 写回确认）。 |
| 收货地址 | Day02 已新增地址页面与 API 模块，并完成最小运行切片回填 | 已完成地址列表只读起步、新增 create-only、编辑 edit-only、默认切换 set-default、删除 delete-only；当前仍不能写成地址域已全量完成或真实联调全部通过。 |

---

## 7. 与 Day01 / 后续 Days 的衔接

- `2026-03-23` 起当前执行日已经切换为 `UserFrontDay02`，Day01 不再重开；
- 若 Day02 实现中需要改动 `request / router / authentication / session` 基线，应先在 Day02 进度回填中写明影响，再决定是否补记 Day01；
- Day02 当前已具备十个已运行回填切片证据（含头像上传最小真实闭环 pass）；结论可收敛为“已具备收口材料，待最终裁定”，但仍不能直接升级成“已完成并回填”“已冻结完成”或“整站联调已通过”；
- 用户端工作继续只回填到 `demo-user-ui/docs/frontend-freeze/`，不要写回 `demo-admin-ui/docs/frontend-freeze/`。
