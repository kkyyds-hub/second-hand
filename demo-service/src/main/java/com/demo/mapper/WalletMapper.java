package com.demo.mapper;

import com.demo.entity.UserWallet;
import com.demo.entity.WalletTransaction;
import com.demo.entity.WithdrawRequest;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.math.BigDecimal;

/**
 * Day13 Step8 - 钱包 Mapper
 */
@Mapper
public interface WalletMapper {

    /**
     * 查询用户钱包
     */
    UserWallet selectByUserId(@Param("userId") Long userId);

    /**
     * 查询并锁定钱包行（FOR UPDATE）。
     *
     * 用于退款记账场景，保证“读余额 -> 算新余额 -> 写余额”在并发下的一致性。
     */
    UserWallet selectByUserIdForUpdate(@Param("userId") Long userId);

    /**
     * 初始化用户钱包
     */
    int insertWallet(UserWallet wallet);

    /**
     * 直接更新钱包余额（绝对值写入）。
     */
    int updateBalance(@Param("userId") Long userId, @Param("balance") BigDecimal balance);

    /**
     * 插入余额流水
     */
    int insertTransaction(WalletTransaction transaction);

    /**
     * 查询用户流水（分页由 PageHelper 处理）
     */
    List<WalletTransaction> listTransactionsByUserId(@Param("userId") Long userId);

    /**
     * 插入提现申请
     */
    int insertWithdrawRequest(WithdrawRequest request);

    /**
     * 查询用户提现记录（分页由 PageHelper 处理）
     */
    List<WithdrawRequest> listWithdrawRequestsByUserId(@Param("userId") Long userId);
}
