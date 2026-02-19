package com.demo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Day13 Step8 - 积分流水
 * 表：points_ledger
 */
@Data
@TableName("points_ledger")
public class PointsLedger {

    @TableId(type = IdType.AUTO)
    /** 主键 ID。 */
    private Long id;

    /** 用户 ID。 */
    private Long userId;

    /**
     * 业务类型：ORDER_COMPLETED
     */
    private String bizType;

    /**
     * 业务 ID（如 orderId）
     */
    private Long bizId;

    /**
     * 积分（正为增加）
     */
    private Integer points;

    /** 创建时间。 */
    private LocalDateTime createTime;
}
