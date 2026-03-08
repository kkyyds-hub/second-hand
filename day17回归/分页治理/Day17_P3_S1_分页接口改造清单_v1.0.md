# Day17 P3-S1 分页接口改造清单 v1.0

## 1. 目标与统一口径

- 目标：列表接口 100% 支持 `page/pageSize`，统一返回 `PageResult`，收口高风险大查询。
- 统一规则：
  - 统一入参：`page`（默认 1），`pageSize`（默认按接口约定，最大 100）。
  - 统一返回：`Result<PageResult<T>>`。
  - 统一约束：禁止业务层直接暴露“无边界全量列表接口”。
  - 统一实现：XML 链路采用 `count + limit offset`，避免分页插件混用。

## 2. 风险分级与改造优先级（高 -> 低）

| 优先级 | 模块 | 现状问题 | 风险级别 | 改造策略 |
|---|---|---|---|---|
| P0 | AdminTaskOps 任务列表（3个） | 仅 `limit`，无页码与总量；任务表随时间增长 | 高 | 增加 `countForAdmin + listForAdmin(offset,pageSize)`，接口返回 `PageResult` |
| P0 | Credit 日志列表（用户/管理端） | 服务层先查全量 `listByUserId` 再截断 | 高 | 改为 DB 分页查询：`countByUserId + listByUserIdPage`，返回 `PageResult` |
| P1 | ProductViolation 列表 | 按商品查全量 active 记录 | 中 | 增加按商品分页查询与总数查询，接口返回 `PageResult` |
| P2 | Address 列表 | 按用户查全量地址 | 低 | 增加分页查询；保留现有排序语义（默认地址优先 + 更新时间倒序） |

## 3. 改造清单（文件级）

### 3.1 Controller 层

- `demo-service/src/main/java/com/demo/controller/admin/AdminTaskOpsController.java`
- `demo-service/src/main/java/com/demo/controller/user/UserCreditController.java`
- `demo-service/src/main/java/com/demo/controller/admin/AdminCreditController.java`
- `demo-service/src/main/java/com/demo/controller/admin/ProductController.java`
- `demo-service/src/main/java/com/demo/controller/user/AddressController.java`

### 3.2 Service 层

- `demo-service/src/main/java/com/demo/service/CreditService.java`
- `demo-service/src/main/java/com/demo/service/serviceimpl/CreditServiceImpl.java`
- `demo-service/src/main/java/com/demo/service/AddressService.java`
- `demo-service/src/main/java/com/demo/service/serviceimpl/AddressServiceImpl.java`
- `demo-service/src/main/java/com/demo/service/ProductService.java`
- `demo-service/src/main/java/com/demo/service/serviceimpl/ProductServiceImpl.java`

### 3.3 Mapper 层（Java + XML）

- `demo-service/src/main/java/com/demo/mapper/OrderShipTimeoutTaskMapper.java`
- `demo-service/src/main/java/com/demo/mapper/OrderRefundTaskMapper.java`
- `demo-service/src/main/java/com/demo/mapper/OrderShipReminderTaskMapper.java`
- `demo-service/src/main/resources/mapper/OrderShipTimeoutTaskMapper.xml`
- `demo-service/src/main/resources/mapper/OrderRefundTaskMapper.xml`
- `demo-service/src/main/resources/mapper/OrderShipReminderTaskMapper.xml`
- `demo-service/src/main/java/com/demo/mapper/UserCreditLogMapper.java`
- `demo-service/src/main/resources/mapper/UserCreditLogMapper.xml`
- `demo-service/src/main/java/com/demo/mapper/AddressMapper.java`
- `demo-service/src/main/resources/mapper/AddressMapper.xml`
- `demo-service/src/main/java/com/demo/mapper/ProductViolationMapper.java`
- `demo-service/src/main/resources/mapper/ProductViolationMapper.xml`

## 4. 执行状态

- [x] 清点现状与风险分级完成
- [x] P0：AdminTaskOps 分页化（3 个任务列表接口改为 `PageResult` + `count + offset`）
- [x] P0：Credit 日志分页化（服务层由“查全量后截断”改为 DB 分页）
- [x] P1：ProductViolation 分页化（新增商品维度 `count/page` 查询）
- [x] P2：Address 分页化（保留排序语义，新增用户维度分页）
- [ ] 编译级静态校验与文档回填

## 4.1 已落地接口（P3-S1）

- `GET /admin/ops/tasks/ship-timeout`：`page/pageSize` + `PageResult<OrderShipTimeoutTask>`
- `GET /admin/ops/tasks/refund`：`page/pageSize` + `PageResult<OrderRefundTask>`
- `GET /admin/ops/tasks/ship-reminder`：`page/pageSize` + `PageResult<OrderShipReminderTask>`
- `GET /user/credit/logs`：`page/pageSize` + `PageResult<UserCreditLogDTO>`
- `GET /admin/credit/logs`：`page/pageSize` + `PageResult<UserCreditLogDTO>`
- `GET /admin/products/{productId}/violations`：`page/pageSize` + `PageResult<ProductViolation>`
- `GET /user/addresses`：`page/pageSize` + `PageResult<AddressVO>`

## 5. DoD 对齐点

- 所有列表接口支持 `page/pageSize`。
- 所有列表接口返回 `PageResult`。
- 无默认全量查询接口暴露给业务层。
- 关键高风险查询由“查全量再截断”改为“数据库分页”。
