package com.demo.service;

import com.demo.dto.Violation.ViolationRecordDTO;
import com.demo.dto.Violation.ViolationReportRequest;
import com.demo.dto.Violation.ViolationStatisticsResponseDTO;

import java.util.Map;

/**
 * 违规治理服务接口。
 * 提供封禁、解封、审核、举报与统计能力。
 */
public interface ViolationService {

    /**
     * 解除用户当前有效封禁。
     */
    void unbanUser(Long userId);

    /**
     * 审核封禁申请并应用审核结果。
     */
    void reviewBan(Long userId, boolean isApproved, String reason);

    /**
     * 按原因封禁用户。
     */
    void banUser(Long userId, String reason);

    /**
     * 提交一条违规举报。
     */
    void reportViolation(ViolationReportRequest request);

    /**
     * 查询系统级违规统计信息。
     */
    ViolationStatisticsResponseDTO getViolationStatistics();

    /**
     * 分页查询指定用户违规记录。
     */
    ViolationRecordDTO getUserViolations(Long userId, int page, int size);
}
