package com.demo.service;

import com.demo.result.PageResult;
import java.math.BigDecimal;

/**
 * Day13 Step8 - 钱包服务
 */
public interface WalletService {

    /**
     * 查询余额
     */
    BigDecimal getBalance(Long userId);

    /**
     * 查询余额流水（分页）
     */
    PageResult<com.demo.entity.WalletTransaction> listTransactions(Long userId, Integer page, Integer pageSize);

    /**
     * 提现申请
     */
    Long applyWithdraw(Long userId, com.demo.dto.wallet.WithdrawApplyRequest request);
}
