package com.demo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Day13 Step7 - 异常订单标记
 * 表：order_flags
 */
@Data
@TableName("order_flags")
public class OrderFlag {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long orderId;

    /**
     * 标记类型：suspicious / refund_risk / other
     */
    private String type;

    /**
     * 备注说明
     */
    private String remark;

    /**
     * 创建人（管理员 ID）
     */
    private Long createdBy;

    private LocalDateTime createTime;
}
