package com.demo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 购物车项实体。
 *
 * 二手商品口径：
 * 1) 每个购物车项数量固定为 1，不存储 quantity；
 * 2) 同一用户对同一商品只允许一行，由 UNIQUE(user_id, product_id) 保证；
 * 3) 删除即物理删除，不使用逻辑删除标记。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("cart_items")
public class CartItem extends BaseAuditEntity {

    /** 主键 ID。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 购物车归属用户 ID。 */
    private Long userId;

    /** 商品 ID。 */
    private Long productId;
}
