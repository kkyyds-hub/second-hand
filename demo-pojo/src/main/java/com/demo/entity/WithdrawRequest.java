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
    private Long id;

    private Long userId;

    private BigDecimal amount;

    /**
     * 状态：APPLIED / APPROVED / REJECTED / PAID
     */
    private String status;

    /**
     * 银行卡号（可脱敏）
     */
    private String bankCardNo;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
