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
    private Long userId;

    private BigDecimal balance;

    private LocalDateTime updateTime;
}
