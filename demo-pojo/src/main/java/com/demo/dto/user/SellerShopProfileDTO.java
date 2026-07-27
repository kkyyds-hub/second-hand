package com.demo.dto.user;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 卖家小店概览 DTO。
 */
@Data
public class SellerShopProfileDTO {

    /** 卖家用户 ID。 */
    private Long sellerId;
    /** 小店名称（运行时计算：nickname + 的小店）。 */
    private String shopName;
    /** 卖家昵称。 */
    private String nickname;
    /** 卖家头像 URL。 */
    private String avatarUrl;
    /** 卖家简介（仅已有真实字段时返回，null 表示未填写）。 */
    private String bio;
    /** 信用分。 */
    private Integer creditScore;
    /** 注册时间。 */
    private LocalDateTime registeredAt;
    /** 当前在售商品数量。 */
    private Long onSaleCount;
    /** 已售商品数量。 */
    private Long soldCount;
    /** 已完成订单数量。 */
    private Long completedOrderCount;
    /** 当前登录用户是否为此卖家本人。 */
    private Boolean isCurrentUser;
}
