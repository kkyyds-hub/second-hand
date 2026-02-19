package com.demo.service.serviceimpl;

import com.demo.constant.CreditConstants;
import com.demo.entity.Order;
import com.demo.entity.UserViolation;
import com.demo.enumeration.CreditReasonType;
import com.demo.mapper.ViolationMapper;
import com.demo.service.CreditService;
import com.demo.service.OrderShipTimeoutPenaltyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 发货超时处罚编排实现。
 *
 * 说明：
 * - 当前先搭建“可插拔框架”，默认开关关闭（不影响现有链路）。
 * - 打开开关后，先落“违规记录上报”这一步，后续再补更细处罚策略。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderShipTimeoutPenaltyServiceImpl implements OrderShipTimeoutPenaltyService {

    private final ViolationMapper violationMapper;
    private final CreditService creditService;

    /**
     * 处罚开关：默认关闭，避免在框架阶段引入行为变化。
     */
    @Value("${order.ship-timeout.penalty.enabled:false}")
    private boolean penaltyEnabled;

    /**
     * 实现接口定义的方法。
     */
    @Override
    public void applyPenalty(Order order) {
        if (order == null || order.getId() == null || order.getSellerId() == null) {
            return;
        }
        if (!penaltyEnabled) {
            log.info("skip ship-timeout penalty because feature is disabled, orderId={}", order.getId());
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        UserViolation violation = new UserViolation();
        violation.setUserId(order.getSellerId());
        // 幂等口径：同一卖家 + 同一违规类型 + 同一订单（bizId）仅记录一次
        violation.setBizId(order.getId());
        violation.setViolationType("ship_timeout");
        violation.setDescription("订单超时未发货，系统自动取消");
        violation.setEvidence("system:auto:ship_timeout");
        violation.setPunish("credit_deduct");
        violation.setCredit(CreditConstants.DELTA_USER_VIOLATION);
        violation.setRecordTime(now);
        violation.setCreateTime(now);

        int inserted = violationMapper.insertIgnore(violation);
        if (inserted == 0) {
            log.info("ship-timeout penalty idempotent hit, orderId={}, sellerId={}", order.getId(), order.getSellerId());
            return;
        }

        creditService.recalcUserCredit(order.getSellerId(), CreditReasonType.USER_VIOLATION, violation.getId());
        log.info("ship-timeout penalty applied, orderId={}, sellerId={}, violationId={}",
                order.getId(), order.getSellerId(), violation.getId());
    }
}
