package com.demo.service;

import com.demo.dto.user.UserCreditDTO;
import com.demo.dto.user.UserCreditLogDTO;
import com.demo.enumeration.CreditReasonType;

import java.util.List;

/**
 * 用户信用分服务接口。
 */
public interface CreditService {

    /**
     * 查询用户当前信用分快照。
     */
    UserCreditDTO getCredit(Long userId);

    /**
     * 重新计算用户信用分，并记录本次变更原因。
     */
    UserCreditDTO recalcUserCredit(Long userId, CreditReasonType reason, Long refId);

    /**
     * 查询用户最近信用分流水。
     */
    List<UserCreditLogDTO> listLogs(Long userId, Integer limit);

}
