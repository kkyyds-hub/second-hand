# 用户端前端冻结文档主入口

> 启用日期：`2026-03-18`
> 适用项目：`demo-user-ui`
> 当前唯一主入口：`demo-user-ui/docs/frontend-freeze/README.md`

这套文档统一管理 `demo-user-ui` 的用户端计划、冻结、联调、回填与移交。

`demo-admin-ui` 已在 `2026-03-16` 完成 `FrontDay10` 收口；用户端必须使用自己的 `UserFrontDay` 体系推进，不能把用户端工作继续写回管理端冻结文档。

---

## 1. 这套体系解决什么问题

1. 解决用户端计划、范围、证据散落的问题；
2. 解决“已有主工程，但没有唯一推进入口”的问题；
3. 解决“先做哪个业务域、做到哪一层算完成”的问题；
4. 解决联调、freeze、handoff 缺少统一口径的问题。

---

## 2. 当前推荐执行日（截至 2026-04-24）

- 当前日期：`2026-04-24`
- 当前执行日：`UserFrontDay07`
- 当前执行主题：`钱包、积分与信用资产视图`
- 当前状态：`UserFrontDay07 Package-1A 资产中心 auth/data 前置已解除，wallet / points / credit 读链路 runtime pass`
- 进入入口：`demo-user-ui/docs/frontend-freeze/UserFrontDay07/README.md`
- Day06 final acceptance 文档：`demo-user-ui/docs/frontend-freeze/UserFrontDay06/04_联调准备与验收/UserFrontDay06_Joint_Debug_Ready_v1.5.md`、`demo-user-ui/docs/frontend-freeze/UserFrontDay06/05_进度回填/UserFrontDay06_Progress_Backfill_v1.7.md`
- 接棒依据：
  1. `UserFrontDay02` 已于 `2026-04-22` 完成 final acceptance docs-only adjudication，并升级为 `已完成并回填`；
  2. `UserFrontDay03`、`UserFrontDay04`、`UserFrontDay05` 当前均已完成并回填，不再作为 active day；
  3. `UserFrontDay06` 已于 `2026-04-24` 完成 final acceptance 文档收口，Package-1 / Package-2 / Package-3 均为 runtime pass；
  4. `UserFrontDay07` 已开始执行，Package-1 聚焦钱包 / 积分 / 信用资产中心最小完整切片。
- 当前边界：
  - Day06 final acceptance 仅覆盖卖家订单履约、订单会话、卖家售后处理 owned scope；
  - Day07 Package-1 已完成代码落地与 fresh build；Package-1A 已完成 wallet / points / credit 读链路 runtime pass，但不写 Day07 final acceptance；
  - 系统通知中心仍归属 `UserFrontDay08`，不并入 Day06 或 Day07；
  - 当前未识别出需要升级到 `$drive-demo-user-ui-delivery` 的新冲突。

---

## 3. 当前前端基线

| 层级 | 当前结论 | 证据等级 | 主要证据 | 明确边界 |
|---|---|---|---|---|
| Day01 基线 | Day01 最小用户端基建、鉴权壳、登录 / 退出、注册、激活、首页 seller summary、账户中心基础展示均已完成并回填 | `Day01 = 已完成并回填` | `demo-user-ui/docs/frontend-freeze/UserFrontDay01/README.md`、`demo-user-ui/docs/frontend-freeze/UserFrontDay01/05_进度回填/UserFrontDay01_Progress_Backfill_v1.10.md`、`demo-user-ui/docs/frontend-freeze/00_Business_Coverage_Matrix.md` | 不等于整站联调已通过，也不代表 Day02+ 已覆盖 |
| Day02 最终裁定 | Day02 已于 `2026-04-22` 基于十个已运行回填切片完成 final acceptance docs-only adjudication，口径升级为 `已完成并回填` | 代码已确认 + 构建已通过 + 运行态已确认（其中手机绑定/解绑为 joint-debug 级 running evidence，`edit-only / set-default / delete-only` 为浏览器可控 mock 运行态） | `demo-user-ui/docs/frontend-freeze/UserFrontDay02/01_冻结文档/UserFrontDay02_Scope_Freeze_v1.3.md`、`demo-user-ui/docs/frontend-freeze/UserFrontDay02/02_接口对齐/UserFrontDay02_Interface_Alignment_v1.4.md`、`demo-user-ui/docs/frontend-freeze/UserFrontDay02/03_API模块/UserFrontDay02_API_Module_Plan_v2.1.md`、`demo-user-ui/docs/frontend-freeze/UserFrontDay02/04_联调准备与验收/UserFrontDay02_Joint_Debug_Ready_v2.5.md`、`demo-user-ui/docs/frontend-freeze/UserFrontDay02/05_进度回填/UserFrontDay02_Progress_Backfill_v2.5.md` | Day02 完成仅覆盖 Day02 owned scope，不等于整站联调已通过 |
| Day03 / Day04 / Day05 已回填基线 | Day03 市场/评论/收藏、Day04 卖家工作台/商品管理、Day05 买家订单/买家售后均已完成并回填 | 代码已确认 + 构建已通过 + 对应 owned scope 运行态已确认 | `demo-user-ui/docs/frontend-freeze/UserFrontDay03/README.md`、`demo-user-ui/docs/frontend-freeze/UserFrontDay04/README.md`、`demo-user-ui/docs/frontend-freeze/UserFrontDay05/README.md`、`demo-user-ui/docs/frontend-freeze/00_Business_Coverage_Matrix.md` | 这些结论不外推 Day06 或后续资产域，也不等于整站联调已通过 |
| Day06 final acceptance | Day06 Package-1 / Package-2 / Package-3 均已完成并回填，owned scope final acceptance pass | 代码已确认 + fresh build 已通过 + 运行态已确认（Day06 owned scope） | `demo-user-ui/docs/frontend-freeze/UserFrontDay06/04_联调准备与验收/UserFrontDay06_Joint_Debug_Ready_v1.5.md`、`demo-user-ui/docs/frontend-freeze/UserFrontDay06/05_进度回填/UserFrontDay06_Progress_Backfill_v1.7.md`、`demo-user-ui/.tmp_runtime/2026-04-22-userfront-day06-package1-runtime-verify/userfront-day06-package1-runtime-verify-result.json`、`demo-user-ui/.tmp_runtime/2026-04-24-userfront-day06-env-unblock-admin/userfront-day06-env-unblock-admin-result.json` | 仅覆盖 Day06 owned scope；不外推钱包 / 积分 / 信用、系统通知或整站回归 |
| Day07 当前执行日 | Package-1A 资产中心 auth/data 前置已解除，wallet / points / credit 读链路 runtime pass | 代码已确认 + fresh build 已通过 + 运行态已确认（资产中心读链路） | `demo-user-ui/docs/frontend-freeze/UserFrontDay07/README.md`、`demo-user-ui/docs/frontend-freeze/UserFrontDay07/05_进度回填/UserFrontDay07_Progress_Backfill_v1.1.md`、`demo-user-ui/.tmp_runtime/2026-04-26-userfront-day07-package1A-auth-data-runtime-2026-04-26-225327/runtime-conclusion.md`、`demo-user-ui/docs/frontend-freeze/00_Business_Coverage_Matrix.md` | 不写 Day07 final acceptance；不把提现写成真实金融出金已打通；不推进 Day08 |

说明：

- Day01 的完成证据仍然来自 `2026-03-19 ~ 2026-03-21` 既有留证，本轮没有重开 Day01 验证；
- Day02 最终裁定基于既有十个已运行回填切片与既有文档链完成，不新增 build/dev/browser/runtime 实测；
- Day06 final acceptance 基于 `2026-04-22` Package-1 runtime 与 `2026-04-24` env-unblock Package-2 / Package-3 runtime pass 证据完成，本轮仅做文档收口；
- Day07 Package-1A 已在 `2026-04-26` 完成 browser runtime / network verify，证据目录 `demo-user-ui/.tmp_runtime/2026-04-26-userfront-day07-package1A-auth-data-runtime-2026-04-26-225327`；
- 若后续实现涉及已完成执行日基线变更，必须先在对应执行线程诚实回填影响，再决定是否补记。

---

## 4. 当前推荐动作

1. 保持 `UserFrontDay07` 为当前 active day；
2. 后续如继续 Day07，只验证提现申请等尚未覆盖写链路；不得把 Package-1A 读链路外推为真实金融出金；
3. 不把 Day06 订单履约 / 会话 / seller decision 结论外推到资产域；
4. 不把 Day08 系统通知中心提前并入 Day07；
5. Day07 执行过程中如发现跨前后端真实冲突，再按交付规则判断是否升级 `$drive-demo-user-ui-delivery`。


