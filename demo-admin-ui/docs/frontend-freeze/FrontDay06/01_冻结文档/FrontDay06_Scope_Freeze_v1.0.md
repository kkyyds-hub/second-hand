# FrontDay06 范围冻结（Scope Freeze）

- 日期：`2026-03-15`
- 文档版本：`v1.2`
- 当前状态：`已完成并回填`
- 交付主题：`运维中心只读联调补证据与设置边界收口`

---

## 1. 今日目标

把 FrontDay06 收口为一轮**只读范围闭环**：

1. 让 `OpsCenter.vue` 的运行快照对单接口失败更稳；
2. 用真实后端补齐 Day06 范围内的 GET 证据；
3. 明确 `SystemSettings.vue` 当前仍是静态配置概览，不伪造后端接口；
4. 把结果同步回 `FrontDay06`、`docs/backend-real-linkup.md` 与根 freeze README。

---

## 2. 范围内

| 范围 | 说明 | 必须完成 | 备注 |
|---|---|---|---|
| OpsCenter 页面 | 核对 `orders / outbox / tasks / violation statistics` 的只读运行态 | 是 | 页面不能因单接口失败整页塌掉 |
| OpsCenter 只读接口 | 只验证 GET 查询链路 | 是 | 需要回填真实返回口径 |
| SystemSettings 页面 | 确认页面为静态配置概览、弹窗与入口逻辑正常 | 是 | 不新增虚构 API |
| Freeze docs | 把真实证据、边界与 handoff 写回 | 是 | 不回退当前执行日 |

---

## 3. 范围外

1. **不执行** `POST /admin/ops/outbox/publish-once`、`POST /admin/ops/tasks/*/run-once`。
2. **不伪造** `SystemSettings.vue` 的后端 settings 协议。
3. **不把**后续写动作验证混入 Day06 的只读闭环口径。

---

## 4. DoD / 退出标准

1. `OpsCenter.vue` 只读快照具备聚合容错能力。
2. `npm.cmd run build` 通过。
3. FrontDay06 freeze docs 已补 `2026-03-14` 的真实只读联调证据。
4. `docs/backend-real-linkup.md` 已补 OpsCenter 真实读接口说明。
5. `SystemSettings.vue` 的静态边界已被明确定义。
6. 在只读范围内，Day06 可以关闭。

---

## 5. 交接提示

- 如果后续要推进写动作验证，应单开新一轮，单独记录副作用风险与执行结果。
- Day06 本次闭环的仅是“只读联调 + 设置边界”，不代表 OpsCenter 的所有副作用动作都已验证。
