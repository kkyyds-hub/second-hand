package com.demo.service.serviceimpl;


import com.demo.constant.MessageConstant;
import com.demo.dto.Violation.ViolationRecordDTO;
import com.demo.dto.Violation.ViolationReportRequest;
import com.demo.dto.Violation.ViolationStatisticsResponseDTO;
import com.demo.dto.base.BanRequest;
import com.demo.entity.User;
import com.demo.entity.UserViolation;
import com.demo.mapper.ViolationMapper;
import com.demo.service.ViolationService;
import com.github.pagehelper.PageHelper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@Transactional
@Slf4j
public class ViolationServiceImpl implements ViolationService {
    @Autowired
    private ViolationMapper violationMapper;
    @Override
    public void unbanUser(Long userId) {
        User user  =violationMapper.SelectById(userId);
        if (user == null){
            throw new RuntimeException(MessageConstant.ID_NOT_FOUND);
        }
        if ("active".equals(user.getStatus())) {
            throw new RuntimeException(MessageConstant.ALREADY_EXISTS);
        }
        user.setStatus("active");
        user.setUpdateTime(LocalDateTime.now());
        violationMapper.update(user);
    }

    @Override
    public void reviewBan(Long userId, boolean isApproved, String reason) {
        User user = violationMapper.SelectById(userId);
        if (user == null) {
            throw new RuntimeException(MessageConstant.ID_NOT_FOUND);
        }

        if (isApproved) {
            // 审核通过，执行封禁操作
            banUser(userId, reason);
        } else {
            // 审核不通过，进行软删除
            softDeleteUser(user);
        }
    }

    @Override
    public void banUser(Long userId, String reason ) {
        // 只负责封禁逻辑
        User user = violationMapper.SelectById(userId);
        if (user == null) {
            throw new RuntimeException(MessageConstant.ID_NOT_FOUND);
        }
        if ("banned".equals(user.getStatus())) {
            throw new RuntimeException(MessageConstant.ALREADY_EXISTS);
        }

        // 执行封禁
        user.setStatus("banned");
        user.setUpdateTime(LocalDateTime.now());
        violationMapper.update(user);

        log.info("用户封禁成功: userId={}, reason={}", userId, reason);
    }

    @Override
    public void reportViolation(ViolationReportRequest request) {
        UserViolation violation = new UserViolation();
        violation.setUserId(request.getUserId());
        violation.setViolationType(request.getViolationType());
        violation.setDescription(request.getDescription());
        violation.setEvidence(String.join(",", request.getEvidenceUrls()));
        violation.setPunish(request.getPunishmentResult());
        violation.setCreateTime(LocalDateTime.now());

        violationMapper.insert(violation);

        // 记录日志，但不直接执行处罚
        log.info("违规记录已上报: userId={}, type={}",
                request.getUserId(), request.getViolationType());
    }

    @Override
    public ViolationStatisticsResponseDTO getViolationStatistics() {
        // 获取违规类型统计数据
        List<Map<String, Object>> statisticsList = violationMapper.getViolationStatistics();

        // 计算总数
        long totalViolations = statisticsList.stream()
                .mapToLong(stat -> (long) stat.get("count"))
                .sum();

        // 计算每种违规类型的百分比
        List<ViolationStatisticsResponseDTO.ViolationTypeDistribution> distributionList = new ArrayList<>();
        for (Map<String, Object> stat : statisticsList) {
            long count = (long) stat.get("count");
            double percentage = (double) count / totalViolations * 100;

            ViolationStatisticsResponseDTO.ViolationTypeDistribution distribution = new ViolationStatisticsResponseDTO.ViolationTypeDistribution();
            distribution.setViolationType((String) stat.get("violationType"));
            distribution.setViolationTypeDesc((String) stat.get("violationTypeDesc"));
            distribution.setCount(count);
            distribution.setPercentage(percentage);

            distributionList.add(distribution);
        }

        // 封装返回结果
        ViolationStatisticsResponseDTO response = new ViolationStatisticsResponseDTO();
        response.setViolationTypeDistribution(distributionList);

        return response;  // 返回 DTO 对象
    }

    @Override
    public ViolationRecordDTO getUserViolations(Long userId, int page, int size) {
        // 进行分页查询
        PageHelper.startPage(page, size);
        List<Map<String, Object>> violationRecords = violationMapper.getUserViolations(userId);

        // 将查询结果转化为 DTO
        List<ViolationRecordDTO.ViolationRecord> recordList = new ArrayList<>();
        for (Map<String, Object> record : violationRecords) {
            ViolationRecordDTO.ViolationRecord violationRecord = ViolationRecordDTO.ViolationRecord.builder()
                    .id((Long) record.get("id"))
                    .userId((Long) record.get("userId"))
                    .username((String) record.get("username"))
                    .violationType((String) record.get("violationType"))
                    .violationTypeDesc((String) record.get("violationTypeDesc"))
                    .orderId((String) record.get("orderId"))
                    .description((String) record.get("description"))
                    .evidenceUrls((List<String>) record.get("evidenceUrls"))
                    .punishmentResult((String) record.get("punishmentResult"))
                    .recordTime((String) record.get("recordTime"))
                    .creditScoreChange((Integer) record.get("creditScoreChange"))
                    .build();  // 使用 builder() 方法来构建对象

            recordList.add(violationRecord);
        }

        // 封装返回结果
        ViolationRecordDTO violationRecordDTO = new ViolationRecordDTO();
        violationRecordDTO.setList(recordList);
        return violationRecordDTO;
    }


    private void softDeleteUser(User user) {
        user.setStatus("deleted"); // 假设 "deleted" 状态表示用户已被软删除
        user.setUpdateTime(LocalDateTime.now());
        violationMapper.update(user);

        log.info("用户已软删除: userId={}", user.getId());
    }
    }


