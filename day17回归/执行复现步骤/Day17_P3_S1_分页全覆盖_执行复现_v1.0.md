# Day17 Step P3-S1：分页全覆盖与大查询收口执行复现步骤

- 文档版本：v1.0
- 日期：2026-02-24
- 目标：验证 P3-S1 改造后，列表接口全部支持 `page/pageSize`，并由数据库分页查询替代无边界全量查询。

---

## 1. 前置条件
1. 服务已启动，数据库连接正常。
2. 已准备可用 token（普通用户 + 管理员）。
3. 测试库中存在以下基础数据：
   - 至少 1 个用户有 `user_credit_logs` 记录；
   - 至少 1 个用户有多条地址记录；
   - 至少 1 个商品有多条 `product_violations` 记录；
   - 至少 1 条任务记录存在于任务表（`order_ship_timeout_task` / `order_refund_task` / `order_ship_reminder_task`）。

---

## 2. 本次复现接口清单
1. `GET /admin/ops/tasks/ship-timeout?page=1&pageSize=20`
2. `GET /admin/ops/tasks/refund?page=1&pageSize=20`
3. `GET /admin/ops/tasks/ship-reminder?page=1&pageSize=20`
4. `GET /user/credit/logs?page=1&pageSize=20`
5. `GET /admin/credit/logs?userId={userId}&page=1&pageSize=20`
6. `GET /admin/products/{productId}/violations?page=1&pageSize=20`
7. `GET /user/addresses?page=1&pageSize=20`

---

## 3. 统一校验项（每个接口都要检查）
1. 返回结构为 `Result<PageResult<T>>`。
2. `PageResult` 中包含：
   - `list`（当前页数据）
   - `total`（总条数）
   - `page`（当前页）
   - `pageSize`（每页大小）
3. 第 1 页与第 2 页数据不重复。
4. 当 `page` 越界时，`list` 为空但 `total` 保持正确。
5. 不传分页参数时使用默认值，不出现全量返回。

---

## 4. 分接口执行步骤与预期

### 4.1 AdminTaskOps 三个任务列表
1. 依次调用：
   - `/admin/ops/tasks/ship-timeout?page=1&pageSize=20`
   - `/admin/ops/tasks/refund?page=1&pageSize=20`
   - `/admin/ops/tasks/ship-reminder?page=1&pageSize=20`
2. 再调用相同接口 `page=2&pageSize=20`。
3. 预期：
   - 返回 `PageResult`，`total` 为任务总数；
   - 第 1 页和第 2 页记录不重复；
   - 排序语义保持（按 `id desc`）。
4. 参数边界校验：
   - `page=0` 时回退到默认 `page=1`；
   - `pageSize=0` 时回退默认值（任务接口默认 50）；
   - `pageSize>100` 时按上限 100 执行。

### 4.2 Credit 日志（用户端 + 管理端）
1. 用户端调用：`/user/credit/logs?page=1&pageSize=20`
2. 管理端调用：`/admin/credit/logs?userId={userId}&page=1&pageSize=20`
3. 对比旧逻辑（limit 截断）关注点：
   - 现在由 DB 层分页，非“查全量后内存截断”；
   - `total` 应返回该用户完整日志总数。
4. 预期：
   - 用户端、管理端同 userId 下 `total` 一致；
   - `page/pageSize` 生效，支持翻页；
   - 默认 `pageSize=20`，最大不超过 100。

### 4.3 商品违规记录分页
1. 调用：`/admin/products/{productId}/violations?page=1&pageSize=20`
2. 调用同接口 `page=2&pageSize=20`。
3. 预期：
   - 返回 `PageResult<ProductViolation>`；
   - 仅返回该商品 `active` 违规记录；
   - 排序按 `id desc`，分页稳定。

### 4.4 用户地址分页
1. 调用：`/user/addresses?page=1&pageSize=20`
2. 再调用：`/user/addresses?page=2&pageSize=20`
3. 预期：
   - 返回 `PageResult<AddressVO>`；
   - 排序语义不变：`is_default desc, updated_at desc`；
   - 默认地址仍优先出现在前面。

---

## 5. SQL 观察点（建议在 dev 环境）
1. 开启 SQL 日志后，验证上述接口均出现分页 SQL（`LIMIT offset, pageSize`）。
2. 重点确认以下旧高风险路径已收口：
   - Credit 日志不再直接走全量 `listByUserId`；
   - 任务列表不再仅靠单一 `limit` 返回；
   - Address / ProductViolation 不再默认全量返回。

---

## 6. DoD 验收勾选
- [ ] 列表接口 100% 支持 `page/pageSize`
- [ ] 统一返回 `PageResult`
- [ ] 无默认全量查询接口暴露给业务层
- [ ] 高风险查询已改为数据库分页
- [ ] 编译校验通过（已通过：IDEA 内置 Maven `-pl demo-service -am -DskipTests compile`）

---

（文件结束）
