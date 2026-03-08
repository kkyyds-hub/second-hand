# Day15 回归失败与修复复盘 v1.0

- 复盘日期：2026-02-12
- 范围：Day15 回归（Newman + MQ + 提醒/超时/退款闭环）
- 目标：记录本轮回归中出现的失败、根因、修复方案与防再发措施，便于后续复用。

---

## 1. 最终结果

本轮 Day15 回归已跑通，核心链路可用：

1. 登录鉴权（买家/卖家/管理员）
2. 发货与物流（`paid -> shipped` + mock 轨迹）
3. 发货提醒任务（H24/H6/H1）
4. 超时取消（`paid -> cancelled(ship_timeout)`）
5. 退款任务推进与钱包记账
6. 通知与消息已读链路

---

## 2. 失败清单（按出现顺序）

## 2.1 失败一：Newman 一开始就连不上服务

### 现象

- `connect ECONNREFUSED 127.0.0.1:8080`
- 出现在第一条登录请求前后。

### 根因

- 后端服务未启动（或端口不在 `8080`）。

### 修复

1. 启动服务并确认端口。
2. 确认环境变量 `baseUrl=http://localhost:8080`。

### 验证

- 登录请求返回 `200` 且 `code==1`。

---

## 2.2 失败二：Outbox 发布失败（MQ 发送侧转换异常）

### 现象

- 日志：`Outbox send failed`
- 异常：`MessageConversionException`
- 关键错误：`LocalDateTime not supported by default`
- 指向字段：`EventMessage.occurredAt`

### 根因

- Rabbit 的 `MessageConverter` 没使用项目统一 `JacksonObjectMapper`，导致 `LocalDateTime` 序列化能力不一致。

### 修复

修改文件：

- `demo-service/src/main/java/com/demo/config/RabbitMqConfiguration.java`

修复点：

- `rabbitMessageConverter()` 改为：
  - `new Jackson2JsonMessageConverter(new JacksonObjectMapper())`

### 验证

- Outbox 不再报 `LocalDateTime not supported`。

---

## 2.3 失败三：监听器消费失败（MQ 接收侧时间格式不兼容）

### 现象

- `Execution of Rabbit message listener failed`
- `Failed to convert message`
- `Cannot deserialize LocalDateTime from String "2026-02-08T17:27:58.8573385"`

### 根因

- 项目原有反序列化只接受 `yyyy-MM-dd HH:mm`。
- 消息里出现 ISO 格式（含 `T`、小数秒）导致消费前转换失败，监听方法未执行。

### 修复

新增文件：

- `demo-common/src/main/java/com/demo/json/MultiFormatLocalDateTimeDeserializer.java`

修改文件：

- `demo-common/src/main/java/com/demo/json/JacksonObjectMapper.java`

修复点：

1. LocalDateTime 反序列化改为多格式兼容：
   - `yyyy-MM-dd HH:mm`
   - `yyyy-MM-dd HH:mm:ss`
   - ISO-8601（含小数秒）
2. 兼容偏移时区与 UTC instant 的兜底解析。

### 验证

- MQ 消费不再因 `LocalDateTime` 格式失败。

---

## 2.4 失败四：夹具发现阶段 `code==0` / 找不到 timeout 订单

### 现象

在 `02-Fixture-Discover / Seller List ...` 失败：

1. `code == 1` 断言失败（实际 `code=0`）
2. `fixture paid-timeout exists` 失败

### 根因（多因素叠加）

1. **token 注入脚本 bug**
   - `/user/orders/sell` 被误判走买家 token，而不是卖家 token。
2. **查询条件过窄**
   - 只查 `status=paid`，而 timeout 夹具可能已被定时任务改成 `cancelled`。
3. **分页参数超限**
   - 临时改成 `pageSize=500`，接口限制最大 `100`，返回 `code=0`（每页大小不能超过100）。

### 修复

修改文件：

- `day15回归/Day15_Regression.postman_collection.json`

修复点：

1. 预请求 token 规则补充：
   - `/user/orders/sell` 强制走卖家 token。
2. 夹具发现请求改为：
   - 不再过滤 `status=paid`
   - 使用 `pageSize=100`、按创建时间排序。
3. 断言文案同步更新，避免误导。

### 验证

- `01-Auth + 02-Fixture-Discover` 全绿通过。

---

## 2.5 失败五：消息“全部已读”断言不符合后端语义

### 现象

在 `04-Reminder-Notify / [Seller] List Messages After Read (All Read)` 失败：

- `all messages are read for seller` 断言失败。

### 根因

- 测试断言过严：要求会话内**全部消息**都 `read=true`。
- 实际后端 `markAsRead` 语义是：
  - 仅把 `toUserId = 当前用户` 且 `read=false` 的消息标记已读。
  - 用户自己发出去的消息不属于“未读待签收”模型。

### 修复

修改文件：

- `day15回归/Day15_Regression.postman_collection.json`

修复点：

1. 断言从“全量消息全读”改为“卖家收到的消息（inbound）全读”。
2. 增加 inbound 非空校验，避免空集合误判通过。

### 验证

- 跑到 `04-Reminder-Notify`：`42 assertions / 0 failed`。

---

## 3. 关键技术知识点（本次必须掌握）

1. **Spring AMQP 入参转换时机**
   - 监听方法执行前，框架先把消息体反序列化到方法参数类型。
   - 转换失败时，业务代码不会进入。

2. **序列化策略一致性**
   - HTTP、MQ、Outbox 必须尽量使用同一 `ObjectMapper` 规则。
   - 否则会出现“发送能过、消费失败”或“接口能过、消息报错”。

3. **时间字段契约**
   - `LocalDateTime` 容易因格式差异出问题。
   - 消费端应在迁移期支持多格式，生产端逐步收敛统一格式。

4. **回归脚本的角色语义**
   - 多角色系统（买家/卖家/管理员）必须精确注入 token。
   - 任何“自动推断角色”的脚本都要小心误判。

5. **断言要贴合业务语义**
   - “已读”这种字段需先明确含义（谁的未读、对谁生效），再写断言。

---

## 4. 防再发措施（建议落地）

1. 在回归文档中固定“回归前置顺序”：
   1) 跑 `Day15_Regression_Prepare.sql`
   2) 启服务
   3) 再跑 Newman

2. Newman 脚本中保留三条硬性规范：
   1) `/user/orders/sell` 必走卖家 token
   2) `pageSize <= 100`
   3) 夹具发现不依赖单一状态（避免被调度抢跑）

3. MQ 方向增加持续校验：
   - 关注 `MessageConversionException` 关键字告警。

4. 时间格式规范建议：
   - 中长期统一对外使用 ISO-8601；
   - 兼容层（多格式反序列化）作为迁移缓冲，不应无限扩张。

---

## 5. 相关文件变更索引

1. `demo-service/src/main/java/com/demo/config/RabbitMqConfiguration.java`
2. `demo-common/src/main/java/com/demo/json/JacksonObjectMapper.java`
3. `demo-common/src/main/java/com/demo/json/MultiFormatLocalDateTimeDeserializer.java`
4. `day15回归/Day15_Regression.postman_collection.json`
5. `day15回归/Day15_Regression_脚本覆盖说明_v1.0.md`

---

## 6. 一句话总结

本轮失败的核心不是业务流程本身，而是 **消息转换口径不一致 + 回归脚本角色/断言语义偏差**。  
修复后，Day15 全链路回归已具备稳定重复执行能力。

