# UserFrontDay06 进度回填

- 日期：`2026-04-24`
- 文档版本：`v1.6`
- 更新类型：`环境解阻后 Package-2 / Package-3 runtime pass 回填`

---

## 1. 三层状态

| 层 | 当前状态 | 说明 |
|---|---|---|
| 代码已落地 | 是 | 本轮未改业务代码 |
| 构建已通过 | 是 | fresh `npm.cmd run build` 已通过 |
| 运行态已验证 | 是（包级） | Package-1 沿用既有 pass；Package-2 / Package-3 本轮 runtime rerun pass |

---

## 2. 包级状态

| 包 | 当前状态 | 证据 |
|---|---|---|
| Package-1 `seller orders / logistics / ship` | runtime pass | `demo-user-ui/.tmp_runtime/2026-04-22-userfront-day06-package1-runtime-verify/userfront-day06-package1-runtime-verify-result.json` |
| Package-2 `order messages` | runtime pass | `demo-user-ui/.tmp_runtime/2026-04-24-userfront-day06-env-unblock-admin/userfront-day06-env-unblock-admin-result.json`、`network/package2-*`、`screenshots/package2-*` |
| Package-3 `seller decision` | runtime pass | `demo-user-ui/.tmp_runtime/2026-04-24-userfront-day06-env-unblock-admin/userfront-day06-env-unblock-admin-result.json`、`network/package3-*`、`screenshots/package3-*` |
| Day06 final acceptance | 未在本轮推进 | 用户明确要求本轮不做 final acceptance、不推进 Day07 |

---

## 3. 本轮关键事实

- Node / esbuild `spawn EPERM` 已解阻。
- Java `Selector.open()` / loopback 已解阻。
- backend `http://localhost:8080` 与 frontend `http://localhost:5175` 均已启动并参与 runtime。
- Package-2 观察到订单会话 list / send / mark-as-read 全链路请求。
- Package-3 通过 fresh completed order + `APPLIED afterSaleId=7` 完成 seller decision 提交。
