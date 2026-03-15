# FrontDay09 联调准备与验收

- 日期：`2026-03-15`
- 文档版本：`v1.0`
- 当前状态：`执行中（本轮仅推进 UserList 封禁/解封）`

---

## 1. 本轮唯一目标

- 目标链路：`UserList 封禁 / 解封`
- 选择原因：
  1. `src/pages/users/UserList.vue` 已具备封禁理由弹窗、成功后刷新列表与解封回刷逻辑，页面接线最短。
  2. `src/api/user.ts` 已直接对齐 `PUT /admin/user/{userId}/ban` 与 `PUT /admin/user/{userId}/unban`，无需额外字段转换。
  3. `demo-service/src/main/java/com/demo/controller/admin/UserController.java` 与 `demo-service/src/main/java/com/demo/service/serviceimpl/UserServiceImpl.java` 已提供幂等 ban/unban 能力，且封禁后可立即解封，风险最低、可回滚。
  4. 相比 ProductReview / AuditCenter / OpsCenter，本链路副作用最小，适合在 2026-03-15 先做快速闭环。

## 2. 最小必要核对结论

| 面 | 文件 | 结论 |
|---|---|---|
| 页面 | `demo-admin-ui/src/pages/users/UserList.vue` | `confirmBan()` 调 `restrictUser()`，`handleUnrestrict()` 调 `unrestrictUser()`，成功后都会 `fetchData()` 刷新列表。 |
| API 模块 | `demo-admin-ui/src/api/user.ts` | 真实环境使用 `PUT /admin/user/{id}/ban?reason=...` 与 `PUT /admin/user/{id}/unban`；状态映射 `banned -> 封禁`。 |
| 请求层 | `demo-admin-ui/src/utils/request.ts` | 登录后统一从 `admin_token / token` 读取 token，并放入请求头 `token`。 |
| 后端鉴权 | `demo-service/src/main/resources/application.yml` + `demo-service/src/main/java/com/demo/interceptor/JwtTokenAdminInterceptor.java` | 管理端读取请求头名 `token`，与前端请求层一致。 |
| 后端控制器 | `demo-service/src/main/java/com/demo/controller/admin/UserController.java` | 路径为 `@RequestMapping("/admin/user")`，封禁/解封动作分别为 `@PutMapping("/{userId}/ban")` 与 `@PutMapping("/{userId}/unban")`。 |
| Day09 文档 | `demo-admin-ui/docs/frontend-freeze/FrontDay09/02_接口对齐/FrontDay09_Interface_Alignment_v1.0.md` | 用户链路总口径写为 `GET/POST/PUT /admin/user*`，与本轮验证范围一致；运行态证据此前为空，本轮已补到 04 / 05。 |

## 3. 2026-03-15 实际执行

- 执行方式：`真实后端接口 smoke + 页面接线核对`
- 真实接口：
  1. `POST /admin/employee/login`
  2. `GET /admin/user?page=1&pageSize=20`
  3. `PUT /admin/user/6/ban?reason=FrontDay09%202026-03-15%20user-ban-smoke`
  4. `GET /admin/user?page=1&pageSize=20`
  5. `PUT /admin/user/6/unban`
  6. `GET /admin/user?page=1&pageSize=20`
- 实际观察：
  - 登录返回 `code=1` 且拿到 token；
  - 选取 `userId=6`（原始状态 `active`）做最小可回滚验证；
  - 封禁后列表状态变为 `banned`；
  - 解封后列表状态恢复为 `active`。
- 证据文件：
  - `demo-admin-ui/docs/frontend-freeze/FrontDay09/04_联调准备与验收/runtime-artifacts/2026-03-15/FrontDay09_userlist_ban_unban_2026-03-15.json`

## 4. 结果判定

| 链路 | 结果 | 说明 |
|---|---|---|
| UserList 封禁/解封 | `pass` | 真实接口 smoke 成功，页面 -> API 模块 -> 请求层 -> 控制器口径一致，且动作可回滚。 |
| ProductReview 审核通过/驳回 | `not-run` | 本轮未选中，为避免扩散范围，留待下一轮。 |
| AuditCenter 仲裁/举报处理 | `not-run` | 本轮未选中。 |
| OpsCenter publish-once/run-once | `not-run` | 本轮未选中，且动作副作用高于 UserList。 |

## 5. 验收口径说明

1. 本轮结论建立在**真实接口已执行**与**页面接线已核对**之上。
2. 本轮**未单独执行浏览器点击截图**，因此证据形态以接口 smoke JSON 为主；若后续需要页面级截图，可在同链路补一轮 UI 证据，不影响当前 `pass` 判定。
3. 本轮未发现前端、后端、token 协议或接口字段不一致问题，因此**无代码修复**。
