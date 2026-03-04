# Day18 P3-S4 OAuth 真源改造说明 v1.0

- 日期：2026-02-27
- 适用对象：需要审查「第三方登录」实现、首次接触 OAuth 映射治理的同学
- 对应改动代码：
  - `demo-service/src/main/java/com/demo/service/serviceimpl/AuthServiceImpl.java`
  - `demo-pojo/src/main/java/com/demo/entity/UserOauthBind.java`
  - `demo-service/src/main/java/com/demo/mapper/UserOauthBindMapper.java`
  - `demo-service/src/main/resources/mapper/UserOauthBindMapper.xml`
  - `demo-service/src/main/resources/mapper/UserMapper.xml`

---

## 1. 改造结论（先看这段）

这次把第三方登录映射从「仅 Redis」改成了「DB 真源 + Redis 加速」：

1. 真源改为 `user_oauth_bind` 表，Redis 不再承担唯一事实来源职责。
2. 登录优先走 Redis 快路径，但 Redis 失效/脏数据时会自动回源 DB。
3. 历史 Redis-only 数据采用「懒迁移」：登录命中旧值时自动补写 DB。
4. 首次登录使用事务保证「创建用户 + 写绑定」原子提交，并处理并发竞争。

---

## 2. 为什么要改（改造背景）

改造前逻辑是：

1. 读 `auth:oauth:{provider}:{externalId}`。
2. 命中则直接当作 `userId` 使用。
3. 未命中就创建新用户，并把 `userId` 写回 Redis。

该方案可以跑通功能，但有明显风险：

1. Redis 单点真源风险：缓存清空或异常后，绑定关系不可恢复。
2. 缺少 DB 唯一约束：无法用数据库规则兜住并发首登冲突。
3. 审计能力弱：缺少绑定时间、最近登录时间等可追溯字段。
4. 运营治理弱：后续解绑、换绑、风险排查都缺少结构化数据支撑。

---

## 3. 改前 vs 改后对照

| 维度 | 改造前 | 改造后 |
|---|---|---|
| 绑定真源 | Redis | MySQL `user_oauth_bind` |
| Redis 角色 | 真源 + 加速 | 仅加速缓存（可丢可重建） |
| 首次登录 | 创建用户后写 Redis | 事务内「创建用户 + 写绑定」 |
| 并发首登 | 无 DB 唯一键兜底 | DB 唯一约束 + 冲突恢复 |
| 历史数据治理 | 无迁移策略 | 登录时懒迁移（Redis -> DB） |
| 可追溯性 | 弱 | 有 `create_time/update_time/last_login_time` |

---

## 4. 数据模型与约束

新增表：`user_oauth_bind`

关键字段：

1. `user_id`：本地用户 ID（关联 `users.id`）。
2. `provider`：第三方平台类型。
3. `external_id`：第三方平台唯一标识（openId/sub 等）。
4. `bind_status`：绑定状态（1=已绑定，0=已解绑）。
5. `last_login_time`：最近一次登录时间。

关键约束（你已导入脚本）：

1. `UNIQUE(provider, external_id)`：同一第三方账号只能绑定到一个本地用户。
2. `UNIQUE(user_id, provider)`：同一用户在同一平台只能有一条绑定记录。

---

## 5. 文件级改动清单（改了哪里）

## 5.1 新增实体：`UserOauthBind`

文件：`demo-pojo/src/main/java/com/demo/entity/UserOauthBind.java`

用途：

1. 承载 `user_oauth_bind` 表结构。
2. 继承 `BaseAuditEntity`，统一审计时间字段。

## 5.2 新增 Mapper 接口 + XML

文件：

1. `demo-service/src/main/java/com/demo/mapper/UserOauthBindMapper.java`
2. `demo-service/src/main/resources/mapper/UserOauthBindMapper.xml`

新增方法：

1. `selectActiveByProviderAndExternalId`：按外部账号查有效绑定。
2. `insert`：严格写入（冲突交由唯一键抛错）。
3. `insertIgnore`：懒迁移回填（冲突返回 0，不打断登录主流程）。
4. `touchLoginTime`：刷新最近登录时间。

## 5.3 核心改造：`AuthServiceImpl.loginWithThirdParty`

文件：`demo-service/src/main/java/com/demo/service/serviceimpl/AuthServiceImpl.java`

新增核心分支：

1. `tryLoginByOauthCache(...)`：Redis 快路径 + 脏缓存清理 + 旧值懒迁移。
2. `loadUserByOauthBind(...)`：DB 真源回查 + 回填 Redis。
3. `createAndBindThirdPartyUserWithRaceRecover(...)`：首次登录创建绑定，并发冲突恢复。
4. `createAndBindThirdPartyUserTx(...)`：事务内原子写入用户与绑定关系。
5. `backfillLegacyOauthCache(...)`：历史 Redis-only 映射落库。

## 5.4 用户主键回填修复

文件：`demo-service/src/main/resources/mapper/UserMapper.xml`

改动：

1. `insertUser` 增加 `useGeneratedKeys="true" keyProperty="id"`。

原因：

1. 首次第三方登录需要立即拿到 `user.id` 写入 `user_oauth_bind.user_id`。
2. 没有主键回填会导致绑定写入阶段缺少外键值。

---

## 6. 新流程详解（按执行顺序）

`loginWithThirdParty` 现在是三段式：

## 6.1 第 1 段：Redis 快路径

1. 根据 `provider + externalId` 生成 key。
2. 尝试读缓存：
   - 命中新格式：`db:{userId}` -> 直接取用户。
   - 命中旧格式：`{userId}` -> 尝试懒迁移补写 DB，再把缓存升级为新格式。
3. 如果缓存值非法或用户不存在：
   - 视为脏缓存，删除缓存，进入第 2 段回源。

## 6.2 第 2 段：DB 真源回查

1. 查 `user_oauth_bind` 是否有 `provider + externalId` 绑定。
2. 有绑定则查 `users`：
   - 用户存在：刷新 `last_login_time`，回填 Redis，返回登录结果。
   - 用户不存在：抛出绑定异常（提示数据完整性问题）。

## 6.3 第 3 段：首次登录创建

1. Redis 和 DB 都没有绑定，进入首次登录流程。
2. 在事务中执行：
   - 再次双检绑定是否已存在（防并发窗口）。
   - 创建 `users` 记录。
   - 写入 `user_oauth_bind` 记录。
3. 若并发冲突触发唯一键异常：
   - 回读现存绑定并恢复登录，不重复创建脏数据。
4. 成功后写 Redis 缓存并返回 JWT。

---

## 7. 缓存键与数据格式变化

旧格式：

1. `auth:oauth:{provider}:{externalId}` -> `{userId}`

新格式：

1. `auth:oauth:{provider}:{externalId}` -> `db:{userId}`

引入新格式的目的：

1. 让运行时可区分“已落库映射”与“历史旧缓存”。
2. 便于懒迁移逻辑只在旧缓存分支触发。

---

## 8. 兼容性与风险控制点

## 8.1 兼容性

1. 老缓存仍可读（支持纯数字 userId）。
2. 读到老缓存时会自动迁移，不要求停机全量脚本。

## 8.2 风险控制

1. 缓存脏数据自愈：格式错/用户不存在时删缓存并回源。
2. 并发首登兜底：唯一键 + `DuplicateKeyException` 恢复分支。
3. 原子性保障：事务收口用户创建与绑定写入。

---

## 9. 建议验证清单（你可按此回归）

1. 场景 A：缓存命中新格式
   - 预期：不查绑定表也能登录成功。
2. 场景 B：缓存命中旧格式（纯数字）
   - 预期：登录成功，同时 DB 新增/补齐绑定，缓存升级为 `db:{userId}`。
3. 场景 C：缓存 miss + DB 命中
   - 预期：登录成功并回填 Redis。
4. 场景 D：缓存 miss + DB miss（首次登录）
   - 预期：创建用户并写绑定后成功登录。
5. 场景 E：并发首次登录同一外部账号
   - 预期：最终只有一条有效绑定，两个请求都可完成登录。

---

## 10. 本次改造边界（明确未做项）

1. 未引入解绑/换绑 API（本次只收口登录绑定真源）。
2. 未改造第三方平台真实 token 校验流程（当前仍是项目内 mock 口径）。
3. 未新增独立管理后台页面（仅完成后端数据层与服务层收口）。

---

## 11. 对后续工作的价值

1. 为账号安全治理提供真实可追溯的数据基础。
2. 为风控（异常登录、设备画像、频繁切号）预留结构化数据位。
3. 为后续扩展解绑、换绑、合并账号提供可演进模型。

---

（文件结束）

