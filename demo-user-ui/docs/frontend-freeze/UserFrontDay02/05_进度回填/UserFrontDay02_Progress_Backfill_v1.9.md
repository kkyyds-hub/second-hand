# UserFrontDay02 进度回填

- 日期：`2026-04-04`
- 文档版本：`v1.9`
- 当前状态：`进行中（账户资料切片 + 地址只读起步切片 + 地址新增 create-only 切片 + 地址编辑 edit-only 切片 + 地址默认切换 set-default 切片 + 地址删除 delete-only 切片均已运行回填；Day02 未完成）`

---

## 1. 当前判定

- 总结：在 `v1.8` 已回填“地址默认切换（set-default）切片”基础上，`2026-04-04` 新增“地址删除（delete-only）最小运行态”回填，`goal1~goal6` 全部 pass。
- 状态判定：`进行中`（可升级为“地址删除 delete-only 切片运行通过”，不能升级为“Day02 已完成并回填”或“整站联调已通过”）。
- blocker：`无`（`verify-stdout.json` 与机器摘要均为 `blocker=null`）。

---

## 2. 已回填完成项（本轮新增）

| 项目 | 判定 | 证据路径 | 回填说明 |
|---|---|---|---|
| Day02 地址删除 delete-only 最小运行态执行完成 | 运行态已确认 | `demo-user-ui/.tmp_runtime/2026-04-04-userfront-day02-address-delete-only-minimal-runtime/summary.md`、`demo-user-ui/.tmp_runtime/2026-04-04-userfront-day02-address-delete-only-minimal-runtime/userfront-day02-address-delete-only-minimal-runtime.json` | 本轮为浏览器可控 mock 运行态验证，不是后端真实联调通过结论。 |
| goal1：地址列表可达 + 删除按钮条件正确（`id!=null`） | 运行态已确认 | `demo-user-ui/.tmp_runtime/2026-04-04-userfront-day02-address-delete-only-minimal-runtime/screenshots/before.png`、`.../userfront-day02-address-delete-only-minimal-runtime.json` | `id!=null` 地址项可删，`id=null` 地址项不显示删除按钮。 |
| goal2：点击删除触发 `DELETE /user/addresses/{id}` | 运行态已确认 | `.../network/goal2-delete-success-request.json`、`.../network/goal2-delete-success-response.json` | 命中目标 endpoint 且目标地址 ID 正确。 |
| goal3：删除中禁用 + 防重复点击 | 运行态已确认 | `.../screenshots/deleting.png`、`.../userfront-day02-address-delete-only-minimal-runtime.json` | 删除中按钮禁用，双击未产生重复 DELETE。 |
| goal4：成功后自动刷新 + 目标项消失/数量变化可见 | 运行态已确认 | `.../network/goal4-get-refresh-after-success-request.json`、`.../network/goal4-get-refresh-after-success-response.json`、`.../screenshots/success.png`、`.../screenshots/after-refresh.png` | 成功后自动触发刷新 GET，删除目标项消失且列表数量变化可见。 |
| goal5：失败分支提示 / 恢复 / 不错误持久化 | 运行态已确认 | `.../network/goal5-delete-failure-response.json`、`.../network/goal5-get-refresh-after-failure-response.json`、`.../screenshots/failure.png` | 失败提示可见、按钮恢复、刷新后状态未错误持久化。 |
| goal6：与 set-default 互斥禁用（并发写抑制） | 运行态已确认 | `.../userfront-day02-address-delete-only-minimal-runtime.json`（`flows.goal6_delete_set_default_mutex_disable.status=pass`、`setDefaultPutCount=0`）、`.../network/unexpected-address-write-observation.json` | 删除进行中同页其他项的 set-default / delete 写操作被抑制。 |
| 本轮构建与 dev 留证 | 构建已通过 + 运行环境可用 | `.../frontend-build.log`、`.../dev.log`、`.../dev.err.log`、`.../verify-stdout.json` | `npm.cmd run build` 与 `npm run dev` 留证完成。 |

---

## 3. flow 结论（2026-04-04 地址删除 delete-only 最小运行态）

| flow | verdict | 说明 |
|---|---|---|
| goal1：地址列表可达，删除按钮显示条件正确（`id!=null`） | pass | 地址列表页可达，`id!=null` 可删、`id=null` 不可删。 |
| goal2：点击删除触发 `DELETE /user/addresses/{id}` | pass | endpoint 命中正确，addressId 正确。 |
| goal3：删除中禁用 + 防重复点击 | pass | 删除中按钮禁用，双击不重复提交。 |
| goal4：成功后自动刷新列表，目标项消失/数量变化可见 | pass | DELETE 成功后触发刷新 GET，目标项消失且数量变化可见。 |
| goal5：失败分支提示与状态恢复正确（且不错误持久化） | pass | 错误提示出现，按钮恢复，刷新后状态未污染。 |
| goal6：与 set-default 互斥禁用（并发写抑制） | pass | 删除进行中同页并发写按钮禁用，未触发 set-default PUT。 |

---

## 4. 已回填切片总览（截至 2026-04-04）

| 切片 | 结论 | 主要证据 |
|---|---|---|
| 账户资料编辑同切片 focused regression | pass | `demo-user-ui/.tmp_runtime/2026-03-30-userfront-day02-account-profile-edit-focused-regression/summary.md` |
| 地址只读起步最小运行态 | pass | `demo-user-ui/.tmp_runtime/2026-03-30-userfront-day02-address-readonly-minimal-runtime/summary.md` |
| 地址新增 create-only 最小运行态 | pass | `demo-user-ui/.tmp_runtime/2026-03-30-userfront-day02-address-create-only-minimal-runtime/summary.md` |
| 地址编辑 edit-only 最小运行态 | pass | `demo-user-ui/.tmp_runtime/2026-03-31-userfront-day02-address-edit-only-minimal-runtime/summary.md` |
| 地址默认切换 set-default 最小运行态 | pass | `demo-user-ui/.tmp_runtime/2026-04-04-userfront-day02-address-set-default-minimal-runtime/summary.md` |
| 地址删除 delete-only 最小运行态 | pass | `demo-user-ui/.tmp_runtime/2026-04-04-userfront-day02-address-delete-only-minimal-runtime/summary.md` |

---

## 5. 仍待推进项（Day02 未完成部分）

| 项目 | 当前判定 | 说明 |
|---|---|---|
| 头像上传两步链路 | 计划中 | 仍未进入本轮。 |
| 账户安全与绑定 | 计划中 | 密码修改 / 手机绑定解绑 / 邮箱绑定解绑尚未进入本轮。 |
| Day02 全量收口 | 进行中 | 当前已确认六个已运行回填切片（账户资料编辑 + 地址只读 + 地址新增 create-only + 地址编辑 edit-only + 地址默认切换 set-default + 地址删除 delete-only）。 |

---

## 6. 本次回填备注

1. `v1.9` 为 `v1.8` 的增量回填，新增的是“地址删除（delete-only）”最小运行态证据；
2. 可升级说法：`地址删除 delete-only 切片运行通过`、`goal1~goal6 全 pass`、`blocker=null`；
3. 必须保留：`本轮为浏览器可控 mock 运行态验证，不是后端真实联调通过结论`；
4. 不能写：`Day02 已完成并回填`、`Day02 全业务已冻结完成`、`整站联调已通过`。
