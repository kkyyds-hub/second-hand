package com.demo.service.serviceimpl;

import com.demo.constant.CreditConstants;
import com.demo.dto.user.UserCreditDTO;
import com.demo.dto.user.UserCreditLogDTO;
import com.demo.entity.User;
import com.demo.entity.UserCreditLog;
import com.demo.enumeration.CreditLevel;
import com.demo.enumeration.CreditReasonType;
import com.demo.exception.BusinessException;
import com.demo.mapper.CreditStatMapper;
import com.demo.mapper.UserCreditLogMapper;
import com.demo.mapper.UserMapper;
import com.demo.service.CreditService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 用户信用分服务实现。
 */
@Service
@Slf4j
public class CreditServiceImpl implements CreditService {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private CreditStatMapper creditStatMapper;

    @Autowired
    private UserCreditLogMapper userCreditLogMapper;

    /**
     * 查询用户当前信用分信息。
     */
    @Override
    public UserCreditDTO getCredit(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        UserCreditDTO dto = new UserCreditDTO();
        dto.setUserId(user.getId());
        dto.setCreditScore(user.getCreditScore());
        dto.setCreditLevel(user.getCreditLevel());
        dto.setCreditUpdatedAt(user.getCreditUpdatedAt());
        return dto;
    }

    /**
     * 根据统计数据重算用户信用分并写入流水。
     */
    @Override
    @Transactional
    public UserCreditDTO recalcUserCredit(Long userId, CreditReasonType reason, Long refId) {
        if (userId == null) {
            throw new BusinessException("userId 不能为空");
        }

        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }

        int before = (user.getCreditScore() == null) ? CreditConstants.DEFAULT_SCORE : user.getCreditScore();

        // 从“默认分 + 统计项”重算信用分。
        int computed = computeScoreFromStats(userId);
        int after = clamp(computed, CreditConstants.SCORE_MIN, CreditConstants.SCORE_MAX);
        String levelDb = CreditLevel.fromScore(after).getDbValue();

        // 更新用户信用分与等级。
        LocalDateTime now = LocalDateTime.now();
        userMapper.updateCredit(userId, after, levelDb, now);

        // 写信用流水：重算一定写，或分数变化时写；部分关键原因即使 delta=0 也强制写。
        CreditReasonType rt = (reason == null) ? CreditReasonType.RECALC : reason;
        int delta = after - before;
        boolean forceLog =
                rt == CreditReasonType.BAN_ACTIVE
                        || rt == CreditReasonType.ORDER_COMPLETED
                        || rt == CreditReasonType.ORDER_CANCELLED
                        || rt == CreditReasonType.USER_VIOLATION
                        || rt == CreditReasonType.PRODUCT_VIOLATION;

        if (rt == CreditReasonType.RECALC || delta != 0 || forceLog) {
            UserCreditLog log = new UserCreditLog();
            log.setUserId(userId);
            log.setDelta(delta);
            log.setReasonType(rt.getDbValue());
            log.setRefId(refId);
            log.setScoreBefore(before);
            log.setScoreAfter(after);
            userCreditLogMapper.insert(log);
        }

        log.info("recalcUserCredit done. userId={}, before={}, after={}, level={}", userId, before, after, levelDb);
        return getCredit(userId);
    }

    /**
     * 查询用户信用流水（按限制条数返回）。
     */
    @Override
    public List<UserCreditLogDTO> listLogs(Long userId, Integer limit) {
        if (userId == null) {
            throw new BusinessException("userId 不能为空");
        }
        int lim = (limit == null || limit <= 0) ? 50 : Math.min(limit, 200);

        List<UserCreditLog> logs = userCreditLogMapper.listByUserId(userId);
        if (logs == null || logs.isEmpty()) {
            return Collections.emptyList();
        }

        List<UserCreditLogDTO> res = new ArrayList<>();
        for (UserCreditLog log : logs) {
            UserCreditLogDTO dto = new UserCreditLogDTO();
            BeanUtils.copyProperties(log, dto);
            res.add(dto);
            if (res.size() >= lim) {
                break;
            }
        }
        return res;
    }

    /**
     * 从各统计维度计算信用分原始值（未夹紧前）。
     */
    private int computeScoreFromStats(Long userId) {
        long completedBuyer = nzL(creditStatMapper.countCompletedAsBuyer(userId));
        long completedSeller = nzL(creditStatMapper.countCompletedAsSeller(userId));
        long cancelledBuyer = nzL(creditStatMapper.countCancelledAsBuyer(userId));
        int violationDelta = nzI(creditStatMapper.sumViolationCreditDelta(userId));
        int activeBans = nzI(creditStatMapper.countActiveBans(userId, LocalDateTime.now()));
        int adminAdjust = nzI(creditStatMapper.sumAdminAdjustDelta(userId));

        long score = CreditConstants.DEFAULT_SCORE;
        score += adminAdjust;
        score += (completedBuyer + completedSeller) * (long) CreditConstants.DELTA_ORDER_COMPLETED;
        score += cancelledBuyer * (long) CreditConstants.DELTA_ORDER_CANCELLED;
        score += violationDelta;

        if (activeBans > 0) {
            score += CreditConstants.DELTA_BAN_ACTIVE;
        }

        // 先做边界夹紧，再转 int。
        long clamped = Math.max(CreditConstants.SCORE_MIN, Math.min(score, CreditConstants.SCORE_MAX));
        return (int) clamped;
    }

    /**
     * 整型边界夹紧。
     */
    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    /**
     * Long 空值归零。
     */
    private static Long nzL(Long v) {
        return v == null ? 0L : v;
    }

    /**
     * Integer 空值归零。
     */
    private static Integer nzI(Integer v) {
        return v == null ? 0 : v;
    }
}
