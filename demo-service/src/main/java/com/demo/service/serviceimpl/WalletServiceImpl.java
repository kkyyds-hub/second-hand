package com.demo.service.serviceimpl;

import com.demo.dto.wallet.WithdrawApplyRequest;
import com.demo.entity.UserWallet;
import com.demo.entity.WalletTransaction;
import com.demo.exception.BusinessException;
import com.demo.mapper.WalletMapper;
import com.demo.result.PageResult;
import com.demo.service.WalletService;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * Day13 Step8 - 钱包服务实现
 */
@Service
@Slf4j
public class WalletServiceImpl implements WalletService {

    @Autowired
    private WalletMapper walletMapper;

    @Override
    public BigDecimal getBalance(Long userId) {
        UserWallet wallet = walletMapper.selectByUserId(userId);
        if (wallet == null) {
            // 自动初始化钱包
            wallet = new UserWallet();
            wallet.setUserId(userId);
            wallet.setBalance(BigDecimal.ZERO);
            walletMapper.insertWallet(wallet);
            return BigDecimal.ZERO;
        }
        return wallet.getBalance();
    }

    @Override
    public PageResult<WalletTransaction> listTransactions(Long userId, Integer page, Integer pageSize) {
        PageHelper.startPage(page, pageSize);
        List<WalletTransaction> list = walletMapper.listTransactionsByUserId(userId);
        PageInfo<WalletTransaction> pageInfo = new PageInfo<>(list);
        return new PageResult<>(pageInfo.getList(), pageInfo.getTotal(), page, pageSize);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long applyWithdraw(Long userId, WithdrawApplyRequest request) {
        // 1. 检查余额
        BigDecimal balance = getBalance(userId);
        if (balance.compareTo(request.getAmount()) < 0) {
            throw new BusinessException("余额不足");
        }

        // 2. 记录提现申请（Day13 不做真实出金）
        com.demo.entity.WithdrawRequest withdrawRequest = new com.demo.entity.WithdrawRequest();
        withdrawRequest.setUserId(userId);
        withdrawRequest.setAmount(request.getAmount());
        withdrawRequest.setStatus("APPLIED");
        withdrawRequest.setBankCardNo(request.getBankCardNo());

        walletMapper.insertWithdrawRequest(withdrawRequest);

        log.info("提现申请成功：userId={}, amount={}, requestId={}",
                userId, request.getAmount(), withdrawRequest.getId());
        return withdrawRequest.getId();
    }
}
