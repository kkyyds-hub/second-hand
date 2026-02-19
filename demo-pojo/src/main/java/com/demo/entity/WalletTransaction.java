package com.demo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Day13 Step8 - 余额流水
 * 表：wallet_transactions
 */
@Data
@TableName("wallet_transactions")
public class WalletTransaction {

    @TableId(type = IdType.AUTO)
    /** 主键 ID。 */
    private Long id;

    /** 用户 ID。 */
    private Long userId;

    /**
     * 业务类型：ORDER_PAY / ORDER_REFUND / WITHDRAW / ADJUST
     */
    private String bizType;

    /**
     * 业务 ID（如 orderId）
     */
    private Long bizId;

    /**
     * 金额（正=入账，负=出账）
     */
    private BigDecimal amount;

    /**
     * 操作后余额
     */
    private BigDecimal balanceAfter;

    /**
     * 备注
     */
    private String remark;

    /** 创建时间。 */
    private LocalDateTime createTime;
}
