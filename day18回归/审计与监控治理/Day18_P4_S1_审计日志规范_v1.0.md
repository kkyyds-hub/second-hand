# Day18 P4-S1 审计日志规范 v1.0

- 日期：2026-02-25  
- 对应阶段：`Step P4-S1：审计日志口径统一`  
- 目标：统一敏感操作审计日志字段、动作字典、检索方式与保留口径，确保“可追踪、可审计、可排障”。

---

## 1. 范围与边界

1. 本规范覆盖 Day18 关键敏感动作：登录、支付、取消、支付回调、封禁/解封。  
2. 本规范定义应用侧日志结构与检索口径，不包含 ELK/Kibana 实际部署（由 P4-S3 承接）。  
3. 应用侧审计日志统一使用 `AUDIT` 前缀与结构化字段，不允许自由文本替代核心字段。

---

## 2. 统一字段字典（冻结）

| 字段 | 必填 | 说明 | 示例 |
|---|---|---|---|
| `@timestamp` | 是 | 日志行时间戳（由日志框架输出） | `2026-02-25T17:05:12.345+08:00` |
| `auditId` | 是 | 单次动作唯一标识，用于跨日志串联 | `f37f4f2c2c4f4f9a8f2f8d0e01a1f3c9` |
| `action` | 是 | 动作编码，见动作字典 | `USER_LOGIN` |
| `actorType` | 是 | 操作主体类型（`USER/ADMIN/SYSTEM`） | `ADMIN` |
| `actorId` | 是 | 操作主体标识；未知时写 `-` | `2` |
| `targetType` | 是 | 操作对象类型 | `ORDER` |
| `targetId` | 是 | 操作对象标识；未知时写 `-` | `900044` |
| `result` | 是 | 结果编码（`SUCCESS/FAILED/IDEMPOTENT/IGNORED`） | `FAILED` |
| `ip` | 是 | 来源 IP（优先 `X-Forwarded-For`） | `127.0.0.1` |
| `error` | 条件必填 | 失败原因编码，仅 `FAILED` 分支必填 | `PASSWORD_MISMATCH` |
| `detail` | 否 | 简要上下文，禁止包含敏感明文 | `status=paid` |

---

## 3. 动作字典与结果口径

| action | 触发点 | 成功口径 | 失败口径 |
|---|---|---|---|
| `USER_LOGIN` | 用户密码登录 | `SUCCESS` | `ACCOUNT_NOT_FOUND/ACCOUNT_BANNED/PASSWORD_MISMATCH/...` |
| `ADMIN_LOGIN` | 管理员登录 | `SUCCESS` | `LOGIN_ID_EMPTY/ACCOUNT_NOT_FOUND/NOT_ADMIN/PASSWORD_MISMATCH` |
| `ORDER_PAY` | 买家支付订单 | `SUCCESS/IDEMPOTENT` | `ORDER_CANCELLED/STATUS_NOT_ALLOW` |
| `ORDER_CANCEL` | 买家取消订单 | `SUCCESS/IDEMPOTENT` | `STATUS_NOT_ALLOW` |
| `PAYMENT_CALLBACK` | 第三方回调入站 | `SUCCESS/IDEMPOTENT/IGNORED` | `SIGN_EMPTY/TIMESTAMP_EXPIRED/ORDER_NOT_FOUND/...` |
| `USER_BAN` | 管理员封禁用户 | `SUCCESS/IDEMPOTENT` | `USER_NOT_FOUND/CAS_CONFLICT/ALREADY_BANNED` |
| `USER_UNBAN` | 管理员解封用户 | `SUCCESS/IDEMPOTENT` | `USER_NOT_FOUND/CAS_CONFLICT/STATUS_NOT_BANNED` |

口径约束：
1. 幂等命中统一记录为 `result=IDEMPOTENT`，不记为失败。  
2. 失败场景必须给出稳定 `error` 编码，禁止只打自然语言。  
3. `detail` 允许记录状态与分流原因，不允许输出密码、JWT、验证码、密钥等敏感值。

---

## 4. 当前代码落点清单（静态核验）

| 模块 | 方法 | 动作覆盖 | 状态 |
|---|---|---|---|
| `EmployeeController` | `login` | `ADMIN_LOGIN` 成功/失败分支 | 已覆盖 |
| `AuthServiceImpl` | `loginWithPassword` | `USER_LOGIN` 成功/失败分支 | 已覆盖 |
| `OrderServiceImpl` | `payOrder` | `ORDER_PAY` 成功/幂等/失败分支 | 已覆盖 |
| `OrderServiceImpl` | `cancelOrder` | `ORDER_CANCEL` 成功/幂等/失败分支 | 已覆盖 |
| `OrderServiceImpl` | `handlePaymentCallback` | `PAYMENT_CALLBACK` 忽略/成功/幂等/失败分支 | 已覆盖 |
| `UserServiceImpl` | `banUser/unbanUser` | `USER_BAN/USER_UNBAN` 分支 | 已覆盖 |
| `ViolationServiceImpl` | `banUser/unbanUser` | `USER_BAN/USER_UNBAN` 分支 | 已覆盖 |
| `AuditLogUtil` | `success/failed` | 统一字段模板与 IP 解析 | 已覆盖 |

---

## 5. 保留与检索口径（冻结）

### 5.1 检索方式

1. 按 `auditId` 精确检索单次操作全链路日志。  
2. 按 `action + targetId` 检索某业务对象审计轨迹。  
3. 按 `result=FAILED + error` 聚合失败类型，定位高频问题。  
4. 最小检索关键字：`AUDIT auditId=...`。

### 5.2 保留口径

1. 当前代码层未定义独立审计日志文件，审计日志随应用日志统一输出。  
2. Day18 冻结要求：  
   - 测试环境审计日志可检索保留不少于 14 天；  
   - 生产环境集中日志保留不少于 180 天；  
   - 涉及封禁、支付、登录风控等安全事件可按合规要求延长归档。  
3. 日志导出或归档时，保持原字段不丢失（至少保留 `auditId/action/actorId/targetId/result/error`）。

---

## 6. 评审准入清单（新增敏感操作必须满足）

1. 是否使用 `AuditLogUtil` 输出 `AUDIT` 统一字段。  
2. 是否定义稳定 `action` 与失败 `error` 编码。  
3. 是否覆盖成功、幂等、失败分支。  
4. `detail` 是否经过敏感信息审查（无密码/JWT/验证码/密钥明文）。  
5. 是否可通过 `auditId` 串联完整排查链路。

---

## 7. DoD 对齐（当前阶段）

- [x] 核心敏感动作具备统一日志字段（静态核验通过）。  
- [ ] 已完成运行态检索演练并回填实测证据。  

---

## 8. 代码证据索引

1. `demo-service/src/main/java/com/demo/audit/AuditLogUtil.java`  
2. `demo-service/src/main/java/com/demo/controller/admin/EmployeeController.java`  
3. `demo-service/src/main/java/com/demo/service/serviceimpl/AuthServiceImpl.java`  
4. `demo-service/src/main/java/com/demo/service/serviceimpl/OrderServiceImpl.java`  
5. `demo-service/src/main/java/com/demo/service/serviceimpl/UserServiceImpl.java`  
6. `demo-service/src/main/java/com/demo/service/serviceimpl/ViolationServiceImpl.java`  

---

（文件结束）
