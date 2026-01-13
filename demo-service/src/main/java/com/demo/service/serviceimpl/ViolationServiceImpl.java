package com.demo.service.serviceimpl;

import com.demo.constant.CreditConstants;
import com.demo.constant.MessageConstant;
import com.demo.context.BaseContext;
import com.demo.dto.Violation.ViolationRecordDTO;
import com.demo.dto.Violation.ViolationReportRequest;
import com.demo.dto.Violation.ViolationStatisticsResponseDTO;
import com.demo.entity.User;
import com.demo.entity.UserBan;
import com.demo.entity.UserViolation;
import com.demo.enumeration.CreditReasonType;
import com.demo.exception.BusinessException;
import com.demo.mapper.UserBanMapper;
import com.demo.mapper.UserMapper;
import com.demo.mapper.ViolationMapper;
import com.demo.service.CreditService;
import com.demo.service.ViolationService;
import com.github.pagehelper.PageHelper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigInteger;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Service
@Transactional
@Slf4j
public class ViolationServiceImpl implements ViolationService {

    @Autowired
    private ViolationMapper violationMapper;

    @Autowired
    private CreditService creditService;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private UserBanMapper userBanMapper;

    @Override
    public void unbanUser(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) throw new BusinessException("用户不存在");
        if (!"banned".equalsIgnoreCase(user.getStatus())) throw new BusinessException("用户未处于封禁状态");

        LocalDateTime now = LocalDateTime.now();

        // 1) 关闭有效封禁记录
        userBanMapper.closeActiveBans(userId, now);

        // 2) 恢复状态
        userMapper.updateStatus(userId, "active", now);

        // 3) 重算（会把 BAN_ACTIVE 影响移除）
        creditService.recalcUserCredit(userId, CreditReasonType.RECALC, null);
    }

    @Override
    public void reviewBan(Long userId, boolean isApproved, String reason) {
        User user = violationMapper.SelectById(userId);
        if (user == null) {
            throw new RuntimeException(MessageConstant.ID_NOT_FOUND);
        }

        if (isApproved) {
            banUser(userId, reason);
        } else {
            softDeleteUser(user);
        }
    }

    @Override
    public void banUser(Long userId, String reason) {
        User user = userMapper.selectById(userId);
        if (user == null) throw new BusinessException("用户不存在");
        if ("banned".equalsIgnoreCase(user.getStatus())) throw new BusinessException("用户已处于封禁状态");

        LocalDateTime now = LocalDateTime.now();

        // 1) 写入封禁记录（信用统计看的是 user_bans）
        UserBan ban = new UserBan();
        ban.setUserId(userId);
        ban.setBanType("PERM");
        ban.setReason(reason);
        ban.setSource("ADMIN");
        ban.setStartTime(now);
        ban.setEndTime(null);
        ban.setCreatedBy(BaseContext.getCurrentId());
        ban.setCreateTime(now);
        userBanMapper.insertUserBan(ban);

        // 2) 更新用户状态
        userMapper.updateStatus(userId, "banned", now);

        // 3) 触发信用重算并落日志（BAN_ACTIVE）
        creditService.recalcUserCredit(userId, CreditReasonType.BAN_ACTIVE, ban.getId());
    }

    @Override
    public void reportViolation(ViolationReportRequest request) {
        UserViolation violation = new UserViolation();
        violation.setUserId(request.getUserId());
        violation.setViolationType(request.getViolationType());
        violation.setDescription(request.getDescription());

        // evidenceUrls 可能为空
        if (request.getEvidenceUrls() != null && !request.getEvidenceUrls().isEmpty()) {
            violation.setEvidence(String.join(",", request.getEvidenceUrls()));
        } else {
            violation.setEvidence(null);
        }

        violation.setPunish(request.getPunishmentResult());

        // Step3：关键点1：写入违规扣分（必须写到 user_violations.credit，否则 Step2 统计不到）
        // 这里用 Day10 的默认扣分常量 -10（你也可以后续扩展：不同违规类型不同分值）
        violation.setCredit(CreditConstants.DELTA_USER_VIOLATION);

        // Step3：关键点2：record_time/create_time 都补齐（避免 DB/DTO 显示混乱）
        LocalDateTime now = LocalDateTime.now();
        violation.setRecordTime(now);
        violation.setCreateTime(now);

        // 插入（useGeneratedKeys 会回填 violation.id）
        violationMapper.insert(violation);

        // Step3：关键点3：插入成功后立刻触发重算
        creditService.recalcUserCredit(request.getUserId(), CreditReasonType.USER_VIOLATION, violation.getId());

        log.info("违规记录已上报: userId={}, type={}, violationId={}", request.getUserId(), request.getViolationType(), violation.getId());
    }


    @Override
    public ViolationStatisticsResponseDTO getViolationStatistics() {
        List<Map<String, Object>> statisticsList = violationMapper.getViolationStatistics();

        long totalViolations = statisticsList.stream()
                .mapToLong(stat -> asLong(stat.get("count"), 0L))
                .sum();

        List<ViolationStatisticsResponseDTO.ViolationTypeDistribution> distributionList = new ArrayList<>();
        for (Map<String, Object> stat : statisticsList) {
            long count = asLong(stat.get("count"), 0L);
            double percentage = totalViolations == 0 ? 0.0 : ((double) count / (double) totalViolations) * 100.0;

            ViolationStatisticsResponseDTO.ViolationTypeDistribution d =
                    new ViolationStatisticsResponseDTO.ViolationTypeDistribution();
            d.setViolationType(asString(stat.get("violationType")));
            d.setViolationTypeDesc(asString(stat.get("violationTypeDesc")));
            d.setCount(count);
            d.setPercentage(percentage);

            distributionList.add(d);
        }

        ViolationStatisticsResponseDTO response = new ViolationStatisticsResponseDTO();
        response.setViolationTypeDistribution(distributionList);
        return response;
    }

    @Override
    public ViolationRecordDTO getUserViolations(Long userId, int page, int size) {
        PageHelper.startPage(page, size);
        List<Map<String, Object>> violationRecords = violationMapper.getUserViolations(userId);

        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        List<ViolationRecordDTO.ViolationRecord> recordList = new ArrayList<>();

        for (Map<String, Object> record : violationRecords) {
            Object rtObj = record.get("recordTime");
            LocalDateTime recordTime = null;

            if (rtObj != null) {
                if (rtObj instanceof LocalDateTime) {
                    recordTime = (LocalDateTime) rtObj;
                } else if (rtObj instanceof Timestamp) {
                    recordTime = ((Timestamp) rtObj).toLocalDateTime();
                } else if (rtObj instanceof String) {
                    String s = ((String) rtObj).trim();
                    if (!s.isEmpty()) {
                        // 兼容 "yyyy-MM-dd HH:mm:ss" 与 "yyyy-MM-ddTHH:mm:ss"
                        if (s.contains(" ") && !s.contains("T")) {
                            s = s.replace(" ", "T");
                        }
                        recordTime = LocalDateTime.parse(s);
                    }
                } else {
                    // 兜底：转字符串再尝试 parse（不保证一定成功）
                    String s = String.valueOf(rtObj).trim();
                    if (!s.isEmpty()) {
                        if (s.contains(" ") && !s.contains("T")) {
                            s = s.replace(" ", "T");
                        }
                        try {
                            recordTime = LocalDateTime.parse(s);
                        } catch (Exception ignore) {
                            // 保持 null，不要再抛异常影响接口返回
                        }
                    }
                }
            }

            // 2) orderId：不要强转 String，统一 String.valueOf 更稳
            Object orderIdObj = record.get("orderId");
            String orderId = (orderIdObj == null) ? null : String.valueOf(orderIdObj);

            // 3) 构建对象
            ViolationRecordDTO.ViolationRecord violationRecord = ViolationRecordDTO.ViolationRecord.builder()
                    .id((Long) record.get("id"))
                    .userId((Long) record.get("userId"))
                    .username((String) record.get("username"))
                    .violationType((String) record.get("violationType"))
                    .violationTypeDesc((String) record.get("violationTypeDesc"))
                    .orderId(orderId)
                    .description((String) record.get("description"))
                    .evidenceUrls((List<String>) record.get("evidenceUrls"))
                    .punishmentResult((String) record.get("punishmentResult"))
                    .recordTime(recordTime)
                    .creditScoreChange((Integer) record.get("creditScoreChange"))
                    .build();

            recordList.add(violationRecord);
        }

        ViolationRecordDTO dto = new ViolationRecordDTO();
        dto.setList(recordList);
        return dto;
    }


    private void softDeleteUser(User user) {
        user.setStatus("deleted");
        user.setUpdateTime(LocalDateTime.now());
        violationMapper.update(user);
        log.info("用户已软删除: userId={}", user.getId());
    }

    // -------------------- 安全转换工具：避免 ClassCastException --------------------

    private static Object getAny(Map<String, Object> map, String... keys) {
        if (map == null || keys == null) return null;
        for (String k : keys) {
            if (k == null) continue;
            if (map.containsKey(k)) return map.get(k);
        }
        return null;
    }

    private static String asString(Object v) {
        if (v == null) return null;
        return String.valueOf(v);
    }

    private static Long asLong(Object v) {
        return asLong(v, null);
    }

    private static Long asLong(Object v, Long defaultVal) {
        if (v == null) return defaultVal;
        if (v instanceof Long) return (Long) v;
        if (v instanceof Integer) return ((Integer) v).longValue();
        if (v instanceof Short) return ((Short) v).longValue();
        if (v instanceof BigInteger) return ((BigInteger) v).longValue();
        if (v instanceof Number) return ((Number) v).longValue();
        if (v instanceof String) {
            String s = ((String) v).trim();
            if (s.isEmpty()) return defaultVal;
            try { return Long.parseLong(s); } catch (Exception ignored) { return defaultVal; }
        }
        return defaultVal;
    }

    private static Integer asInteger(Object v) {
        if (v == null) return null;
        if (v instanceof Integer) return (Integer) v;
        if (v instanceof Long) return ((Long) v).intValue();
        if (v instanceof Number) return ((Number) v).intValue();
        if (v instanceof String) {
            String s = ((String) v).trim();
            if (s.isEmpty()) return null;
            try { return Integer.parseInt(s); } catch (Exception ignored) { return null; }
        }
        return null;
    }

    private static List<String> asStringList(Object v) {
        if (v == null) return Collections.emptyList();

        if (v instanceof List) {
            List<?> raw = (List<?>) v;
            List<String> out = new ArrayList<>();
            for (Object o : raw) {
                if (o == null) continue;
                String s = String.valueOf(o).trim();
                if (!s.isEmpty()) out.add(s);
            }
            return out;
        }

        // DB 里常见是 "a,b,c" 或者 "a, b, c"
        if (v instanceof String) {
            String s = ((String) v).trim();
            if (s.isEmpty()) return Collections.emptyList();
            return Arrays.stream(s.split(","))
                    .map(String::trim)
                    .filter(x -> !x.isEmpty())
                    .toList();
        }

        return Collections.singletonList(String.valueOf(v));
    }

    private static String formatDateTime(Object v, DateTimeFormatter fmt) {
        if (v == null) return null;
        if (v instanceof LocalDateTime) return ((LocalDateTime) v).format(fmt);
        if (v instanceof Timestamp) return ((Timestamp) v).toLocalDateTime().format(fmt);

        // 某些驱动直接给 String 或 Date
        String s = String.valueOf(v).trim();
        return s.isEmpty() ? null : s;
    }
}
