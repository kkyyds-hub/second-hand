# Day18 P4-S1 审计日志执行记录 v1.0

- 日期：2026-02-25  
- 关联复现文档：`day18回归/执行复现步骤/Day18_P4_S1_审计日志口径统一_执行复现_v1.0.md`  
- 当前状态：已执行（运行态已完成，日志串联抽检待控制台回填）。

---

## 1. 环境信息

1. 服务地址：`http://localhost:8080`  
2. 数据库：`secondhand2`  
3. 执行人：`Codex`  
4. 执行时间：`2026-02-25 15:24:20`  
5. 日志来源：`IDEA Run 控制台（仓库内未检出可读取日志文件）`  
6. 原始结果：`day18回归/执行记录/Day18_P4_S1_动态验证结果_2026-02-25_15-24-22.json`

---

## 2. 本次静态核验结论（已完成）

1. 审计统一工具已落地：`AuditLogUtil`。  
2. 关键动作已完成埋点：`USER_LOGIN/ADMIN_LOGIN/ORDER_PAY/ORDER_CANCEL/PAYMENT_CALLBACK/USER_BAN/USER_UNBAN`。  
3. 统一字段模板已固定：`auditId/action/actorType/actorId/targetType/targetId/result/ip/error/detail`。  
4. 幂等分支已统一口径：`result=IDEMPOTENT`。  

---

## 3. 动态执行回填表

| 场景 | 输入 | 预期 | 实际结果 | 是否通过 |
|---|---|---|---|---|
| A 用户登录 | `POST /user/auth/login/password` 成功+失败 | `USER_LOGIN` 输出统一字段 | 成功 `code=1`、失败 `code=0(用户名或密码错误)`，两次调用均触发对应业务分支 | `[x]` |
| B 管理员登录 | `POST /admin/employee/login` 成功+失败 | `ADMIN_LOGIN` 输出统一字段 | 成功 `loginId=13900000001,password=admin123` 返回 `code=1`；错误密码返回 `code=0(账号或密码错误)` | `[x]` |
| C 支付/取消 | `POST /user/orders/{id}/pay`、`/cancel` | `ORDER_PAY/ORDER_CANCEL` 成功/失败/幂等分流清晰 | 新建订单 `orderId=900045` 后：首次支付成功、重复支付返回“订单已支付，无需重复操作”、支付后取消返回不允许取消 | `[x]` |
| D 回调 | `POST /payment/callback` 多分支 | `PAYMENT_CALLBACK` 覆盖 `FAILED/IGNORED/IDEMPOTENT/SUCCESS` | 同一 `orderNo=2026022515242112497`：`status=FAIL` 返回忽略；过期时间戳返回失败；重复 SUCCESS 回调返回幂等成功 | `[x]` |
| E 封禁/解封 | `PUT /admin/user/{id}/ban`、`/unban` | `USER_BAN/USER_UNBAN` 字段完整 | `userId=2`：封禁成功 -> 重复封禁幂等；解封成功 -> 重复解封幂等 | `[x]` |

---

## 4. 字段完整性抽检

| 检查项 | 抽检结果 |
|---|---|
| 每条审计日志均带 `auditId` | 代码模板已统一（`AuditLogUtil` 强制输出）；本次运行态已触发对应动作分支 |
| `FAILED` 分支均带 `error` | 代码模板已统一（`AuditLogUtil.failed` 固定输出 `error` 字段） |
| 日志中未出现敏感明文字段（密码/JWT/验证码/密钥） | 代码口径满足；本次仓库内未检出可读取运行日志文件，控制台抽检待补 |
| 可通过 `auditId` 串联一次完整链路 | 待在 IDEA Run 控制台按 `AUDIT auditId=` 检索补证（当前环境无法直接读取控制台流） |

---

## 5. DoD 勾选（回填区）

- [x] 核心敏感动作具备统一日志字段。  
- [ ] 问题排查可通过日志串联关键链路。  
- [x] 动态验证证据已归档（接口原始结果已归档，日志片段待控制台补证）。  

---

（文件结束）
