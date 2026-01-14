# Day10 信用体系政策文档

## 分数范围
- 最小值：0
- 最大值：200
- 默认值：100（对应信用等级 LV3）

## 信用等级阈值（闭区间 [minScore, maxScore]）
- **LV1（等级1）**：0 ~ 39
- **LV2（等级2）**：40 ~ 79
- **LV3（等级3）**：80 ~ 119
- **LV4（等级4）**：120 ~ 159
- **LV5（等级5）**：160 ~ 200

## 信用分变动规则（MVP 常量）
- 订单完成：+2
- 订单取消：-3
- 用户违规：-10
- 商品违规：-5
- 封禁生效：-30

## 变更原因类型
- `order_completed`：订单完成
- `order_cancelled`：订单取消
- `user_violation`：用户违规
- `product_violation`：商品违规
- `ban_active`：封禁生效
- `admin_adjust`：管理员调整
- `recalc`：重算/对账

## Day12 扩展点
引入"评价"后，可追加维度：好评率、纠纷次数、退货/退款等。
建议做法：新增统计来源与策略后，仍保持 CreditLevel 映射不轻易变动。
若要变动阈值，必须同步更新：CreditLevel + CreditConstants + 本文档。

