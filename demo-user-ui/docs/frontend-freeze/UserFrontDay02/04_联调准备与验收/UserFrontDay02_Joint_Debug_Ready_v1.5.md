# UserFrontDay02 联调准备与验收

- 日期：`2026-03-30`
- 文档版本：`v1.5`
- 当前状态：`进行中（地址只读起步切片最小运行态已通过；Day02 未完成）`

---

## 1. 本轮验证范围

仅验证 Day02「地址管理-只读起步切片」最小运行态：

- 路由：`/account/addresses`
- 请求：`GET /user/addresses`
- 行为：`requiresAuth`、`loading / empty / error / retry`、只读展示

不扩到地址新增/编辑/删除/设默认，不扩到账号安全，不扩到 Day03+。

---

## 2. 验收清单（2026-03-30 实际结果）

| 场景 | 本轮结果 | 关键证据 |
|---|---|---|
| goal1：`/account/addresses` 受 requiresAuth 保护 | pass | `demo-user-ui/.tmp_runtime/2026-03-30-userfront-day02-address-readonly-minimal-runtime/userfront-day02-address-readonly-minimal-runtime.json`（`flows.goal1_requiresAuth_guard.status=pass`）<br>`.../screenshots/goal1-unauth-redirect-login.png` |
| goal2：登录后可达地址列表页 | pass | `.../userfront-day02-address-readonly-minimal-runtime.json`（`flows.goal2_login_reach_addresses.status=pass`）<br>`.../screenshots/goal2-goal3-real-route-state.png` |
| goal3：触发 `GET /user/addresses` 且请求/响应留证 | pass | `.../network/goal3-real-get-addresses-request.json`、`.../network/goal3-real-get-addresses-response.json` |
| goal4：loading 行为 | pass | `.../userfront-day02-address-readonly-minimal-runtime.json`（`flows.goal4_loading_state.status=pass`）<br>`.../screenshots/goal4-loading-state.png` |
| goal4：empty 行为 | pass | `.../userfront-day02-address-readonly-minimal-runtime.json`（`flows.goal4_empty_state.status=pass`）<br>`.../screenshots/goal4-empty-state.png` |
| goal4：error 行为 | pass | `.../userfront-day02-address-readonly-minimal-runtime.json`（`flows.goal4_error_state.status=pass`）<br>`.../screenshots/goal4-error-state.png` |
| goal4：retry 行为 | pass | `.../userfront-day02-address-readonly-minimal-runtime.json`（`flows.goal4_retry_state.status=pass`）<br>`.../screenshots/goal4-retry-after-state.png` |
| goal5：list 或 empty 展示 | pass（真实链路 empty，mock retry 验证 list） | `.../userfront-day02-address-readonly-minimal-runtime.json`（`flows.goal5_list_or_empty_render.status=pass`） |
| goal6：地址域无写操作 | pass | `.../network/goal6-address-write-ops-observation.json`（`addressWriteRequests=[]`） |

---

## 3. 本轮固化口径（地址只读）

| 项目 | 口径 | 本轮结论 |
|---|---|---|
| 路由鉴权 | 未登录访问 `/account/addresses` 必须跳转 `/login?redirect=/account/addresses` | pass |
| 主请求 | 地址页加载触发 `GET /user/addresses?page=1&pageSize=20` | pass |
| 空态与列表态 | 若真实返回无数据，显示 empty；列表态可用 mock retry 验证展示结构 | pass |
| 失败恢复 | error 态可见，点击重试后恢复为可读结果态 | pass |
| 写操作边界 | 本轮禁止地址域 `POST/PUT/DELETE` | pass |

---

## 4. 构建与运行环境证据

- 构建留证：`demo-user-ui/.tmp_runtime/2026-03-30-userfront-day02-address-readonly-minimal-runtime/build-verdict.json`、`.../frontend-build.log`
- Dev 运行日志：`.../dev.log`、`.../dev.err.log`
- Dev 运行探针：`.../dev-runtime-probe.json`
- 人工摘要：`.../summary.md`
- 机器摘要：`.../userfront-day02-address-readonly-minimal-runtime.json`

---

## 5. Blocker 分类

- 本轮 blocker：`无`
- 结论依据：`.../summary.md` 与机器摘要（`blocker=null`）。

---

## 6. 下一轮验收入口（仍属 Day02）

1. 可进入 Day02 第三个最小切片（地址新增）并按写操作风险控制单独验证；
2. 或继续 verify 当前只读切片（重复执行、不同账号数据分布、长时稳定性）。

