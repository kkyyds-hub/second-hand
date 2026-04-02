# UserFrontDay02 联调准备与验收

- 日期：`2026-03-30`
- 文档版本：`v1.4`
- 当前状态：`进行中（同切片 focused regression 已通过；Day02 未完成）`

---

## 1. 本轮验证范围

仅验证 Day02 同切片 focused regression：`AccountCenter 昵称/简介编辑 + PATCH /user/me/profile + saveCurrentUser() 回写`。

不扩到地址管理、账号安全、Day03+。

---

## 2. 验收清单（2026-03-30 实际结果）

| 场景 | 本轮结果 | 关键证据 |
|---|---|---|
| case1：仅改 nickname 提交 | pass | `demo-user-ui/.tmp_runtime/2026-03-30-userfront-day02-account-profile-edit-focused-regression/userfront-day02-account-profile-focused-regression.json`（`flows.case1_nickname_only.status=pass`） |
| case2：仅改 bio 提交 | pass | `.../userfront-day02-account-profile-focused-regression.json`（`flows.case2_bio_only.status=pass`） |
| case3：nickname + bio 同改提交 | pass | `.../userfront-day02-account-profile-focused-regression.json`（`flows.case3_nickname_bio_both.status=pass`） |
| case4：无变更直接提交 | pass | `.../userfront-day02-account-profile-focused-regression.json`（`flows.case4_no_change_submit.status=pass`、`patchReqCount=0`） |
| case5：提交失败分支（mock forced error） | pass | `.../userfront-day02-account-profile-focused-regression.json`（`flows.case5_forced_failure_branch.status=pass`）<br>`.../screenshots/case5_forced_failure_branch-error.png` |

---

## 3. 分支行为复核口径（本轮新增）

| 分支 | 口径 | 本轮结论 |
|---|---|---|
| 无变更提交分支 | 提交按钮保持禁用，且不发送 `PATCH /user/me/profile` | pass |
| 强制失败分支 | 错误提示可见、按钮与输入控件恢复可操作、失败值不持久化 | pass |

---

## 4. 构建与运行环境证据

- 构建留证：`demo-user-ui/.tmp_runtime/2026-03-30-userfront-day02-account-profile-edit-focused-regression/build-verdict.json`、`.../frontend-build.log`
- Dev 运行探针：`demo-user-ui/.tmp_runtime/2026-03-30-userfront-day02-account-profile-edit-focused-regression/dev-runtime-probe.json`
- 人工摘要：`demo-user-ui/.tmp_runtime/2026-03-30-userfront-day02-account-profile-edit-focused-regression/summary.md`
- 机器摘要：`demo-user-ui/.tmp_runtime/2026-03-30-userfront-day02-account-profile-edit-focused-regression/userfront-day02-account-profile-focused-regression.json`

---

## 5. Blocker 分类

- 本轮 blocker：`无`
- 结论依据：`.../summary.md` 与机器摘要均未记录阻断项。

---

## 6. 下一轮验收入口（仍属 Day02）

1. 可进入 Day02 下一最小切片（建议：地址列表骨架或安全中心第一刀）；
2. 若需要先提置信心，可继续对当前切片补充长时回归（重复执行与随机输入集）。
