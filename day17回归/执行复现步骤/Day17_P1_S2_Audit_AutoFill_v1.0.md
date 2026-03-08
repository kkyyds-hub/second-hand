# Day17 Step P1-S2：全局字段与自动填充能力落地说明

- 文档版本：v1.0
- 日期：2026-02-24
- 目标：统一 `create_time/update_time` 写入规则，减少业务层手工赋值。

---

## 1. 本次改动文件
1. `demo-pojo/src/main/java/com/demo/entity/BaseAuditEntity.java`
2. `demo-pojo/src/main/java/com/demo/entity/Favorite.java`
3. `demo-pojo/src/main/java/com/demo/entity/Product.java`
4. `demo-pojo/src/main/java/com/demo/entity/User.java`
5. `demo-pojo/src/main/java/com/demo/entity/ProductReportTicket.java`
6. `demo-service/src/main/java/com/demo/config/AuditMetaObjectHandler.java`
7. `demo-service/src/main/java/com/demo/service/serviceimpl/ProductServiceImpl.java`
8. `demo-service/src/main/java/com/demo/mapper/UserMapper.java`
9. `demo-service/src/main/resources/mapper/UserMapper.xml`
10. `demo-service/src/main/java/com/demo/service/serviceimpl/UserServiceImpl.java`
11. `demo-service/src/main/java/com/demo/service/serviceimpl/AuthServiceImpl.java`
12. `demo-service/src/main/java/com/demo/service/serviceimpl/ViolationServiceImpl.java`

---

## 2. 现状盘点结论（按写入链路分类）
### 2.1 MP 链路（可自动填充）
1. 当前确认的 `BaseMapper` 链路：`FavoriteMapper extends BaseMapper<Favorite>`。
2. 该链路已接入自动填充，可由 `MetaObjectHandler` 统一写时间字段。

### 2.2 XML 手写 SQL 链路（先兼容）
1. 大量历史模块仍是 XML 手写 SQL。
2. 本次不强制全量改造，只做必要收敛：`UserMapper.xml` 更新语句统一改为 `update_time = NOW()`，插入改为 `create_time/update_time = NOW()`。
3. 其余历史 XML 保持兼容，后续按模块逐步收敛。

---

## 3. 字段规范统一
### 3.1 命名口径
1. Java 侧统一：`createTime` / `updateTime`。
2. DB 侧统一：`create_time` / `update_time`。

### 3.2 基类抽取
新增 `BaseAuditEntity`，统一审计字段和注解：
1. `createTime`：`@TableField(value = "create_time", fill = FieldFill.INSERT)`
2. `updateTime`：`@TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)`

已继承基类实体：
1. `Favorite`
2. `Product`
3. `User`
4. `ProductReportTicket`

---

## 4. 自动填充策略
### 4.1 实现
新增 `AuditMetaObjectHandler`：
1. `insertFill`：同时写 `createTime`、`updateTime`
2. `updateFill`：刷新 `updateTime`

### 4.2 规则
1. 服务端主导时间：填充时以服务端 `LocalDateTime.now()` 为准。
2. 插入与更新的时间口径保持一致，避免外部请求传值干扰。

---

## 5. 手工赋值清理（本次收敛）
### 5.1 已清理
1. `ProductServiceImpl`：移除 `product.setUpdateTime(LocalDateTime.now())`。
2. `UserServiceImpl`：移除 `user.setUpdateTime(...)` 等手工赋值。
3. `AuthServiceImpl`：基础建模不再手工 `setCreateTime/setUpdateTime`。

### 5.2 SQL 侧兜底
1. `UserMapper.xml` 的 `updateStatus/updateProfile/updatePassword/updateMobile/updateEmail` 统一 `update_time = NOW()`。
2. `insertUser` 统一 `create_time = NOW()`、`update_time = NOW()`。
3. `UserMapper` 方法签名同步去掉 `updateTime` 参数，避免继续从业务层传时间。

---

## 6. 写入约束（执行规范）
1. MP 链路优先使用：`save/saveBatch/updateById/update(entity, wrapper)`。
2. 避免 `update(wrapper)` 这类无实体更新（自动填充可能不触发）。
3. XML 链路暂保留 `NOW()` 兜底，后续按模块迁移到 MP 自动填充。

---

## 7. DoD 验证步骤（复现）
1. 执行一条 MP 插入（如 `favorites` 新增）：
   - 期望：`create_time`、`update_time` 自动写入。
2. 执行一条 MP 更新（如收藏取消/恢复）：
   - 期望：`update_time` 自动刷新。
3. 执行一条 `users` 更新（XML 链路）：
   - 期望：即使不传 `updateTime` 参数，`update_time` 仍由 SQL `NOW()` 写入。
4. 检查业务代码：
   - 期望：主要实体链路不再重复 `setUpdateTime/setCreateTime`。

---

## 8. 本阶段边界说明
1. 本次是 P1-S2 的“必要范围收敛”，不做全库全模块一次性改造。
2. 历史 XML 中未改造模块保持兼容，避免引入大规模回归风险。
3. 后续阶段再按模块推进 XML -> MP 的增量迁移。

（文件结束）
