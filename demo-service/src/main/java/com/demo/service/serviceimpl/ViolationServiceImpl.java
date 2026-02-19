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

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.math.BigInteger;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.*;

@Service
@Transactional
@Slf4j
/**
 * ViolationServiceImpl 业务组件。
 */
public class ViolationServiceImpl implements ViolationService {

    @Autowired
    private ViolationMapper violationMapper;

    @Autowired
    private CreditService creditService;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private UserBanMapper userBanMapper;

    /**
     * 实现接口定义的方法。
     */
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

    /**
     * 实现接口定义的方法。
     */
    @Override
    public void reviewBan(Long userId, boolean isApproved, String reason) {
        // 保持你现有写法：如果你已把 SelectById 放在 ViolationMapper 里就能用
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

    /**
     * 实现接口定义的方法。
     */
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

    /**
     * 实现接口定义的方法。
     */
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

        // 写入违规扣分（必须写到 user_violations.credit，否则 Step2 统计不到）
        violation.setCredit(CreditConstants.DELTA_USER_VIOLATION);

        // record_time/create_time 补齐
        LocalDateTime now = LocalDateTime.now();
        violation.setRecordTime(now);
        violation.setCreateTime(now);

        // 插入
        violationMapper.insert(violation);

        // 插入成功后立刻触发重算
        creditService.recalcUserCredit(request.getUserId(), CreditReasonType.USER_VIOLATION, violation.getId());

        log.info("违规记录已上报: userId={}, type={}, violationId={}", request.getUserId(), request.getViolationType(), violation.getId());
    }

    /**
     * 注意：这里兼容 Mapper 返回：
     * - List<Map<String,Object>>
     * - List<ViolationsStatisticsDTO>（或任何 DTO）
     */
    @Override
    public ViolationStatisticsResponseDTO getViolationStatistics() {

        // 用 List<?> 承接，兼容 Map / DTO 两种形态
        List<?> statisticsList = violationMapper.getViolationStatistics();

        long totalViolations = 0L;
        if (statisticsList != null) {
            for (Object stat : statisticsList) {
                Object cntObj = getAnyObj(stat, "count", "cnt", "total", "num");
                totalViolations += asLong(cntObj, 0L);
            }
        }

        List<ViolationStatisticsResponseDTO.ViolationTypeDistribution> distributionList = new ArrayList<>();

        if (statisticsList != null) {
            for (Object stat : statisticsList) {
                long count = asLong(getAnyObj(stat, "count", "cnt", "total", "num"), 0L);
                double percentage = totalViolations == 0 ? 0.0 : ((double) count / (double) totalViolations) * 100.0;

                ViolationStatisticsResponseDTO.ViolationTypeDistribution d =
                        new ViolationStatisticsResponseDTO.ViolationTypeDistribution();

                d.setViolationType(asString(getAnyObj(stat, "violationType", "violation_type", "type")));
                d.setViolationTypeDesc(asString(getAnyObj(stat, "violationTypeDesc", "violation_type_desc", "typeDesc", "desc")));
                d.setCount(count);
                d.setPercentage(percentage);

                distributionList.add(d);
            }
        }

        ViolationStatisticsResponseDTO response = new ViolationStatisticsResponseDTO();
        response.setViolationTypeDistribution(distributionList);
        return response;
    }

    /**
     * 注意：这里兼容 Mapper 返回：
     * - List<Map<String,Object>>
     * - List<UserViolationDTO>（或任何 DTO）
     */
    @Override
    public ViolationRecordDTO getUserViolations(Long userId, int page, int size) {
        PageHelper.startPage(page, size);

        // 用 List<?> 承接，兼容 Map / DTO 两种形态
        List<?> violationRecords = violationMapper.getUserViolations(userId);

        List<ViolationRecordDTO.ViolationRecord> recordList = new ArrayList<>();

        if (violationRecords != null) {
            for (Object record : violationRecords) {

                // recordTime：兼容 LocalDateTime/Timestamp/String
                LocalDateTime recordTime = toLocalDateTime(getAnyObj(record, "recordTime", "record_time"));

                // orderId：不确定字段名/类型，统一 String.valueOf
                Object orderIdObj = getAnyObj(record, "orderId", "order_id", "refId", "ref_id");
                String orderId = (orderIdObj == null) ? null : String.valueOf(orderIdObj);

                ViolationRecordDTO.ViolationRecord violationRecord = ViolationRecordDTO.ViolationRecord.builder()
                        .id(asLong(getAnyObj(record, "id"), null))
                        .userId(asLong(getAnyObj(record, "userId", "user_id"), null))
                        .username(asString(getAnyObj(record, "username")))
                        .violationType(asString(getAnyObj(record, "violationType", "violation_type")))
                        .violationTypeDesc(asString(getAnyObj(record, "violationTypeDesc", "violation_type_desc")))
                        .orderId(orderId)
                        .description(asString(getAnyObj(record, "description")))
                        // evidenceUrls 兼容：List<String> / "a,b,c" / null
                        .evidenceUrls(asStringList(getAnyObj(record, "evidenceUrls", "evidence", "evidence_urls")))
                        .punishmentResult(asString(getAnyObj(record, "punishmentResult", "punish")))
                        .recordTime(recordTime)
                        // creditScoreChange 兼容：creditScoreChange / credit
                        .creditScoreChange(asInteger(getAnyObj(record, "creditScoreChange", "credit")))
                        .build();

                recordList.add(violationRecord);
            }
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

    // =========================================================
    // 兼容 Map/DTO 的“统一取值”工具：解决你这次的类型不兼容根因
    // =========================================================

    /**
     * 从一个对象中按 key 顺序取值：
     * - 如果 obj 是 Map：直接 map.get(key)
     * - 如果 obj 是 DTO：优先 getter(getXxx/isXxx)，其次字段反射
     */
    private static Object getAnyObj(Object obj, String... keys) {
        if (obj == null || keys == null) return null;

        // Map 直接取
        if (obj instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) obj;
            for (String k : keys) {
                if (k == null) continue;
                if (map.containsKey(k)) return map.get(k);
            }
            return null;
        }

        // DTO / POJO 反射取
        for (String k : keys) {
            if (k == null) continue;

            Object v = getProp(obj, k);
            if (v != null) return v;

            // 兼容 snake_case -> camelCase
            if (k.contains("_")) {
                String camel = snakeToCamel(k);
                v = getProp(obj, camel);
                if (v != null) return v;
            }
        }

        return null;
    }

    private static Object getProp(Object bean, String name) {
        if (bean == null || name == null || name.isEmpty()) return null;

        Class<?> c = bean.getClass();
        String cap = Character.toUpperCase(name.charAt(0)) + name.substring(1);

        // 1) getter: getXxx()
        try {
            Method m = c.getMethod("get" + cap);
            return m.invoke(bean);
        } catch (Exception ignore) {}

        // 2) boolean getter: isXxx()
        try {
            Method m = c.getMethod("is" + cap);
            return m.invoke(bean);
        } catch (Exception ignore) {}

        // 3) 字段：name
        try {
            Field f = findField(c, name);
            if (f != null) {
                f.setAccessible(true);
                return f.get(bean);
            }
        } catch (Exception ignore) {}

        return null;
    }

    private static Field findField(Class<?> c, String name) {
        Class<?> cur = c;
        while (cur != null && cur != Object.class) {
            try {
                return cur.getDeclaredField(name);
            } catch (NoSuchFieldException ignore) {}
            cur = cur.getSuperclass();
        }
        return null;
    }

    private static String snakeToCamel(String s) {
        if (s == null) return null;
        StringBuilder sb = new StringBuilder();
        boolean up = false;
        for (char ch : s.toCharArray()) {
            if (ch == '_') {
                up = true;
            } else {
                sb.append(up ? Character.toUpperCase(ch) : ch);
                up = false;
            }
        }
        return sb.toString();
    }

    // -------------------- 安全转换工具：避免 ClassCastException --------------------

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

        // DB 常见： "a,b,c"
        if (v instanceof String) {
            String s = ((String) v).trim();
            if (s.isEmpty()) return Collections.emptyList();
            String[] arr = s.split(",");
            List<String> out = new ArrayList<>();
            for (String x : arr) {
                String t = x == null ? "" : x.trim();
                if (!t.isEmpty()) out.add(t);
            }
            return out;
        }

        return Collections.singletonList(String.valueOf(v));
    }

    private static LocalDateTime toLocalDateTime(Object v) {
        if (v == null) return null;
        if (v instanceof LocalDateTime) return (LocalDateTime) v;
        if (v instanceof Timestamp) return ((Timestamp) v).toLocalDateTime();

        // 某些驱动直接给 String
        if (v instanceof String) {
            String s = ((String) v).trim();
            if (s.isEmpty()) return null;
            // 兼容 "yyyy-MM-dd HH:mm:ss" -> "yyyy-MM-ddTHH:mm:ss"
            if (s.contains(" ") && !s.contains("T")) {
                s = s.replace(" ", "T");
            }
            try {
                return LocalDateTime.parse(s);
            } catch (Exception ignore) {
                return null;
            }
        }

        // 兜底：转字符串再尝试 parse
        String s = String.valueOf(v).trim();
        if (s.isEmpty()) return null;
        if (s.contains(" ") && !s.contains("T")) {
            s = s.replace(" ", "T");
        }
        try {
            return LocalDateTime.parse(s);
        } catch (Exception ignore) {
            return null;
        }
    }
}
