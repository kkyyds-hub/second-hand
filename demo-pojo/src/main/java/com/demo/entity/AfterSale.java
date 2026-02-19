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
    /** 主键 ID。 */
    private Long id;

    /** 订单 ID。 */
    private Long orderId;
    /** 买家用户 ID。 */
    private Long buyerId;
    /** 卖家用户 ID。 */
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

    /** 创建时间。 */
    private LocalDateTime createTime;
    /** 更新时间。 */
    private LocalDateTime updateTime;
}
