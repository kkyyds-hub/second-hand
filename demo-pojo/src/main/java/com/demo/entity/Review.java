package com.demo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("reviews")
/**
 * Review 业务组件。
 */
public class Review {

    @TableId(type = IdType.AUTO)
    /** 主键 ID。 */
    private Long id;

    /** 订单 ID。 */
    private Long orderId;
    /** 商品 ID。 */
    private Long productId;

    /** 买家用户 ID。 */
    private Long buyerId;
    /** 卖家用户 ID。 */
    private Long sellerId;

    /**
     * 1 = BUYER_TO_SELLER（Day12 仅使用该值） 后续可扩展互相评论
     */
    private Integer role;

    /**
     * 1~5
     */
    private Integer rating;

    /** 字段：content。 */
    private String content;

    /**
     * 0=否，1=是
     */
    private Integer isAnonymous;

    @TableLogic(value = "0", delval = "1")
    /** 逻辑删除标记。 */
    private Integer isDeleted;

    /** 创建时间。 */
    private LocalDateTime createTime;
    /** 更新时间。 */
    private LocalDateTime updateTime;
}
