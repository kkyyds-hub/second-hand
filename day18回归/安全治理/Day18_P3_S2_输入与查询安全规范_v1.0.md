# Day18 P3-S2 输入与查询安全规范 v1.0

- 日期：2026-02-25
- 对应阶段：`Step P3-S2：SQL 注入与 XSS 防护收口`
- 目标：在现有代码基线上统一“参数化查询 + 排序白名单 + 文本输入 XSS 守卫”。

---

## 1. 防护基线

1. SQL 构建必须使用 `#{}` 参数化绑定，禁止 `${}` 拼接可控输入。
2. 动态排序必须走白名单，不接受任意列名/表达式。
3. 文本入口按“纯文本策略”处理：`trim + 长度限制 + 拒绝 HTML/脚本片段`。
4. MP 防全表写继续保持开启：`BlockAttackInnerInterceptor`。

---

## 2. 本次收口改造

## 2.1 排序白名单收口（SQL 注入面）

1. 新增统一守卫：`InputSecurityGuard.normalizeSortField/normalizeSortOrder`。
2. 管理端订单查询：
   - `sortField` 仅允许：`createTime` / `payTime`
   - `sortOrder` 仅允许：`asc` / `desc`
3. 用户订单分页查询同口径收口（买家/卖家链路统一走 `pageValidated`）。

## 2.2 文本输入 XSS 收口

1. 新增统一守卫：`InputSecurityGuard.normalizePlainText`。
2. 关键文本入口接入守卫：
   - 用户资料：昵称、简介
   - 认证注册：昵称、第三方登录标识
   - 商品：标题、描述、分类、图片 URL、审核/下架原因
   - 评价：评价内容
   - 站内信：消息内容、客户端幂等键
   - 举报/违规：举报类型、描述、处理备注、处罚结果
   - 管理端订单标记：类型、备注
3. 统一拒绝项：
   - `<` / `>`
   - `javascript:`
   - `onxxx=`

---

## 3. DoD 对齐（P3-S2）

- [x] 不存在动态 SQL 拼接注入入口（Mapper 未使用 `${}`，排序口径白名单化）。  
- [x] 关键富文本/字符串入口具备明确 XSS 处理规则（统一守卫 + 关键入口落地）。  
- [ ] 动态验证记录回填（待服务重启后执行）。  

---

## 4. 代码证据索引

1. `demo-service/src/main/java/com/demo/security/InputSecurityGuard.java`
2. `demo-service/src/main/java/com/demo/controller/admin/AdminOrderController.java`
3. `demo-service/src/main/java/com/demo/service/serviceimpl/OrderServiceImpl.java`
4. `demo-service/src/main/java/com/demo/service/serviceimpl/UserServiceImpl.java`
5. `demo-service/src/main/java/com/demo/service/serviceimpl/AuthServiceImpl.java`
6. `demo-service/src/main/java/com/demo/service/serviceimpl/ProductServiceImpl.java`
7. `demo-service/src/main/java/com/demo/service/serviceimpl/ReviewServiceImpl.java`
8. `demo-service/src/main/java/com/demo/service/serviceimpl/MessageServiceImpl.java`
9. `demo-service/src/main/java/com/demo/service/serviceimpl/ProductReportServiceImpl.java`
10. `demo-service/src/main/java/com/demo/service/serviceimpl/ViolationServiceImpl.java`
11. `demo-service/src/main/java/com/demo/config/MybatisPlusConfig.java`

---

（文件结束）
