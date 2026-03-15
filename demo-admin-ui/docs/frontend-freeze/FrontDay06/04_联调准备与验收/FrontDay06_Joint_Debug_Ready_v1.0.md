# FrontDay06 联调准备与验收

- 日期：`2026-03-15`
- 文档版本：`v1.2`
- 当前状态：`已完成并回填`

---

## 1. 联调前提

1. 后端运行在 `http://localhost:8080`。
2. 前端 `demo-admin-ui` 以真实模式运行：`npm run dev:real`。
3. 本地已有有效管理端登录态，可携带 `token` 请求头访问管理端接口。

---

## 2. 本轮验收边界

1. 只验证 OpsCenter 的真实 GET 查询链路。
2. 验证 `OpsCenter.vue` 在只读快照部分失败时仍能局部展示。
3. 验证 `SystemSettings.vue` 保持静态范围，不新增虚构 settings API。
4. **不执行** `POST /admin/ops/outbox/publish-once`、`POST /admin/ops/tasks/*/run-once`。

---

## 3. 实测结果（2026-03-14 / 2026-03-15）

1. **构建通过**
   - 在 `demo-admin-ui` 目录执行 `npm.cmd run build`，通过。

2. **页面入口可访问**
   - `http://localhost:5173/ops` -> HTTP `200`
   - `http://localhost:5173/settings` -> HTTP `200`

3. **OpsCenter 只读接口全部返回 `code=1`**
   - `GET /admin/ops/outbox/metrics` -> `new=0 / sent=84 / fail=0 / failRetrySum=0`
   - `GET /admin/ops/tasks/ship-timeout?page=1&pageSize=1` -> `total=29`
   - `GET /admin/ops/tasks/refund?page=1&pageSize=1` -> `total=29`
   - `GET /admin/ops/tasks/ship-reminder?page=1&pageSize=1` -> `total=54`
   - `GET /admin/orders?page=1&pageSize=1` -> `total=68`
   - `GET /admin/users/user-violations/statistics` -> Top1=`ship_timeout`，`count=1795`

4. **页面容错行为已对齐**
   - `OpsCenter.vue` 使用 `fetchOpsRuntimeBundle()` 聚合读取只读数据；
   - 任一接口失败时，不再让整页报错，只降级对应卡片并提示缺失来源；
   - `SystemSettings.vue` 仍是静态配置概览，不存在新增 settings API 联调动作。

5. **副作用接口未执行**
   - 本轮未触发 `publish-once`
   - 本轮未触发任何 `run-once`

---

## 4. 验收结论

1. Day06 的“只读联调补证据”目标已达成。
2. 写动作联调从一开始就不属于本轮范围，因此不再作为 Day06 的未完成理由。
3. 后续如需推进写动作验证，必须单开新一轮并重新记录执行结果。
4. 因此 Day06 可以按既定只读范围认定为 `已完成并回填`。
