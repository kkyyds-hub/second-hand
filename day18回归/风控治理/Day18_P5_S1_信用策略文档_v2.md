# Day18 P5-S1 信用策略文档 v2

- 日期：2026-02-25
- 对应阶段：`Step P5-S1：信用评分与策略收口`
- 目标：把“信用分怎么算、从哪来、如何限制卖家能力”固化为可审计、可复核、可交接的统一口径。

---

## 1. 口径冻结（本版结论）

1. 信用分计算采用“默认分 + 统计项重算 + 边界夹紧”的模式。
2. 信用等级阈值统一由 `CreditLevel` 枚举维护，使用闭区间映射。
3. 卖家能力限制当前只在“创建商品”链路生效：
   - LV1：禁止创建商品；
   - LV2：允许创建，但活跃商品（`under_review + on_sale`）上限为 3；
   - LV3/LV4/LV5：不施加额外发布限制。

---

## 2. 信用分计算规则与阈值

## 2.1 计算公式（源码口径）

```text
score_raw = DEFAULT_SCORE
          + sumAdminAdjustDelta
          + (countCompletedAsBuyer + countCompletedAsSeller) * DELTA_ORDER_COMPLETED
          + countCancelledAsBuyer * DELTA_ORDER_CANCELLED
          + sumViolationCreditDelta
          + (countActiveBans > 0 ? DELTA_BAN_ACTIVE : 0)

score_final = clamp(score_raw, SCORE_MIN, SCORE_MAX)
credit_level = CreditLevel.fromScore(score_final)
```

## 2.2 常量冻结

| 常量 | 值 | 说明 |
|---|---:|---|
| `DEFAULT_SCORE` | 100 | 默认信用分 |
| `SCORE_MIN` | 0 | 最小分 |
| `SCORE_MAX` | 200 | 最大分 |
| `DELTA_ORDER_COMPLETED` | +2 | 订单完成加分 |
| `DELTA_ORDER_CANCELLED` | -3 | 买家取消扣分 |
| `DELTA_USER_VIOLATION` | -10 | 用户违规扣分 |
| `DELTA_PRODUCT_VIOLATION` | -5 | 商品违规扣分（常量预留） |
| `DELTA_BAN_ACTIVE` | -30 | 有效封禁扣分 |

## 2.3 等级阈值冻结（闭区间）

| 等级 | dbValue | 分数区间 |
|---|---|---|
| LV1 | `lv1` | 0 ~ 39 |
| LV2 | `lv2` | 40 ~ 79 |
| LV3 | `lv3` | 80 ~ 119 |
| LV4 | `lv4` | 120 ~ 159 |
| LV5 | `lv5` | 160 ~ 200 |

---

## 3. 来源字段追溯（DoD-1）

| 统计维度 | 来源表/字段 | 统计口径 | 触发入口（写入来源） |
|---|---|---|---|
| 管理员调分累计 | `user_credit_logs.delta` | `reason_type = admin_adjust` 求和 | `POST /admin/credit/adjust` 插入 `user_credit_logs` 后触发重算 |
| 买家完成单量 | `orders.status` + `orders.buyer_id` | `status=completed` 且 `buyer_id=userId` 计数 | 买家确认收货成功后，触发买卖双方重算 |
| 卖家完成单量 | `orders.status` + `orders.seller_id` | `status=completed` 且 `seller_id=userId` 计数 | 同上 |
| 买家取消单量 | `orders.status` + `orders.buyer_id` | `status=cancelled` 且 `buyer_id=userId` 计数 | 买家主动取消、超时关单后触发买家重算 |
| 违规扣分累计 | `user_violations.credit` | `user_id=userId` 求和 | 管理端违规上报、发货超时处罚写入违规表并触发重算 |
| 当前有效封禁 | `user_bans.start_time/end_time` | `start_time<=now && (end_time is null || end_time>now)` 计数 | 管理封禁/自动风控冻结写入封禁记录并触发重算 |
| 信用快照 | `users.credit_score/credit_level/credit_updated_at` | 每次重算后覆盖写 | `CreditServiceImpl.recalcUserCredit` 统一写回 |

结论：信用分来源字段已可逐项追溯到表、字段、统计 SQL 与业务触发入口。

---

## 4. 信用等级与卖家能力限制映射（DoD-2）

| 等级 | 卖家创建商品能力 | 判定条件 | 返回语义 |
|---|---|---|---|
| LV1 | 禁止创建 | `level == LV1` | 抛错：`信用等级过低（LV1），暂不可发布商品` |
| LV2 | 允许创建（有上限） | `activeCount < 3` | 超限抛错：`信用等级为 LV2，活跃商品数量已达上限：3` |
| LV3 | 正常创建 | 无额外限制 | 创建成功 |
| LV4 | 正常创建 | 无额外限制 | 创建成功 |
| LV5 | 正常创建 | 无额外限制 | 创建成功 |

活跃商品口径：`products.is_deleted = 0 AND status IN (under_review, on_sale)`。

---

## 5. 一致性复核结论

## 5.1 已通过项

1. 常量、等级阈值、重算逻辑三者一致，未发现阈值漂移。
2. 信用分每个维度都能定位到明确来源字段与统计 SQL。
3. 卖家限制规则与当前实现一致（以“创建商品”作为发布限制入口）。

## 5.2 已知边界与后续建议

1. 当前信用限制只拦截 `createProduct`，`resubmit/onShelf(提审别名)` 未再次校验信用等级。
2. 若后续策略升级为“所有提审/上架动作都受信用等级限制”，需在 `resubmitProduct/onShelfProduct` 同步补限制校验。
3. `PRODUCT_VIOLATION` 已有原因枚举与常量，但当前重算统计口径未接入 `product_violations` 维度（属于后续策略扩展项，不影响本次口径一致性）。

---

## 6. DoD 勾选

- [x] 信用分来源字段可追溯。
- [x] 限制策略与等级映射一致（基于“创建商品限制”边界）。

---

## 7. 代码证据索引

1. `demo-service/src/main/java/com/demo/service/serviceimpl/CreditServiceImpl.java`
2. `demo-service/src/main/resources/mapper/CreditStatMapper.xml`
3. `demo-common/src/main/java/com/demo/constant/CreditConstants.java`
4. `demo-common/src/main/java/com/demo/enumeration/CreditLevel.java`
5. `demo-service/src/main/java/com/demo/controller/admin/AdminCreditController.java`
6. `demo-service/src/main/resources/mapper/UserMapper.xml`
7. `demo-service/src/main/java/com/demo/service/serviceimpl/ProductServiceImpl.java`
8. `demo-service/src/main/resources/mapper/ProductMapper.xml`
9. `demo-service/src/main/java/com/demo/service/serviceimpl/OrderServiceImpl.java`
10. `demo-service/src/main/java/com/demo/service/serviceimpl/OrderTimeoutServiceImpl.java`
11. `demo-service/src/main/java/com/demo/service/serviceimpl/OrderShipTimeoutPenaltyServiceImpl.java`
12. `demo-service/src/main/java/com/demo/service/serviceimpl/ViolationServiceImpl.java`
13. `demo-service/src/main/java/com/demo/service/serviceimpl/AuthServiceImpl.java`

---

（文件结束）
