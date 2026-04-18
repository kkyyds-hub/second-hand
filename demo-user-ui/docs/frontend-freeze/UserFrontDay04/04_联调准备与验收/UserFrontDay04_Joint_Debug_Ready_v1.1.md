# UserFrontDay04 联调准备与验收

- 日期：`2026-04-18`
- 文档版本：`v1.1`
- 当前状态：`进行中（第一包只读 pass；第二包 create 驱动最小真实闭环 pass；最终 acceptance 未开始）`

---

## 1. 环境前置条件

1. Day01 登录态、路由守卫、request 头注入保持可用；
2. `SellerController`、`UserProductController` 服务可访问；
3. create 需满足发布前置条件（账号信用策略）；
4. 2026-04-18 本线程已在环境层处理历史 create 前置条件阻塞，并完成一次 create 驱动闭环验证。

---

## 2. Day04 最小验证清单（当前执行态）

| 场景 | 本轮结论 | 证据 |
|---|---|---|
| 卖家工作台摘要（第一包） | `pass` | `demo-user-ui/.tmp_runtime/2026-04-18-userfront-day04-package1-runtime-verify/` |
| 商品列表与详情只读（第一包） | `pass` | `demo-user-ui/.tmp_runtime/2026-04-18-userfront-day04-package1-runtime-verify/` |
| 商品创建（第二包） | `pass` | `demo-user-ui/.tmp_runtime/2026-04-18-userfront-day04-package2-create-unblocked-runtime-v2/network/create-post-response.json` |
| 商品编辑（第二包） | `pass` | `demo-user-ui/.tmp_runtime/2026-04-18-userfront-day04-package2-create-unblocked-runtime-v2/network/edit-put-response.json` |
| 状态流转（withdraw/resubmit/off-shelf/on-shelf） | `pass` | `demo-user-ui/.tmp_runtime/2026-04-18-userfront-day04-package2-create-unblocked-runtime-v2/network/` |
| 商品删除（第二包） | `pass` | `demo-user-ui/.tmp_runtime/2026-04-18-userfront-day04-package2-create-unblocked-runtime-v2/network/delete-response.json` |
| 鉴权回归（写链路请求头） | `pass` | `userfront-day04-package2-runtime-verify-result.json` 中 `authHeaderPresent=true` |

---

## 3. 当日证据保留

- build 证据：`demo-user-ui/.tmp_runtime/2026-04-18-userfront-day04-package2-create-unblocked-runtime-v2/frontend-build.log`
- dev 证据：`demo-user-ui/.tmp_runtime/2026-04-18-userfront-day04-package2-create-unblocked-runtime-v2/frontend-dev.log`
- runtime 总结：`demo-user-ui/.tmp_runtime/2026-04-18-userfront-day04-package2-create-unblocked-runtime-v2/summary.md`
- runtime 明细：`demo-user-ui/.tmp_runtime/2026-04-18-userfront-day04-package2-create-unblocked-runtime-v2/userfront-day04-package2-runtime-verify-result.json`
- network/screenshot：同目录 `network/` 与 `screenshots/`
- 前置条件修复记录：`demo-user-ui/.tmp_runtime/2026-04-18-userfront-day04-package2-create-unblocked-runtime-v2/precondition-fix-evidence.md`

---

## 4. 验收判定建议

1. 第二包 blocker 已关闭，create 驱动最小真实闭环可判定为 `pass`；
2. Day04 仍不直接升级为“已完成并回填”，需单独走最终 acceptance 裁定线程；
3. 本轮未发现必须升级到 `$drive-demo-user-ui-delivery` 的新增跨层缺陷。
