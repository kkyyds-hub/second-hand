# UserFrontDay02 API 模块规划

- 日期：`2026-03-30`
- 文档版本：`v1.4`
- 当前状态：`进行中（同切片 focused regression 已通过；Day02 未完成）`

---

## 1. 模块目标

在 Day02 主题不变（账户中心补强与地址管理）的前提下，保持首个最小切片稳定：
`AccountCenter 昵称/简介编辑 + PATCH /user/me/profile + saveCurrentUser() 回写`。

本版新增的是同切片回归后的“分支验证口径”回填，不扩展新业务域。

---

## 2. 已落地 API 结论（延续 v1.3，并补 2026-03-30 回归证据）

| 模块 / 文件 | 当前结论 | 证据 |
|---|---|---|
| `demo-user-ui/src/api/profile.ts` | Day02 第一刀继续稳定，`updateMyProfile` 承接 `PATCH /user/me/profile`。 | `demo-user-ui/src/api/profile.ts`、`demo-user-ui/.tmp_runtime/2026-03-30-userfront-day02-account-profile-edit-focused-regression/network/case1_nickname_only-patch-request.json`、`.../case1_nickname_only-patch-response.json` |
| `demo-user-ui/src/pages/AccountCenterPage.vue` | 提交态、成功态、失败态与会话回写流程在同切片回归中保持稳定。 | `demo-user-ui/src/pages/AccountCenterPage.vue`、`.../screenshots/case1_nickname_only-success.png`、`.../screenshots/case5_forced_failure_branch-error.png` |
| `demo-user-ui/src/utils/request.ts` | Day01 `authentication` 与会话工具继续可复用，未引入新口径。 | `demo-user-ui/src/utils/request.ts`、`.../network/case3_nickname_bio_both-patch-request.json` |

---

## 3. 本轮新增：分支验证口径

| 分支 | API/页面协同口径 | 2026-03-30 结果 |
|---|---|---|
| 无变更提交分支 | 不应发送 `PATCH /user/me/profile`；提交按钮保持禁用并给出可解释提示。 | pass（`flows.case4_no_change_submit.patchReqCount=0`） |
| 强制失败分支 | 仅一次提交请求；错误提示出现后按钮/输入恢复；失败值不写回本地会话快照。 | pass（`flows.case5_forced_failure_branch.checks.*=true`） |

---

## 4. 仍保持计划态的 API 子流

| 模块 / 文件 | 当前状态 | 说明 |
|---|---|---|
| `demo-user-ui/src/api/profile.ts` 的头像上传相关能力 | 计划中 | `upload-config -> avatar/upload` 两步链路尚未进入本轮。 |
| 账号安全与绑定 API（密码修改 / 手机绑定解绑 / 邮箱绑定解绑） | 计划中 | 不在本轮最小链路范围内。 |
| `demo-user-ui/src/api/address.ts` 与地址页面消费 | 计划中 | 地址管理尚未进入本轮实现与验证。 |

---

## 5. 边界声明

1. 本文档只确认同切片回归口径与证据，不代表 Day02 全量完成；
2. 不得写成“Day02 已完成并回填”或“整站联调已通过”；
3. Day02 后续仍按 `UserFrontDay02/05_进度回填` 的增量证据继续升级。
