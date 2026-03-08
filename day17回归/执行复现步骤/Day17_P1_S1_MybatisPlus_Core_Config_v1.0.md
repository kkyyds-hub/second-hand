# Day17 Step P1-S1：MyBatis-Plus 核心配置统一说明

- 文档版本：v1.0
- 日期：2026-02-24
- 目标：统一 MP 配置入口，固化分页与安全基线，明确分页插件单链路不混用规则。

---

## 1. 改动文件清单
1. `demo-service/src/main/java/com/demo/config/MybatisPlusConfig.java`
2. `demo-service/src/main/java/com/demo/config/PaginationMixGuardInterceptor.java`
3. `demo-service/src/main/resources/application.yml`
4. `demo-service/src/main/resources/application-dev.yml`

---

## 2. 统一配置口径
### 2.1 MP 插件链（统一入口）
在 `MybatisPlusConfig` 中统一注册：
1. `BlockAttackInnerInterceptor`
   - 作用：拦截无条件 `UPDATE/DELETE`，防止误操作全表数据。
2. `PaginationInnerInterceptor(DbType.MYSQL)`
   - `maxLimit=100`：单页最大 100。
   - `overflow=false`：页码越界不自动回第一页。

说明：
1. 分页插件放在 MP 插件链最后，遵循官方建议，避免与其他插件处理顺序冲突。
2. 分页参数限制属于基础设施规则，不在业务层重复硬编码。

### 2.2 MyBatis 基础配置
在 `application.yml` 固化：
1. `mybatis-plus.mapper-locations=classpath*:mapper/**/*.xml`
2. `mybatis-plus.configuration.map-underscore-to-camel-case=true`
3. `mybatis-plus.configuration.log-impl=org.apache.ibatis.logging.slf4j.Slf4jImpl`

说明：
1. `classpath*:` + `**/*.xml` 适配当前与后续分目录 mapper 布局。
2. SQL 日志通过 Slf4j 进入统一日志体系，避免 stdout 直出。

### 2.3 日志级别口径
1. `application.yml`（默认环境）：
   - `com.demo.mapper=info`
   - `com.demo.service=info`
   - `com.demo.controller=info`
   - `org.springframework.web=info`
2. `application-dev.yml`（开发环境）：
   - `com.demo.mapper=debug`（仅 dev 打开 SQL 细粒度日志）

---

## 3. 项目级约束：分页单链路不混用
### 3.1 约束规则
同一请求链路中，禁止同时使用：
1. `PageHelper.startPage(...)`
2. MP 分页参数（`IPage` / `Page<?>`）

命中后直接抛错（`error`）并中断查询。

### 3.2 实现方式
`PaginationMixGuardInterceptor` 在 MyBatis `Executor.query` 阶段执行：
1. 检测 PageHelper 本地上下文是否已开启。
2. 检测当前参数是否包含 MP 分页对象。
3. 同时命中则抛出 `IllegalStateException`，错误信息带 `mapperId`。

### 3.3 正确示例
1. 纯 MP 分页：
   - 不调用 `PageHelper.startPage(...)`
   - Mapper 方法参数使用 `Page<T>`/`IPage<T>`
2. 纯 PageHelper 分页：
   - 调用 `PageHelper.startPage(...)`
   - Mapper 方法参数不传 `IPage/Page`

### 3.4 错误示例（会被拦截）
1. 先 `PageHelper.startPage(...)`
2. 同一次查询又传入 `Page<T>` 到 Mapper

---

## 4. 本地复现步骤（DoD 对齐）
1. 启动服务，确认应用可正常启动。
2. 执行一个 MP 分页接口（仅 MP 分页），确认返回正常。
   - 可用现有接口：`GET /user/favorites?page=1&pageSize=10`
3. 构造一个混用场景（PageHelper + MP），确认抛出分页混用错误。
4. 在 `dev` 环境观察 SQL 日志，确认 mapper debug 生效；非 dev 环境默认不输出 debug SQL。

---

## 5. 验收结论模板（提交时填写）
1. 应用启动：通过 / 不通过
2. MP 分页接口：通过 / 不通过
3. 混用拦截：通过 / 不通过
4. 配置可复现：通过 / 不通过

（文件结束）
