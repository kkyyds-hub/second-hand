package com.demo.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Day13 Step8 - 用户钱包
 * 表：user_wallets
 */
@Data
@TableName("user_wallets")
public class UserWallet {

    @TableId
    /** 用户 ID。 */
    private Long userId;

    /** 字段：balance。 */
    private BigDecimal balance;

    /** 更新时间。 */
    private LocalDateTime updateTime;
}
