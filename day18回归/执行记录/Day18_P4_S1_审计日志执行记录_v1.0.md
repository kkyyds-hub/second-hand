# Day18 P4-S1 审计日志执行记录 v1.0

- 日期：2026-02-25（2026-03-04 补采日志样本）  
- 关联复现文档：`day18回归/执行复现步骤/Day18_P4_S1_审计日志口径统一_执行复现_v1.0.md`  
- 当前状态：已执行（运行态与日志串联均已回填）。

---

## 1. 环境信息

1. 服务地址：`http://localhost:18080`  
2. 数据库：`secondhand2`  
3. 执行人：`Codex`  
4. 执行时间：`2026-03-04 10:53`  
5. 日志来源：`_tmp_day18_app18080.out.log`  
6. 原始结果：  
   - `day18回归/执行记录/Day18_P4_S1_动态验证结果_2026-02-25_15-24-22.json`  
   - `day18回归/执行记录/Day18_CloseLoop_Dynamic_Result_2026-03-04_10-53-17.json`

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
| B 管理员登录 | `POST /admin/employee/login` 成功+失败 | `ADMIN_LOGIN` 输出统一字段 | 成功 `loginId=13900000001,password=<redacted_password>` 返回 `code=1`；错误密码返回 `code=0(账号或密码错误)` | `[x]` |
| C 支付/取消 | `POST /user/orders/{id}/pay`、`/cancel` | `ORDER_PAY/ORDER_CANCEL` 成功/失败/幂等分流清晰 | 新建订单 `orderId=900065`：首次支付成功、重复支付幂等、支付后取消返回状态不允许 | `[x]` |
| D 回调 | `POST /payment/callback` 多分支 | `PAYMENT_CALLBACK` 覆盖 `FAILED/IGNORED/IDEMPOTENT/SUCCESS` | 同一 `orderNo=2026030410531818448`：`status=FAIL` 返回忽略；过期时间戳返回失败；重复 SUCCESS 回调返回幂等成功 | `[x]` |
| E 封禁/解封 | `PUT /admin/user/{id}/ban`、`/unban` | `USER_BAN/USER_UNBAN` 字段完整 | `userId=1`：封禁成功 -> 重复封禁幂等；解封成功 -> 重复解封幂等 | `[x]` |

---

## 4. 字段完整性抽检

| 检查项 | 抽检结果 |
|---|---|
| 每条审计日志均带 `auditId` | 已抽检 `USER_LOGIN/ADMIN_LOGIN/ORDER_PAY/ORDER_CANCEL/PAYMENT_CALLBACK/USER_BAN/USER_UNBAN`，均包含 `auditId` |
| `FAILED` 分支均带 `error` | 已抽检 `PASSWORD_MISMATCH`、`STATUS_NOT_ALLOW`、`TIMESTAMP_EXPIRED`，均带 `error=` 字段 |
| 日志中未出现敏感明文字段（密码/JWT/验证码/密钥） | 抽检 `AUDIT` 行未出现敏感明文（密码/JWT 未落在 `AUDIT` 模板字段中） |
| 可通过 `auditId` 串联一次完整链路 | 已可按 `auditId` + `action` 检索同链路成功/失败/幂等分支 |

---

## 5. DoD 勾选（回填区）

- [x] 核心敏感动作具备统一日志字段。  
- [x] 问题排查可通过日志串联关键链路。  
- [x] 动态验证证据已归档（已补 2026-03-04 运行态日志样本）。  

---

（文件结束）
