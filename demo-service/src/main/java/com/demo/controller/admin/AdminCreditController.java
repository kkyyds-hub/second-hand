package com.demo.controller.admin;

import com.demo.context.BaseContext;
import com.demo.dto.admin.AdminAdjustCreditRequest;
import com.demo.dto.user.UserCreditDTO;
import com.demo.dto.user.UserCreditLogDTO;
import com.demo.entity.User;
import com.demo.entity.UserCreditLog;
import com.demo.enumeration.CreditReasonType;
import com.demo.exception.BusinessException;
import com.demo.mapper.UserCreditLogMapper;
import com.demo.mapper.UserMapper;
import com.demo.result.Result;
import com.demo.service.CreditService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 管理端信用分管理接口。
 */
@RestController
@RequestMapping("/admin/credit")
public class AdminCreditController {

    @Autowired
    private CreditService creditService;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private UserCreditLogMapper userCreditLogMapper;

    /**
     * 查询指定用户信用分信息。
     */
    @GetMapping
    public Result<UserCreditDTO> get(@RequestParam("userId") Long userId) {
        return Result.success(creditService.getCredit(userId));
    }

    /**
     * 查询指定用户信用流水。
     */
    @GetMapping("/logs")
    public Result<List<UserCreditLogDTO>> logs(@RequestParam("userId") Long userId,
                                               @RequestParam(value = "limit", defaultValue = "50") Integer limit) {
        return Result.success(creditService.listLogs(userId, limit));
    }

    /**
     * 触发指定用户信用分重算。
     */
    @PostMapping("/recalc")
    public Result<UserCreditDTO> recalc(@RequestParam("userId") Long userId) {
        creditService.recalcUserCredit(userId, CreditReasonType.RECALC, null);
        return Result.success(creditService.getCredit(userId));
    }

    /**
     * 管理员手工调分并重算。
     */
    @PostMapping("/adjust")
    public Result<UserCreditDTO> adjust(@Valid @RequestBody AdminAdjustCreditRequest req) {
        Long adminId = BaseContext.getCurrentId();
        Long userId = req.getUserId();

        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }

        Integer before = user.getCreditScore() == null ? 100 : user.getCreditScore();
        Integer delta = req.getDelta();

        UserCreditLog log = new UserCreditLog();
        log.setUserId(userId);
        log.setDelta(delta);
        log.setReasonType(CreditReasonType.ADMIN_ADJUST.getDbValue());
        log.setRefId(req.getRefId());
        log.setScoreBefore(before);
        log.setScoreAfter(before + delta);

        String note = req.getReason();
        if (note == null) {
            note = "";
        }
        log.setReasonNote(note + " (adminId=" + adminId + ")");
        log.setCreateTime(LocalDateTime.now());
        userCreditLogMapper.insert(log);

        // 调整后再重算，确保最终分数口径与统计口径一致。
        creditService.recalcUserCredit(userId, CreditReasonType.RECALC, log.getId());
        return Result.success(creditService.getCredit(userId));
    }
}
