# Day18 P5-S2 登录风控规则文档 v1.0

- 日期：2026-02-25
- 对应阶段：`Step P5-S2：登录风控与账号保护收口`
- 目标：让“连续失败自动冻结 + 管理端人工解冻 + 审计留痕”具备统一规则与可验证证据。

---

## 1. 结论先行（本版冻结）

1. 登录失败风控采用 Redis 计数窗口：`5 次 / 30 分钟`。
2. 达到阈值后执行 CAS 冻结：仅 `active -> frozen`，避免并发覆盖。
3. 自动冻结会写 `user_bans`（`source=AUTO_RISK`，`TEMP`，默认 1 小时）并触发信用重算。
4. 管理端人工解冻入口为：`PUT /admin/user/{userId}/unban`。
5. 已补充风控审计字段：`LOGIN_RISK_FREEZE`（SUCCESS/IDEMPOTENT/FAILED）。

---

## 2. 登录失败冻结策略（自动风控）

## 2.1 判定参数

| 项 | 当前值 | 说明 |
|---|---:|---|
| 失败计数 key | `auth:login_fail:{userId}` | 按用户维度聚合 |
| 触发阈值 | `MAX_FAIL_COUNT=5` | 连续失败达到阈值触发冻结 |
| 计数窗口 | `FAIL_WINDOW_MINUTES=30` | 首次失败写入 TTL |
| 冻结动作 | `active -> frozen` | CAS 条件更新 |
| 自动封禁记录 | `ban_type=TEMP`，`source=AUTO_RISK`，`end_time=now+1h` | 保留审计与统计依据 |

## 2.2 执行链路

1. `POST /user/auth/login/password` 密码错误。
2. 递增失败计数（Redis）。
3. 若 `failCount >= 5`：
   - CAS 更新 `users.status: active -> frozen`；
   - 插入 `user_bans` 自动风控记录；
   - 调用 `creditService.recalcUserCredit(..., BAN_ACTIVE, banId)`；
   - 写审计日志 `action=LOGIN_RISK_FREEZE`。
4. 后续再登录会在状态预检阶段直接返回“账号已被暂时冻结”。

---

## 3. 解冻流程（人工处置）

## 3.1 运维入口

| 动作 | 接口 | 说明 |
|---|---|---|
| 管理员解冻 | `PUT /admin/user/{userId}/unban` | 状态 CAS 回写到 `active` |
| 解冻结果 | 成功/幂等/并发冲突 | 返回语义固定，便于值班处置 |

## 3.2 处置边界（自动 vs 人工）

| 维度 | 自动风控 | 人工处置 |
|---|---|---|
| 触发条件 | 连续失败达到阈值 | 管理员确认后手工执行 |
| 状态变更 | `active -> frozen`（CAS） | `* -> active`（CAS） |
| 记录归档 | `user_bans` 写 `AUTO_RISK` 记录 | 通过审计日志记录管理员动作 |
| 信用影响 | 自动冻结写 ban 记录后重算信用 | 人工解冻当前仅恢复账号状态（见“已知边界”） |

---

## 4. 风控事件审计字段（本次补充）

## 4.1 审计动作字典

| action | 场景 | result |
|---|---|---|
| `USER_LOGIN` | 登录成功/失败/冻结态拦截 | `SUCCESS` / `FAILED` |
| `LOGIN_RISK_FREEZE` | 达阈值自动冻结（新增） | `SUCCESS` / `IDEMPOTENT` / `FAILED` |
| `USER_UNBAN` | 管理员解冻 | `SUCCESS` / `IDEMPOTENT` / `FAILED` |

## 4.2 统一字段模板

审计字段口径统一为：
`auditId, action, actorType, actorId, targetType, targetId, result, ip, error, detail`

其中 `LOGIN_RISK_FREEZE.detail` 约定包含：
1. `failCount`
2. `source=AUTO_RISK`
3. `banId`（成功场景）或 `latestStatus`（冲突/幂等场景）

---

## 5. 已知边界与操作规范

1. 当前人工解冻（`/admin/user/{userId}/unban`）以“账号状态恢复”为主。
2. 自动风控 ban 记录为临时记录（默认 1 小时），用于审计与信用统计。
3. 运维规范建议：
   - 解冻前先确认是否为误触发（密码输错、脚本误调用）；
   - 解冻后立即要求用户改密并启用二次验证；
   - 必要时补查同 IP 的异常登录行为。

---

## 6. DoD 对齐（当前阶段）

- [x] 已形成连续失败触发保护逻辑与阈值口径。  
- [x] 已形成解冻流程与操作规范文档。  
- [ ] 待完成动态验证回填（接口 + SQL + 审计日志检索证据）。

---

## 7. 代码证据索引

1. `demo-service/src/main/java/com/demo/service/serviceimpl/AuthServiceImpl.java`
2. `demo-service/src/main/java/com/demo/service/serviceimpl/UserServiceImpl.java`
3. `demo-service/src/main/java/com/demo/controller/user/UserAuthController.java`
4. `demo-service/src/main/java/com/demo/controller/admin/UserController.java`
5. `demo-service/src/main/java/com/demo/mapper/UserBanMapper.java`
6. `demo-service/src/main/resources/mapper/UserBanMapper.xml`
7. `demo-service/src/main/resources/mapper/UserMapper.xml`
8. `demo-service/src/main/java/com/demo/audit/AuditLogUtil.java`
9. `demo-service/src/main/java/com/demo/interceptor/JwtTokenUserInterceptor.java`

---

（文件结束）
