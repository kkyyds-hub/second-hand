package com.demo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("reviews")
public class Review {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long orderId;
    private Long productId;

    private Long buyerId;
    private Long sellerId;

    /**
     * 1 = BUYER_TO_SELLER（Day12 仅使用该值） 后续可扩展互相评论
     */
    private Integer role;

    /**
     * 1~5
     */
    private Integer rating;

    private String content;

    /**
     * 0=否，1=是
     */
    private Integer isAnonymous;

    @TableLogic(value = "0", delval = "1")
    private Integer isDeleted;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
