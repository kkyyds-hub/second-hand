# UserFrontDay02 进度回填

- 日期：`2026-03-30`
- 文档版本：`v1.6`
- 当前状态：`进行中（账户资料切片 + 地址只读起步切片 + 地址新增 create-only 切片均已运行回填；Day02 未完成）`

---

## 1. 当前判定

- 总结：在 `v1.5` 已回填“地址只读起步切片”基础上，`2026-03-30` 已新增“地址新增（create-only）最小运行态”回填，`goal1~goal6` 全部 pass。
- 状态判定：`进行中`（可升级为“地址新增 create-only 切片运行通过”，不能升级为“Day02 已完成并回填”或“整站联调已通过”）。
- blocker：`无`。

---

## 2. 已回填完成项（本轮新增）

| 项目 | 判定 | 证据路径 | 回填说明 |
|---|---|---|---|
| Day02 地址新增 create-only 最小运行态执行完成 | 运行态已确认 | `demo-user-ui/.tmp_runtime/2026-03-30-userfront-day02-address-create-only-minimal-runtime/summary.md`、`demo-user-ui/.tmp_runtime/2026-03-30-userfront-day02-address-create-only-minimal-runtime/userfront-day02-address-create-only-minimal-runtime.json` | 本轮只覆盖地址新增 create-only，不扩写操作全域，不扩 Day03+。 |
| 路由守卫（`/account/addresses/new`） | 运行态已确认 | `.../screenshots/goal1-unauth-redirect-login-new.png`、`.../userfront-day02-address-create-only-minimal-runtime.json` | 未登录拦截到登录页并携带 redirect 可复现。 |
| 表单校验（必填 + 手机号格式） | 运行态已确认 | `.../screenshots/goal2-validation-required-field.png`、`.../goal2-validation-mobile-format.png`、`.../userfront-day02-address-create-only-minimal-runtime.json` | 非法输入被拦截，提交按钮保持禁用。 |
| 有效提交触发 `POST /user/addresses` 成功 | 运行态已确认 | `.../network/goal3-success-post-request-attempt1.json`、`.../network/goal3-success-post-response-attempt1.json` | 成功响应 `code=1`，返回新增地址对象。 |
| 提交中禁用 + 防重复提交 | 运行态已确认 | `.../screenshots/goal4-saving-disabled.png`、`.../userfront-day02-address-create-only-minimal-runtime.json` | 提交中按钮文案为“提交中...”，且未出现重复 POST。 |
| 成功后跳转回列表并可见新增 | 运行态已确认 | `.../network/goal5-after-redirect-get-addresses-request.json`、`.../network/goal5-after-redirect-get-addresses-response.json`、`.../screenshots/goal5-after-redirect-list.png` | 成功后跳转 `/account/addresses?created=1`，新增收货人可见。 |
| 失败分支：提示/恢复/不持久化 | 运行态已确认 | `.../network/goal6-failure-post-request.json`、`.../network/goal6-failure-post-response.json`、`.../network/goal6-no-persist-get-response.json`、`.../screenshots/goal6-failure-branch.png` | mock 失败后按钮状态恢复，且失败样本未写入列表。 |
| 本轮构建与 dev 留证 | 构建已通过 + 运行环境可用 | `.../build-verdict.json`、`.../frontend-build.log`、`.../dev.log`、`.../dev-runtime-probe.json` | `npm.cmd run build` 与 `npm run dev` 留证完成。 |

---

## 3. flow 结论（2026-03-30 地址新增 create-only 最小运行态）

| flow | verdict | 说明 |
|---|---|---|
| goal1：`/account/addresses/new` requiresAuth 守卫 | pass | 未登录被拦截至 `/login?redirect=/account/addresses/new`。 |
| goal2：表单必填与手机号格式校验 | pass | 非法输入被阻止提交，错误提示可见。 |
| goal3：`POST /user/addresses` 成功链路 | pass | 成功请求与响应均留证。 |
| goal4：提交中禁用 + 防重复提交 | pass | 提交中状态可见，且 POST 命中数保持单次。 |
| goal5：成功跳转与新增可见 | pass | 跳转回列表后可见新增记录。 |
| goal6：失败 mock 提示/恢复/不持久化 | pass | 失败提示可见；恢复后列表无失败样本。 |

---

## 4. 仍待推进项（Day02 未完成部分）

| 项目 | 当前判定 | 说明 |
|---|---|---|
| 地址编辑（第四最小切片候选） | 计划中 | 本轮未覆盖。 |
| 地址删除 / 默认地址 | 计划中 | 本轮未覆盖。 |
| 头像上传两步链路 | 计划中 | 仍未进入本轮。 |
| 账户安全与绑定 | 计划中 | 密码修改 / 手机绑定解绑 / 邮箱绑定解绑尚未进入本轮。 |
| Day02 全量收口 | 进行中 | 当前已确认三个已运行回填切片（账户资料编辑 + 地址只读起步 + 地址新增 create-only）。 |

---

## 5. 本次回填备注

1. `v1.6` 为 `v1.5` 的增量回填，新增的是“地址新增（create-only）”最小运行态证据；
2. 可升级说法：`地址新增 create-only 切片运行通过`、`goal1~goal6 全 pass`、`无 blocker`、`失败分支不持久化已验证`；
3. 不能写：`Day02 已完成并回填`、`Day02 全业务已冻结完成`、`整站联调已通过`。
