# UserFrontDay02 联调准备与验收

- 日期：`2026-04-04`
- 文档版本：`v1.9`
- 当前状态：`进行中（地址删除 delete-only 切片最小运行态已通过；Day02 未完成）`

---

## 1. 本轮验证范围

仅验证 Day02「地址管理-地址删除（delete-only）切片」最小运行态：

- 路由：`/account/addresses`
- 请求：`DELETE /user/addresses/{id}` + 成功后刷新 `GET /user/addresses`
- 行为：列表可达、删除按钮条件正确（`id!=null`）、删除中禁用、防重复点击、成功提示与目标项消失、失败提示与状态恢复、不错误持久化、与 set-default 的互斥禁用（并发写操作抑制）

本轮后端来源为 `browser route mocked`，属于浏览器可控 mock 运行态验证；不是后端真实联调通过结论。

---

## 2. 验收清单（2026-04-04 实际结果）

| 场景 | 本轮结果 | 关键证据 |
|---|---|---|
| goal1：地址列表可达 + 删除按钮条件正确（`id!=null`） | pass | `demo-user-ui/.tmp_runtime/2026-04-04-userfront-day02-address-delete-only-minimal-runtime/screenshots/before.png`、`.../userfront-day02-address-delete-only-minimal-runtime.json`（`flows.goal1_address_list_reachable_delete_button_condition.status=pass`） |
| goal2：点击删除触发 `DELETE /user/addresses/{id}` | pass | `.../network/goal2-delete-success-request.json`、`.../network/goal2-delete-success-response.json` |
| goal3：删除中禁用 + 防重复点击 | pass | `.../screenshots/deleting.png`、`.../userfront-day02-address-delete-only-minimal-runtime.json`（`flows.goal3_deleting_disable_and_duplicate_prevent.status=pass`） |
| goal4：成功后自动刷新 `GET /user/addresses` + 目标项消失/数量变化 | pass | `.../network/goal4-get-refresh-after-success-request.json`、`.../network/goal4-get-refresh-after-success-response.json`、`.../screenshots/success.png`、`.../screenshots/after-refresh.png` |
| goal5：失败分支提示 / 状态恢复 / 不错误持久化 | pass | `.../network/goal5-delete-failure-response.json`、`.../network/goal5-get-refresh-after-failure-response.json`、`.../screenshots/failure.png` |
| goal6：与 set-default 互斥禁用（并发写抑制） | pass | `.../userfront-day02-address-delete-only-minimal-runtime.json`（`flows.goal6_delete_set_default_mutex_disable.status=pass`、`setDefaultPutCount=0`）、`.../network/unexpected-address-write-observation.json` |

---

## 3. delete-only 验收口径（补充固化）

| 口径项 | 验收标准 | 本轮结论 |
|---|---|---|
| 地址列表可达 | 登录后可进入 `/account/addresses`，可见地址卡片与删除按钮区域 | pass |
| 删除按钮条件 | 仅 `id!=null` 的地址项可见并可触发删除；`id=null` 不应出现删除按钮 | pass |
| 删除 DELETE | 点击“删除地址”必须命中 `DELETE /user/addresses/{id}` 且 addressId 正确 | pass |
| 提交态治理 | 删除中按钮禁用且双击不应产生重复 DELETE | pass |
| 成功链路 | `DELETE code=1` 后自动刷新 `GET /user/addresses`，目标项消失或数量变化可见 | pass |
| 失败链路 | `DELETE code=0` 时展示错误提示、按钮恢复、刷新后不出现错误持久化 | pass |
| 互斥禁用 | 删除进行中应抑制并发 set-default / delete 写操作（同页其他项按钮禁用） | pass |

---

## 4. 构建与运行环境证据

- 构建留证：`demo-user-ui/.tmp_runtime/2026-04-04-userfront-day02-address-delete-only-minimal-runtime/frontend-build.log`
- Dev 运行日志：`.../dev.log`、`.../dev.err.log`
- 人工摘要：`.../summary.md`
- 机器摘要：`.../userfront-day02-address-delete-only-minimal-runtime.json`
- 控制台摘要：`.../verify-stdout.json`

---

## 5. Blocker 分类

- 本轮 blocker：`无`
- 结论依据：`.../summary.md`、`.../verify-stdout.json`、`.../userfront-day02-address-delete-only-minimal-runtime.json`（`verdict=pass`、`blocker=null`）。

---

## 6. 下一轮验收入口（仍属 Day02）

1. 进入 Day02 收口评估（docs-only）：核对六个已运行切片与剩余未覆盖范围（头像上传、账号安全与绑定）；
2. 或继续新增切片（优先头像上传两步链路 / 账号安全与绑定最小切片）。
