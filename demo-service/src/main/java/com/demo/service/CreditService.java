package com.demo.service;

import com.demo.dto.user.UserCreditDTO;
import com.demo.dto.user.UserCreditLogDTO;
import com.demo.enumeration.CreditReasonType;

import java.util.List;

public interface CreditService {

    UserCreditDTO getCredit(Long userId);

    UserCreditDTO recalcUserCredit(Long userId, CreditReasonType reason, Long refId);

    List<UserCreditLogDTO> listLogs(Long userId, Integer limit);

}
