# UserFrontDay06 联调准备与验收

- 日期：`2026-04-24`
- 文档版本：`v1.5`
- 更新类型：`Day06 final acceptance 文档收口`
- 当前状态：`Day06 owned scope final acceptance pass；Package-1 / Package-2 / Package-3 均已代码落地、构建通过、runtime 验证通过；当前执行日可推进至 UserFrontDay07`

---

## 1. 本版收口口径

本版不重新运行 build / dev / browser runtime；收口依据为既有已通过证据：

1. Package-1 `seller orders / logistics / ship`：`2026-04-22` runtime verify 已通过。
2. Package-2 `order messages`：`2026-04-24` 环境解阻后 runtime rerun 已通过。
3. Package-3 `seller decision`：`2026-04-24` 环境解阻后 runtime rerun 已通过。
4. fresh `npm.cmd run build`：已由 `2026-04-24-userfront-day06-env-unblock-admin` 证据证明通过。
5. Java loopback / embedded Tomcat / `localhost:8080` 与 frontend `localhost:5175`：已由同一证据目录证明解阻并可用。

---

## 2. Day06 包级最终验收

| 包 | 范围 | final acceptance 状态 | 主要证据 |
|---|---|---|---|
| Package-1 | `seller orders / logistics / ship` | pass | `demo-user-ui/.tmp_runtime/2026-04-22-userfront-day06-package1-runtime-verify/userfront-day06-package1-runtime-verify-result.json` |
| Package-2 | `order messages` list / send / mark-as-read | pass | `demo-user-ui/.tmp_runtime/2026-04-24-userfront-day06-env-unblock-admin/userfront-day06-env-unblock-admin-result.json`、`network/package2-*`、`screenshots/package2-*` |
| Package-3 | `seller decision` 同意 / 拒绝入口与提交 | pass | `demo-user-ui/.tmp_runtime/2026-04-24-userfront-day06-env-unblock-admin/userfront-day06-env-unblock-admin-result.json`、`network/package3-*`、`screenshots/package3-*` |

---

## 3. 三层状态

| 层 | final acceptance 结论 | 说明 |
|---|---|---|
| 代码已落地 | 是 | Day06 owned scope 对应页面、路由与 API 模块已存在；本轮仅做文档收口，未修改 `src/` 业务代码。 |
| 构建已通过 | 是 | fresh `npm.cmd run build` 已在 env-unblock 线程通过，证据位于 `build.log` / `build-exit.txt`。 |
| 运行态已验证 | 是（Day06 owned scope） | Package-1 / Package-2 / Package-3 均有 runtime pass 证据。 |

---

## 4. blocker 与边界

- 新增 blocker：`无`。
- 已关闭 blocker：Node / esbuild `spawn EPERM`、Java `Selector.open()` / loopback、localhost backend/frontend runtime 阻断均已关闭。
- 不外推范围：Day06 final acceptance 仅覆盖卖家订单履约、订单会话、卖家售后处理 owned scope；不代表钱包 / 积分 / 信用资产视图、系统通知中心或整站回归已完成。
- seller after-sale 查询接口：本轮不补；Package-3 仍按手动输入 / URL query 预填 `afterSaleId` 的既有边界验收。

---

## 5. 交接结论

`UserFrontDay06` 已完成 final acceptance 文档收口，root README 与 coverage matrix 可将当前执行日推进至 `UserFrontDay07`。
