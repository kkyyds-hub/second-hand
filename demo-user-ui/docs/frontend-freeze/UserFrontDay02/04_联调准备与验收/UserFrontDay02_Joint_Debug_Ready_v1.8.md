# UserFrontDay02 联调准备与验收

- 日期：`2026-04-04`
- 文档版本：`v1.8`
- 当前状态：`进行中（地址默认切换 set-default 切片最小运行态已通过；Day02 未完成）`

---

## 1. 本轮验证范围

仅验证 Day02「地址管理-地址默认切换（set-default）切片」最小运行态：

- 路由：`/account/addresses`
- 请求：`PUT /user/addresses/{id}/default` + 成功后刷新 `GET /user/addresses`
- 行为：列表可达、默认标识可见、提交中禁用、防重复点击、成功提示与默认标识切换、失败提示与状态恢复、不错误持久化、不执行 delete/create/edit 写操作

本轮后端来源为 `browser route mocked`，属于浏览器可控 mock 运行态验证；不是后端真实联调通过结论。

---

## 2. 验收清单（2026-04-04 实际结果）

| 场景 | 本轮结果 | 关键证据 |
|---|---|---|
| goal1：地址列表可达 + 默认标识可见 | pass | `demo-user-ui/.tmp_runtime/2026-04-04-userfront-day02-address-set-default-minimal-runtime/screenshots/before.png`、`.../userfront-day02-address-set-default-minimal-runtime.json`（`flows.goal1_address_list_reachable_default_badge_visible.status=pass`） |
| goal2：非默认地址触发 `PUT /user/addresses/{id}/default` | pass | `.../network/goal2-put-default-success-request.json`、`.../network/goal2-put-default-success-response.json` |
| goal3：提交中禁用 + 防重复点击 | pass | `.../screenshots/saving.png`、`.../userfront-day02-address-set-default-minimal-runtime.json`（`flows.goal3_submitting_disable_and_duplicate_prevent.status=pass`） |
| goal4：成功后自动刷新 `GET /user/addresses` + 默认标识切换 | pass | `.../network/goal4-get-refresh-after-success-request.json`、`.../network/goal4-get-refresh-after-success-response.json`、`.../screenshots/success.png`、`.../screenshots/after-refresh.png` |
| goal5：失败分支提示 / 状态恢复 / 不错误持久化 | pass | `.../network/goal5-put-default-failure-response.json`、`.../network/goal5-get-refresh-after-failure-response.json`、`.../screenshots/failure.png` |
| goal6：本轮不执行 delete/create/edit 写操作 | pass | `.../network/goal6-write-op-boundary-observation.json`（`disallowedWriteCount=0`） |

---

## 3. set-default 验收口径（补充固化）

| 口径项 | 验收标准 | 本轮结论 |
|---|---|---|
| 地址列表可达 | 登录后可进入 `/account/addresses`，且可观察默认地址标识 | pass |
| 默认切换 PUT | 对非默认地址点击“设为默认地址”必须命中 `PUT /user/addresses/{id}/default` | pass |
| 提交态治理 | 提交中按钮禁用且双击不应产生重复 PUT | pass |
| 成功链路 | `PUT code=1` 后自动刷新 `GET /user/addresses`，并可见默认标识从旧地址切换到目标地址 | pass |
| 失败链路 | `PUT code=0` 时展示错误提示、按钮恢复、刷新后不出现错误持久化 | pass |
| 写操作边界 | 本轮不触发 `POST /user/addresses`、`DELETE /user/addresses/{id}`、`PUT /user/addresses/{id}` | pass |

---

## 4. 构建与运行环境证据

- 构建留证：`demo-user-ui/.tmp_runtime/2026-04-04-userfront-day02-address-set-default-minimal-runtime/frontend-build.log`
- Dev 运行日志：`.../dev.log`、`.../dev.err.log`
- 人工摘要：`.../summary.md`
- 机器摘要：`.../userfront-day02-address-set-default-minimal-runtime.json`
- 控制台摘要：`.../verify-stdout.json`

---

## 5. Blocker 分类

- 本轮 blocker：`无`
- 结论依据：`.../summary.md`、`.../verify-stdout.json`、`.../userfront-day02-address-set-default-minimal-runtime.json`（`verdict=pass`、`blocker=null`）。

---

## 6. 下一轮验收入口（仍属 Day02）

1. 进入 Day02 第六最小切片：地址删除 delete-only；
2. 或继续 verify 当前 set-default 切片（重复执行、不同账号样本、长时稳定性）。
