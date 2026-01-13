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
import java.util.List;

@Service
@Slf4j
public class CreditServiceImpl implements CreditService {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private CreditStatMapper creditStatMapper;

    @Autowired
    private UserCreditLogMapper userCreditLogMapper;

    @Override
    public UserCreditDTO getCredit(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) throw new BusinessException("用户不存在");
        UserCreditDTO dto = new UserCreditDTO();
        dto.setUserId(user.getId());
        dto.setCreditScore(user.getCreditScore());
        dto.setCreditLevel(user.getCreditLevel());
        dto.setCreditUpdatedAt(user.getCreditUpdatedAt());
        return dto;
    }

    @Override
    @Transactional
    public UserCreditDTO recalcUserCredit(Long userId, CreditReasonType reason, Long refId) {
        if (userId == null) throw new BusinessException("userId 不能为空");

        User user = userMapper.selectById(userId);
        if (user == null) throw new BusinessException("用户不存在");

        int before = (user.getCreditScore() == null) ? CreditConstants.DEFAULT_SCORE : user.getCreditScore();

        // ====== 真·重算：从默认分 + 统计项推导 ======
        int computed = computeScoreFromStats(userId);

        int after = clamp(computed, CreditConstants.SCORE_MIN, CreditConstants.SCORE_MAX);
        String levelDb = CreditLevel.fromScore(after).getDbValue();

        // 落库
        LocalDateTime now = LocalDateTime.now();
        userMapper.updateCredit(userId, after, levelDb, now);


        // 写流水：RECALC 一律写；其他 reason 如果 delta=0 可不写（避免刷屏）secondhand2
        CreditReasonType rt = (reason == null) ? CreditReasonType.RECALC : reason;
        int delta = after - before;

        if (rt == CreditReasonType.RECALC || delta != 0) {
            UserCreditLog log = new UserCreditLog();
            log.setUserId(userId);
            log.setDelta(delta);
            log.setReasonType(rt.getDbValue());
            log.setRefId(refId);
            log.setScoreBefore(before);
            log.setScoreAfter(after);
            // create_time 由 SQL NOW()/DEFAULT 生成，不强依赖 Java 赋值
            userCreditLogMapper.insert(log);
        }

        log.info("recalcUserCredit done. userId={}, before={}, after={}, level={}", userId, before, after, levelDb);
        return getCredit(userId);
    }

    @Override
    public List<UserCreditLogDTO> listLogs(Long userId, Integer limit) {
        if (userId == null) throw new BusinessException("userId 不能为空");
        int lim = (limit == null || limit <= 0) ? 50 : Math.min(limit, 200);

        List<UserCreditLog> logs = userCreditLogMapper.listByUserId(userId);
        if (logs == null || logs.isEmpty()) return java.util.Collections.emptyList();

        List<UserCreditLogDTO> res = new java.util.ArrayList<>();
        for (UserCreditLog log : logs) {
            UserCreditLogDTO dto = new UserCreditLogDTO();
            BeanUtils.copyProperties(log, dto);
            res.add(dto);
            if (res.size() >= lim) break;
        }
        return res;
    }


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

        // clamp 到范围后再转 int
        long clamped = Math.max(CreditConstants.SCORE_MIN, Math.min(score, CreditConstants.SCORE_MAX));
        return (int) clamped;
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    private static Long nzL(Long v) {
        return v == null ? 0L : v;
    }

    private static Integer nzI(Integer v) {
        return v == null ? 0 : v;
    }
}
