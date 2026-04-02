# UserFrontDay02 联调准备与验收

- 日期：`2026-03-30`
- 文档版本：`v1.6`
- 当前状态：`进行中（地址新增 create-only 切片最小运行态已通过；Day02 未完成）`

---

## 1. 本轮验证范围

仅验证 Day02「地址管理-地址新增（create-only）切片」最小运行态：

- 路由：`/account/addresses/new`
- 请求：`POST /user/addresses`
- 行为：`requiresAuth`、表单必填与手机号格式校验、提交中禁用、防重复提交、成功跳转回列表、失败分支不持久化

不扩到地址编辑/删除/设默认，不扩到账号安全，不扩到 Day03+。

---

## 2. 验收清单（2026-03-30 实际结果）

| 场景 | 本轮结果 | 关键证据 |
|---|---|---|
| goal1：`/account/addresses/new` 受 requiresAuth 保护 | pass | `demo-user-ui/.tmp_runtime/2026-03-30-userfront-day02-address-create-only-minimal-runtime/userfront-day02-address-create-only-minimal-runtime.json`（`flows.goal1_requiresAuth_guard_new_route.status=pass`）<br>`.../screenshots/goal1-unauth-redirect-login-new.png` |
| goal2：表单必填与手机号格式校验 | pass | `.../userfront-day02-address-create-only-minimal-runtime.json`（`flows.goal2_required_and_mobile_validation.status=pass`）<br>`.../screenshots/goal2-validation-required-field.png`、`.../goal2-validation-mobile-format.png` |
| goal3：有效提交触发 `POST /user/addresses` 且成功 | pass | `.../network/goal3-success-post-request-attempt1.json`、`.../network/goal3-success-post-response-attempt1.json` |
| goal4：提交中禁用 + 防重复提交 | pass | `.../userfront-day02-address-create-only-minimal-runtime.json`（`flows.goal4_submitting_disable_and_duplicate_prevent.status=pass`）<br>`.../screenshots/goal4-saving-disabled.png` |
| goal5：成功后跳转地址列表并确认新增可见 | pass | `.../userfront-day02-address-create-only-minimal-runtime.json`（`flows.goal5_success_redirect_and_created_visible.status=pass`）<br>`.../network/goal5-after-redirect-get-addresses-response.json`、`.../screenshots/goal5-after-redirect-list.png` |
| goal6：失败 mock 分支提示/恢复/不持久化 | pass | `.../userfront-day02-address-create-only-minimal-runtime.json`（`flows.goal6_failure_mock_error_restore_no_persist.status=pass`）<br>`.../network/goal6-failure-post-response.json`、`.../network/goal6-no-persist-get-response.json`、`.../screenshots/goal6-failure-branch.png` |

---

## 3. create-only 验收口径（固化）

| 口径项 | 验收标准 | 本轮结论 |
|---|---|---|
| 守卫 | 未登录访问 `/account/addresses/new` 必须跳转 `/login?redirect=/account/addresses/new` | pass |
| 校验 | 必填字段缺失时禁止提交；手机号格式非法时显示错误并禁止提交 | pass |
| POST | 有效输入提交必须触发 `POST /user/addresses`，响应 `code=1` 视为成功链路通过 | pass |
| 跳转 | 成功后跳转 `/account/addresses?created=1` 并能在列表响应/页面中看到新增收货人 | pass |
| 失败分支不持久化 | mock `code=0` 错误后，按钮文本与状态恢复；后续 `GET /user/addresses` 不包含失败样本 | pass |

---

## 4. 构建与运行环境证据

- 构建留证：`demo-user-ui/.tmp_runtime/2026-03-30-userfront-day02-address-create-only-minimal-runtime/build-verdict.json`、`.../frontend-build.log`
- Dev 运行日志：`.../dev.log`、`.../dev.err.log`
- Dev 运行探针：`.../dev-runtime-probe.json`
- 人工摘要：`.../summary.md`
- 机器摘要：`.../userfront-day02-address-create-only-minimal-runtime.json`

---

## 5. Blocker 分类

- 本轮 blocker：`无`
- 结论依据：`.../summary.md` 与机器摘要（`blocker=null`）。

---

## 6. 下一轮验收入口（仍属 Day02）

1. 推荐进入 Day02 第四最小切片（地址编辑）；
2. 或继续 verify 当前 create-only 切片（重复执行、不同账号数据分布、长时稳定性）。
