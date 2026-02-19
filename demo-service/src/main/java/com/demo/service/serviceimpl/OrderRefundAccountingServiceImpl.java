package com.demo.service.serviceimpl;

import com.demo.entity.Order;
import com.demo.entity.OrderRefundTask;
import com.demo.entity.UserWallet;
import com.demo.entity.WalletTransaction;
import com.demo.mapper.WalletMapper;
import com.demo.service.OrderRefundAccountingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * 退款记账编排实现。
 *
 * 说明：
 * - 该类先作为“统一记账扩展点”，让退款主链路具备可扩展结构。
 * - 当前默认关闭，后续再补充钱包余额变更与流水幂等细节。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderRefundAccountingServiceImpl implements OrderRefundAccountingService {

    //用于区分是否是订单退款加回的余额
    private static final String BIZ_TYPE_ORDER_REFUND = "ORDER_REFUND";

    /**
     * 记账开关：默认关闭，避免在框架阶段改变现有行为。
     */
    @Value("${order.refund.accounting.enabled:false}")
    private boolean accountingEnabled;

    private final WalletMapper walletMapper;

    /**
     * 实现接口定义的方法。
     */
    @Override
    public void recordRefund(Order order, OrderRefundTask refundTask) {
        if (order == null || refundTask == null) {
            return;
        }
        if (!accountingEnabled) {
            log.info("skip refund accounting because feature is disabled, orderId={}, taskId={}",
                    order.getId(), refundTask.getId());
            return;
        }

        Long buyerId = order.getBuyerId();
        Long refundTaskId = refundTask.getId();
        BigDecimal amount = refundTask.getAmount();
        if (buyerId == null || refundTaskId == null || amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalStateException("invalid refund accounting input, orderId=" + order.getId() + ", taskId=" + refundTaskId);
        }

        UserWallet wallet = lockOrCreateWallet(buyerId);
        BigDecimal currentBalance = wallet.getBalance() == null ? BigDecimal.ZERO : wallet.getBalance();
        BigDecimal newBalance = currentBalance.add(amount);

        WalletTransaction transaction = new WalletTransaction();
        transaction.setUserId(buyerId);
        transaction.setBizType(BIZ_TYPE_ORDER_REFUND);
        // 幂等口径（本阶段）：用 refundTask.id 作为业务唯一标识
        transaction.setBizId(refundTaskId);
        transaction.setAmount(amount);
        transaction.setBalanceAfter(newBalance);
        transaction.setRemark("ship_timeout_refund:orderId=" + order.getId());

        try {
            // 必须先插流水：
            // - 命中唯一键冲突时，直接判定“已记过账”，后续不能再改余额
            walletMapper.insertTransaction(transaction);
        } catch (DuplicateKeyException duplicateKeyException) {
            log.info("refund accounting idempotent hit, orderId={}, taskId={}", order.getId(), refundTaskId);
            return;
        }

        int updated = walletMapper.updateBalance(buyerId, newBalance);
        if (updated != 1) {
            throw new IllegalStateException("update wallet balance failed, userId=" + buyerId);
        }

        log.info("refund accounting success, orderId={}, taskId={}, userId={}, amount={}",
                order.getId(), refundTaskId, buyerId, amount);
    }

    /**
     * 锁定钱包；若不存在则初始化后再锁定。
     */
    private UserWallet lockOrCreateWallet(Long userId) {
        UserWallet wallet = walletMapper.selectByUserIdForUpdate(userId);
        if (wallet != null) {
            return wallet;
        }

        UserWallet initWallet = new UserWallet();
        initWallet.setUserId(userId);
        initWallet.setBalance(BigDecimal.ZERO);
        try {
            walletMapper.insertWallet(initWallet);
        } catch (DuplicateKeyException duplicateKeyException) {
            // 并发初始化命中唯一键：忽略并重新锁定读取
            log.info("wallet already initialized concurrently, userId={}", userId);
        }

        wallet = walletMapper.selectByUserIdForUpdate(userId);
        if (wallet == null) {
            throw new IllegalStateException("wallet init failed, userId=" + userId);
        }
        return wallet;
    }
}
