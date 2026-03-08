# Day17 P5-S4 MP 使用规范 v1.0

- 日期：2026-02-24  
- 目标：统一 “MyBatis-Plus + 传统 MyBatis XML” 的工程边界，降低维护分歧。  
- 适用范围：`demo-service` 全量数据访问代码。

---

## 1. 核心结论（先看这个）

1. **默认用 MP**：单表 CRUD、简单条件查询、常规分页。  
2. **保留 XML**：复杂联表、聚合统计、强执行计划控制、高风险语义 SQL。  
3. **分页禁止混用**：单链路只能选 MP 分页或 PageHelper 之一。  
4. **返回统一 `PageResult`**：对外口径一致，便于前端/测试回归。  
5. **自动填充统一服务端时间**：`createTime/updateTime` 不依赖调用方传值。

---

## 2. 当前统一配置口径

## 2.1 配置入口

1. `demo-service/src/main/java/com/demo/config/MybatisPlusConfig.java`  
2. `demo-service/src/main/java/com/demo/config/PaginationMixGuardInterceptor.java`  
3. `demo-service/src/main/java/com/demo/config/AuditMetaObjectHandler.java`  
4. `demo-service/src/main/resources/application.yml`

## 2.2 固定参数（冻结）

1. MP 分页单页上限：`100`  
2. 页码越界策略：`overflow=false`（不回到第一页）  
3. 驼峰映射：`map-underscore-to-camel-case=true`  
4. Mapper 扫描路径：`classpath*:mapper/**/*.xml`  
5. SQL 日志口径：默认 `info`，仅 `dev` 打开 mapper `debug`

---

## 3. 何时用 MP，何时保留 XML

## 3.1 MP 适用（必须优先评估）

1. `BaseMapper<T>` 能覆盖的读写。  
2. `lambdaQuery()` / `LambdaQueryWrapper` 可表达的条件查询。  
3. 标准分页（`Page<T>`）与常规排序。  
4. 批量写（`saveBatch` / `updateBatchById`）可满足语义。

## 3.2 XML 保留（明确允许）

1. 多表 Join + 复杂筛选 + 子查询。  
2. 依赖特定索引 Hint 或稳定执行计划。  
3. 任务抢占 SQL（`UPDATE ... ORDER BY ... LIMIT`）这类强语义写法。  
4. CAS 状态推进 SQL 需要显式 `expectedStatus` 条件。  
5. 迁移收益明显低于风险的历史稳定查询。

---

## 4. 编码规则（强约束）

## 4.1 分页规则

1. 单链路只允许一种分页机制。  
2. 新接口优先 MP 分页。  
3. 统一返回 `PageResult<T>`。  
4. `pageSize` 必须有上限（当前统一 100）。

## 4.2 条件查询规则

1. 简单条件查询优先 `LambdaQueryWrapper`，避免手写列名字符串。  
2. 排序必须显式写出，禁止依赖“自然顺序”。  
3. 列表查询默认必须有分页参数，不允许无边界全量返回。

## 4.3 写入规则

1. 审计字段不手工赋值，交给自动填充器。  
2. 状态流转/任务推进优先 CAS 更新（`id + expectedStatus`）。  
3. 批量操作优先批量 SQL，不在循环里单条 update/insert。  
4. 重复请求场景必须有唯一键兜底。

## 4.4 事务规则

1. 跨表写入方法必须标注事务边界。  
2. 事务内不直接发 MQ，统一 Outbox。  
3. 通知动作优先 `afterCommit`，避免回滚后消息已发。

---

## 5. 推荐模板（新模块接入）

## 5.1 Mapper 模板

1. `XxxMapper extends BaseMapper<Xxx>`  
2. 简单查询走 MP；复杂查询补充 XML，并标注用途/索引依赖。  
3. 保留 XML 方法必须有“不迁移原因”注释。

## 5.2 Service 模板

1. `XxxServiceImpl extends ServiceImpl<XxxMapper, Xxx>`  
2. 单表 CRUD 调用通用方法；复杂编排放在 Service 层。  
3. 批量聚合场景采用“批量查询 + 内存 Map 回填”。

## 5.3 实体模板

1. 公共审计字段优先继承 `BaseAuditEntity`。  
2. `@TableField(fill = INSERT/INSERT_UPDATE)` 明确填充策略。  
3. DB 列名下划线，Java 字段驼峰。

---

## 6. 禁止项（代码评审直接驳回）

1. 同一请求链路同时使用 PageHelper 与 MP 分页。  
2. 在循环中调用单条查询形成 N+1。  
3. 新增接口返回全量列表且无分页。  
4. 状态更新缺少预期状态条件（无 CAS 防护）。  
5. 跨表写入主流程直接发 MQ（绕过 Outbox）。

---

## 7. 评审检查清单（MR/PR）

1. 是否声明该接口走 MP 还是 XML？  
2. 是否有分页与 `pageSize` 上限？  
3. 是否存在循环内 SQL（潜在 N+1）？  
4. 是否有 CAS 更新与影响行数=0分流？  
5. 是否有幂等键与唯一约束支撑？  
6. 是否补充 SQL/索引/注释说明可供接手者复现？

---

## 8. 与 Day17 既有产物的关系

1. P1：统一配置、自动填充、分页混用守卫。  
2. P2：试点迁移与复杂 SQL 保留边界。  
3. P3：分页全覆盖、批量写、索引与 N+1 治理。  
4. P4：事务边界、幂等、并发 CAS、异步一致性。  
5. P5：回归与质量门禁，为本规范提供落地证据。

---

（文件结束）
