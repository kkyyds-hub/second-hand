# FrontDay08 联调准备与验收

- 日期：`2026-03-17`
- 文档版本：`v1.0`
- 当前状态：`已完成并回填`

---

## 1. 环境前置条件

1. 后端服务可访问，默认以 `http://localhost:8080` 为准。
2. 前端以 `demo-admin-ui` 项目运行，常用命令为 `npm run dev:real`。
3. 当前页面需要的管理员账号与 token 可用。

---

## 2. 当日联调清单

1. 逐模块抽查至少一条成功和一条失败路径。
2. 验证 401 自动回登录、接口失败提示、mock/real 切换都符合预期。
3. 确认字段兜底没有从 API 层泄漏到页面模板。

---

## 3. 当日需要保留的证据

1. API 模块职责表。
2. 错误处理前后说明。
3. mock/real 切换验证记录。

---

## 4. 验收判定建议

1. 能实际执行的项，优先记录为“运行态已确认”。
2. 只有代码存在但未实测的项，记录为“代码已完成待运行验证”。
3. 联调失败时，必须注明是前端问题、后端问题还是环境阻塞。

---

## 5. `2026-03-15` 本轮最小范围执行记录

1. 本轮只覆盖 `Dashboard`、`UserList`、`ProductReview`、`AuditCenter`、`OpsCenter` 五个页面，对应路径分别是 `/`、`/users`、`/products`、`/audit`、`/ops-center`。
2. 执行顺序遵循“先真实运行态只读，再做低风险错误态验证”：
   - 真实运行态：在 `http://localhost:5173` 的 `npm run dev:real` 环境下进入页面，确认页面可打开、关键只读区块可见，并记录真实 `/api/*` 请求返回。
   - 低风险错误态：通过前端本地 `code=0` 模拟单个读接口失败，验证 Day08 新增的错误 banner、空态边界与“重新加载”入口仍然可见，不触发真实后端写动作。
3. `2026-03-15` 本轮结果：
   - `Dashboard`：pass
   - `UserList`：pass
   - `ProductReview`：pass
   - `AuditCenter`：pass
   - `OpsCenter`：pass
4. 证据统一落在：
   - `demo-admin-ui/docs/frontend-freeze/FrontDay08/04_联调准备与验收/runtime-artifacts/2026-03-15/FrontDay08_runtime_verification_2026-03-15.json`
   - 同目录下各页面 `*_live.png` / `*_fallback.png`
5. 本轮未执行的真实写动作仍按 `risk-controlled` 处理；这些未执行项不阻塞 Day08 关闭，统一转入 FrontDay09 / `drive-demo-admin-ui-delivery`。

---

## 6. 本轮验收结论

1. `2026-03-15` 五个页面的最小范围验收结果均为 `pass`，本轮未发现必须同轮回改的前端 / 后端 / 契约阻塞。
2. 现有证据足以支撑 FrontDay08“错误处理与 API 模块治理”范围关闭，但不足以宣称各业务写动作全部联调完成。
3. 后续若进入封禁 / 解封、审核通过 / 驳回、仲裁 / 举报处理、`publish-once` / `run-once` 等真实写动作，应直接归入 FrontDay09 或 `drive-demo-admin-ui-delivery`。
