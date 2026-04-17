# UserFrontDay02 联调准备与验收

- 日期：`2026-03-31`
- 文档版本：`v1.7`
- 当前状态：`进行中（地址编辑 edit-only 切片最小运行态已通过；Day02 未完成）`

---

## 1. 本轮验证范围

仅验证 Day02「地址管理-地址编辑（edit-only）切片」最小运行态：

- 路由：`/account/addresses/:addressId/edit`
- 请求：`GET /user/addresses/{id}`、`PUT /user/addresses/{id}`
- 行为：`requiresAuth`、详情回填、必填/手机号校验、提交中禁用、防重复提交、成功回列表、失败分支不持久化、无 create/delete/default 写操作

本轮后端来源为 `mocked-in-browser-route`，属于浏览器可控 mock 运行态验证；不是后端真实联调通过结论。

---

## 2. 验收清单（2026-03-31 实际结果）

| 场景 | 本轮结果 | 关键证据 |
|---|---|---|
| goal1：`/account/addresses/:addressId/edit` 受 requiresAuth 保护 | pass | `demo-user-ui/.tmp_runtime/2026-03-31-userfront-day02-address-edit-only-minimal-runtime/userfront-day02-address-edit-only-minimal-runtime.json`（`flows.goal1_requiresAuth_guard_edit_route.status=pass`）<br>`.../screenshots/goal1-unauth-redirect-login-edit.png` |
| goal2：编辑页 `GET /user/addresses/{id}` 详情并回填 | pass | `.../network/goal2-detail-get-request-attempt1.json`、`.../network/goal2-detail-get-response-attempt1.json`、`.../screenshots/goal2-before-edit-prefill.png` |
| goal3：必填/手机号校验 + 提交中禁用/防重复 | pass | `.../userfront-day02-address-edit-only-minimal-runtime.json`（`flows.goal3_validation_and_submitting_guard.status=pass`）<br>`.../screenshots/goal3-validation-required-mobile.png`、`.../screenshots/goal3-saving-disabled.png` |
| goal4：`PUT /user/addresses/{id}` 成功后回列表并可见更新 | pass | `.../network/goal4-put-update-request-attempt1.json`、`.../network/goal4-put-update-response-attempt1.json`、`.../network/goal4-after-redirect-list-response.json`、`.../screenshots/goal4-after-redirect-list.png` |
| goal5：失败分支提示/恢复/不持久化 | pass | `.../userfront-day02-address-edit-only-minimal-runtime.json`（`flows.goal5_failure_error_restore_no_persist.status=pass`）<br>`.../network/goal5-put-update-response-attempt2.json`、`.../network/goal5-no-persist-list-response.json`、`.../screenshots/goal5-failure-branch.png` |
| goal6：本轮无 create/delete/default 写操作 | pass | `.../network/goal6-address-write-ops-observation.json`、`.../userfront-day02-address-edit-only-minimal-runtime.json`（`flows.goal6_no_create_delete_default_write_ops.status=pass`） |

---

## 3. edit-only 验收口径（补充固化）

| 口径项 | 验收标准 | 本轮结论 |
|---|---|---|
| 守卫 | 未登录访问 `/account/addresses/:addressId/edit` 必须跳转 `/login?redirect=/account/addresses/:addressId/edit` | pass |
| 详情回填 | 打开编辑页必须命中 `GET /user/addresses/{id}`，并把返回值回填到表单字段 | pass |
| 校验与提交态 | 必填/手机号非法时禁止提交；提交中按钮禁用且文案为“提交中...” | pass |
| PUT 成功链路 | 有效输入提交触发 `PUT /user/addresses/{id}` 且 `code=1`，随后跳转 `/account/addresses?edited=1` 并可见更新后内容 | pass |
| 失败分支不持久化 | mock `code=0` 后应展示错误并恢复按钮状态；后续列表响应与页面均不包含失败样本 | pass |
| edit-only 写操作边界 | 本轮不允许触发 `POST /user/addresses`、`DELETE /user/addresses/{id}`、`PUT /user/addresses/{id}/default` | pass |

---

## 4. 构建与运行环境证据

- 构建留证：`demo-user-ui/.tmp_runtime/2026-03-31-userfront-day02-address-edit-only-minimal-runtime/build-verdict.json`、`.../frontend-build.log`
- Dev 运行日志：`.../dev.log`、`.../dev.err.log`
- Dev 运行探针：`.../dev-runtime-probe.json`
- 人工摘要：`.../summary.md`
- 机器摘要：`.../userfront-day02-address-edit-only-minimal-runtime.json`
- 控制台摘要：`.../verify-stdout.json`

---

## 5. Blocker 分类

- 本轮 blocker：`无`
- 结论依据：`.../summary.md`、`.../verify-stdout.json` 与机器摘要（`verdict=pass`、`blocker=null`）。

---

## 6. 下一轮验收入口（仍属 Day02）

1. 进入 Day02 第五最小切片：地址删除或默认地址（二选一）；
2. 或继续 verify 当前 edit-only 切片（重复执行、不同账号样本、长时稳定性）。
