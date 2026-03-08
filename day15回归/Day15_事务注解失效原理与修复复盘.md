# Day15 事务注解失效原理与修复复盘（`@Transactional` on `processOne`）

- 复盘日期：2026-02-10
- 关联代码：
  - 原问题点：`demo-service/src/main/java/com/demo/service/serviceimpl/OrderShipTimeoutServiceImpl.java`
  - 修复后：`demo-service/src/main/java/com/demo/service/serviceimpl/OrderShipTimeoutTaskProcessor.java`

---

## 1. 现象（你看到的报错）
IDE 报告类似：

- `使用 '@Transactional' 注解的方法必须可重写`
- 并提示把 `processOne()` 改成 `public`

这不是“语法错”，而是 Spring 事务代理机制的有效性提示：**当前写法很可能导致事务不生效**。

---

## 2. 根因（底层机制）
Spring 声明式事务（`@Transactional`）默认是通过 **AOP 代理** 实现的，不是 JVM 原生事务魔法。

简化理解：

1. 外部调用 `Bean` 时，实际先进入 Spring 代理对象  
2. 代理对象判断方法有没有事务注解  
3. 有则开启事务，再调用目标方法  
4. 方法结束后提交/回滚

关键点：**必须“经过代理”调用，事务才会生效**。

---

## 3. 为什么你原来那段代码会失效
你原本是在同一个类里：

- `processDueTasks(...)` 循环调用 `processOne(...)`
- `processOne(...)` 上标了 `@Transactional`

问题在于：

1. 同类内调用本质是 `this.processOne(...)`
2. `this` 直接调用目标对象方法，不会经过 Spring 代理
3. 代理没介入，`@Transactional` 就不会触发

也就是说，虽然注解写上了，但运行时可能等于没写。

---

## 4. 为什么 IDE 还强调“方法要可重写/最好 public”
你的项目基线是 Spring Boot `2.7.3`（Spring Framework 5.3.x）。

在这代默认行为下（代理事务模式）：

1. 事务通常要求可代理方法（非 `private`、非 `final`）
2. 实务上强烈建议使用 `public` 方法承载事务边界
3. 尤其当使用 JDK 动态代理时，常见只代理接口 `public` 方法

所以 IDE 提示并不是死板规则，而是提醒你“这种写法代理难以稳定拦截”。

---

## 5. 这次采用的正确修复方式
我们把“单条任务事务处理”拆到独立 Bean：

1. 新建 `OrderShipTimeoutTaskProcessor`
2. 在该类中使用 `public boolean processOne(...)` + `@Transactional`
3. 在 `OrderShipTimeoutServiceImpl` 中注入 `taskProcessor` 调用

这样调用路径变成：

- `OrderShipTimeoutServiceImpl`（Bean A） -> `OrderShipTimeoutTaskProcessor`（Bean B）
- 跨 Bean 调用会经过代理
- `@Transactional` 能稳定生效

---

## 6. 为什么这个修复比“只改 public”更稳
仅把同类方法改 `public` 并不能解决“同类内调用绕过代理”问题。

所以最稳方案是：

1. 拆分到另一个 Spring Bean
2. 由外部 Bean 调用事务方法

这不仅修复本次问题，也让事务边界更清晰（单条任务一个事务）。

---

## 7. 事务失效排查清单（以后通用）
看到“事务没回滚/没提交”时，按这个顺序查：

1. 调用是否经过 Spring Bean 代理（不是 `new` 出来的对象）
2. 是否同类内 `this.xxx()` 调用事务方法
3. 方法可见性是否合适（优先 `public`）
4. 方法/类是否被 `final` 限制（影响代理）
5. 异常类型是否触发回滚（默认运行时异常；你这里用 `rollbackFor=Exception.class` 已覆盖）
6. 方法是否被 Spring 扫描到（`@Service`、包路径等）

---

## 8. 本次业务层面的收益
修复后你的“超时关单 + 释放商品 + 任务状态更新”可以稳定在单条事务内完成，避免：

1. 订单已取消但商品未释放
2. 商品释放了但任务状态未更新
3. 异常时半成功半失败的脏状态

这对 Day15 的“发货超时自动处理”是关键基础。

---

（文件结束）
