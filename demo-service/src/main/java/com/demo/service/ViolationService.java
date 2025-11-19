package com.demo.service;

import com.demo.dto.Violation.ViolationRecordDTO;
import com.demo.dto.Violation.ViolationReportRequest;
import com.demo.dto.Violation.ViolationStatisticsResponseDTO;

import java.util.Map;

public interface ViolationService {

    void unbanUser(Long userId);

    void reviewBan(Long userId, boolean isApproved, String reason);

    void banUser(Long userId, String reason);

    void reportViolation(ViolationReportRequest request);

    ViolationStatisticsResponseDTO getViolationStatistics();

    ViolationRecordDTO getUserViolations(Long userId, int page, int size);
}
