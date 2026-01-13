# Day10：信用体系政策文档（MVP）

本文件用于冻结 Day10「信用分/信用等级」对外口径，避免后续迭代（Day11 收藏、Day12 评价、Day13 风控）时产生返工。

## 1. 分数范围

- 最小值：0
- 最大值：200
- 默认值：100（对应信用等级 **LV3 / lv3**）

## 2. 信用等级阈值

等级阈值采用**闭区间** `[minScore, maxScore]`：

| Level | dbValue | 分数区间 |
|------|--------|---------|
| LV1  | lv1    | 0 ~ 39  |
| LV2  | lv2    | 40 ~ 79 |
| LV3  | lv3    | 80 ~ 119 |
| LV4  | lv4    | 120 ~ 159 |
| LV5  | lv5    | 160 ~ 200 |

说明：
- `CreditLevel.fromScore(score)` 的边界处理：
    - `score < 0` 视为 LV1
    - `score > 200` 视为 LV5
- `creditLevel` 的数据库存储值统一使用 `dbValue`（全小写，如 `lv3`）。

## 3. 信用分变动规则（MVP 常量）

本阶段只冻结“最小可用”的计分项，具体业务接入在后续步骤完成。

| 事件 | delta |
|------|-------|
| 订单完成（ORDER_COMPLETED） | +2 |
| 订单取消（ORDER_CANCELLED） | -3 |
| 用户违规（USER_VIOLATION） | -10 |
| 商品违规（PRODUCT_VIOLATION，预留） | -5 |
| 封禁生效（BAN_ACTIVE） | -30 |

> 注意：本次只冻结常量，暂不落地“责任归因”（如买家取消/卖家取消/超时关闭）。责任归因与扣分力度将在 Day10 业务接入时再细化。

## 4. 变更原因类型（dbValue）

| 枚举 | dbValue | 说明 |
|------|--------|------|
| ORDER_COMPLETED | order_completed | 订单完成 |
| ORDER_CANCELLED | order_cancelled | 订单取消 |
| USER_VIOLATION | user_violation | 用户违规 |
| PRODUCT_VIOLATION | product_violation | 商品违规 |
| BAN_ACTIVE | ban_active | 封禁生效 |
| ADMIN_ADJUST | admin_adjust | 管理员调整 |
| RECALC | recalc | 重算/对账 |

## 5. Day12 扩展点（评价）

引入“评价”后，可追加维度：
- 好评率/差评率
- 纠纷/投诉次数
- 退货/退款次数

建议做法：
1) 先新增统计来源与策略，再决定是否调整阈值。
2) 若要变动阈值，必须同步更新：`CreditLevel` + `CreditConstants` + 本文档。
