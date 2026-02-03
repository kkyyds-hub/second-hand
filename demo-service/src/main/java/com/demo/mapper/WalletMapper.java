package com.demo.mapper;

import com.demo.entity.UserWallet;
import com.demo.entity.WalletTransaction;
import com.demo.entity.WithdrawRequest;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

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
     * 初始化用户钱包
     */
    int insertWallet(UserWallet wallet);

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
