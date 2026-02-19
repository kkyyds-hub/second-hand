package com.demo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Day13 Step8 - 提现申请
 * 表：withdraw_requests
 */
@Data
@TableName("withdraw_requests")
public class WithdrawRequest {

    @TableId(type = IdType.AUTO)
    /** 主键 ID。 */
    private Long id;

    /** 用户 ID。 */
    private Long userId;

    /** 字段：amount。 */
    private BigDecimal amount;

    /**
     * 状态：APPLIED / APPROVED / REJECTED / PAID
     */
    private String status;

    /**
     * 银行卡号（可脱敏）
     */
    private String bankCardNo;

    /** 创建时间。 */
    private LocalDateTime createTime;
    /** 更新时间。 */
    private LocalDateTime updateTime;
}
