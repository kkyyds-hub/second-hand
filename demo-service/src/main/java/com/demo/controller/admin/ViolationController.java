package com.demo.controller.admin;

import com.demo.dto.Violation.ViolationRecordDTO;
import com.demo.dto.Violation.ViolationReportRequest;
import com.demo.dto.Violation.ViolationStatisticsResponseDTO;
import com.demo.dto.base.BanRequest;
import com.demo.result.Result;
import com.demo.service.ViolationService;
import io.swagger.annotations.Api;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;

/**
 * 管理端违规治理接口。
 */
@RestController
@Api(tags = "封禁管理")
@RequestMapping("/admin/users")
@Slf4j
public class ViolationController {

    @Autowired
    private ViolationService violationService;

    /**
     * 上报一条违规记录。
     */
    @PostMapping("/user-violations")
    public Result<String> reportViolation(@RequestBody ViolationReportRequest request) {
        violationService.reportViolation(request);
        return Result.success("违规记录上报成功");
    }

    /**
     * 审核并执行封禁。
     */
    @PostMapping("/{userId}/ban")
    public Result<String> banUser(@PathVariable Long userId,
                                  @Valid @RequestBody BanRequest request) {
        violationService.reviewBan(userId, request.getIsApproved(), request.getReason());
        return Result.success("用户封禁成功");
    }

    /**
     * 解封用户。
     */
    @PostMapping("/{userId}/unban")
    public Result<String> unbanUser(@PathVariable Long userId) {
        log.info("用户解封: userId={}", userId);
        try {
            violationService.unbanUser(userId);
            return Result.success("用户解封成功");
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 查询违规统计信息。
     */
    @GetMapping("/user-violations/statistics")
    public Result<ViolationStatisticsResponseDTO> getViolationStatistics() {
        try {
            ViolationStatisticsResponseDTO statistics = violationService.getViolationStatistics();
            return Result.success(statistics);
        } catch (Exception e) {
            log.error("获取违规统计数据失败", e);
            return Result.error("获取违规统计数据失败");
        }
    }

    /**
     * 分页查询用户违规记录。
     */
    @GetMapping
    public Result<ViolationRecordDTO> getUserViolations(
            @RequestParam Long userId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        try {
            ViolationRecordDTO violationRecordDTO = violationService.getUserViolations(userId, page, size);
            return Result.success(violationRecordDTO);
        } catch (Exception e) {
            log.error("获取用户违规记录失败", e);
            return Result.error("获取用户违规记录失败");
        }
    }
}
