package com.demo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Day13 Step5 - 售后主表
 * 表：after_sales
 */
@Data
@TableName("after_sales")
public class AfterSale {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long orderId;
    private Long buyerId;
    private Long sellerId;

    /**
     * 退货原因
     */
    private String reason;

    /**
     * 售后状态：APPLIED / SELLER_APPROVED / SELLER_REJECTED / 
     * DISPUTED / PLATFORM_APPROVED / PLATFORM_REJECTED / CLOSED
     */
    private String status;

    /**
     * 卖家备注
     */
    private String sellerRemark;

    /**
     * 平台备注
     */
    private String platformRemark;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
