package com.demo.controller.user;

import com.demo.context.BaseContext;
import com.demo.dto.wallet.WithdrawApplyRequest;
import com.demo.entity.WalletTransaction;
import com.demo.result.PageResult;
import com.demo.result.Result;
import com.demo.service.WalletService;
import io.swagger.annotations.Api;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

/**
 * Day13 Step8 - 用户钱包接口
 */
@RestController
@RequestMapping("/user/wallet")
@Api(tags = "用户钱包接口")
@Slf4j
public class WalletController {

    @Autowired
    private WalletService walletService;

    /**
     * 查询余额
     * GET /user/wallet/balance
     */
    @GetMapping("/balance")
    public Result<BigDecimal> getBalance() {
        Long currentUserId = BaseContext.getCurrentId();
        log.info("查询余额：userId={}", currentUserId);
        BigDecimal balance = walletService.getBalance(currentUserId);
        return Result.success(balance);
    }

    /**
     * 查询余额流水（分页）
     * GET /user/wallet/transactions?page=1&pageSize=10
     */
    @GetMapping("/transactions")
    public Result<PageResult<WalletTransaction>> listTransactions(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        Long currentUserId = BaseContext.getCurrentId();
        log.info("查询余额流水：userId={}, page={}, pageSize={}", currentUserId, page, pageSize);
        PageResult<WalletTransaction> result = walletService.listTransactions(currentUserId, page, pageSize);
        return Result.success(result);
    }

    /**
     * 提现申请
     * POST /user/wallet/withdraw
     */
    @PostMapping("/withdraw")
    public Result<Long> applyWithdraw(@Validated @RequestBody WithdrawApplyRequest request) {
        Long currentUserId = BaseContext.getCurrentId();
        log.info("提现申请：userId={}, amount={}", currentUserId, request.getAmount());
        Long requestId = walletService.applyWithdraw(currentUserId, request);
        return Result.success(requestId);
    }
}
