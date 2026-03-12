package com.demo.service.serviceimpl;

import com.demo.dto.admin.AdminAuditQueryDTO;
import com.demo.entity.AfterSale;
import com.demo.entity.ProductReportTicket;
import com.demo.entity.UserViolation;
import com.demo.mapper.AfterSaleMapper;
import com.demo.mapper.ProductReportTicketMapper;
import com.demo.mapper.ViolationMapper;
import com.demo.service.AdminAuditService;
import com.demo.vo.admin.AdminAuditOverviewVO;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * 管理端纠纷与违规页聚合服务实现。
 * 这里优先复用已有业务表，把前端当前需要的列表和统计统一整理成一个接口。
 */
@Service
@Slf4j
public class AdminAuditServiceImpl implements AdminAuditService {

    private static final int DEFAULT_LIMIT = 120;

    @Autowired
    private AfterSaleMapper afterSaleMapper;

    @Autowired
    private ProductReportTicketMapper productReportTicketMapper;

    @Autowired
    private ViolationMapper violationMapper;

    /**
     * 查询页面总览。
     */
    @Override
    public AdminAuditOverviewVO getOverview(AdminAuditQueryDTO queryDTO) {
        int limit = normalizeLimit(queryDTO);

        List<AdminAuditOverviewVO.AuditTicketItem> allTickets = new ArrayList<>();
        allTickets.addAll(buildDisputeTickets(afterSaleMapper.selectRecentForAudit(limit)));
        allTickets.addAll(buildReportTickets(productReportTicketMapper.selectRecent(limit)));
        allTickets.addAll(buildRiskTickets(violationMapper.selectRecentViolations(limit)));

        List<AdminAuditOverviewVO.AuditTicketItem> filteredTickets = allTickets.stream()
                .filter(ticket -> matchesKeyword(ticket, queryDTO == null ? null : queryDTO.getKeyword()))
                .filter(ticket -> matchesExact(ticket.getType(), queryDTO == null ? null : queryDTO.getType()))
                .filter(ticket -> matchesExact(ticket.getStatus(), queryDTO == null ? null : queryDTO.getStatus()))
                .filter(ticket -> matchesExact(ticket.getRiskLevel(), queryDTO == null ? null : queryDTO.getRiskLevel()))
                .sorted(Comparator.comparing(AdminAuditOverviewVO.AuditTicketItem::getCreateTime,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(limit)
                .collect(Collectors.toList());

        AdminAuditOverviewVO overview = new AdminAuditOverviewVO();
        overview.setTickets(filteredTickets);
        overview.setStats(buildStats(filteredTickets));
        return overview;
    }

    /**
     * 把售后单转换成页面纠纷工单。
     */
    private List<AdminAuditOverviewVO.AuditTicketItem> buildDisputeTickets(List<AfterSale> afterSales) {
        if (afterSales == null || afterSales.isEmpty()) {
            return List.of();
        }

        return afterSales.stream().map(afterSale -> {
            AdminAuditOverviewVO.AuditTicketItem item = new AdminAuditOverviewVO.AuditTicketItem();
            item.setId("AS-" + afterSale.getId());
            item.setType("DISPUTE");
            item.setTitle(buildDisputeTitle(afterSale));
            item.setTarget("订单 #" + afterSale.getOrderId());
            item.setRiskLevel(resolveDisputeRiskLevel(afterSale.getStatus()));
            item.setStatus(resolveDisputeStatus(afterSale.getStatus()));
            item.setCreateTime(afterSale.getCreateTime());
            item.setDescription(buildDisputeDescription(afterSale));
            item.setSourceId(afterSale.getId());
            item.setSourceStatus(afterSale.getStatus());
            return item;
        }).collect(Collectors.toList());
    }

    /**
     * 把商品举报工单转换成页面记录。
     */
    private List<AdminAuditOverviewVO.AuditTicketItem> buildReportTickets(List<ProductReportTicket> tickets) {
        if (tickets == null || tickets.isEmpty()) {
            return List.of();
        }

        return tickets.stream().map(ticket -> {
            AdminAuditOverviewVO.AuditTicketItem item = new AdminAuditOverviewVO.AuditTicketItem();
            item.setId(StringUtils.isNotBlank(ticket.getTicketNo()) ? ticket.getTicketNo() : "RPT-" + ticket.getId());
            item.setType("REPORT");
            item.setTitle("商品举报：" + normalizeBlank(ticket.getReportType(), "待核实举报"));
            item.setTarget("商品 #" + ticket.getProductId());
            item.setRiskLevel(resolveReportRiskLevel(ticket));
            item.setStatus(resolveReportStatus(ticket.getStatus()));
            item.setCreateTime(ticket.getCreateTime());
            item.setDescription(normalizeBlank(ticket.getDescription(), "暂无举报描述"));
            item.setSourceId(ticket.getId());
            item.setSourceStatus(ticket.getStatus());
            return item;
        }).collect(Collectors.toList());
    }

    /**
     * 把违规记录转换成页面风险线索。
     */
    private List<AdminAuditOverviewVO.AuditTicketItem> buildRiskTickets(List<UserViolation> violations) {
        if (violations == null || violations.isEmpty()) {
            return List.of();
        }

        return violations.stream().map(violation -> {
            AdminAuditOverviewVO.AuditTicketItem item = new AdminAuditOverviewVO.AuditTicketItem();
            item.setId("UV-" + violation.getId());
            item.setType("RISK");
            item.setTitle("违规线索：" + normalizeBlank(violation.getViolationType(), "用户异常行为"));
            item.setTarget("用户 #" + violation.getUserId());
            item.setRiskLevel(resolveViolationRiskLevel(violation));
            item.setStatus("PROCESSING");
            item.setCreateTime(violation.getRecordTime() != null ? violation.getRecordTime() : violation.getCreateTime());
            item.setDescription(buildViolationDescription(violation));
            item.setSourceId(violation.getId());
            item.setSourceStatus("RECORDED");
            return item;
        }).collect(Collectors.toList());
    }

    /**
     * 组装顶部统计卡。
     */
    private AdminAuditOverviewVO.AuditStats buildStats(List<AdminAuditOverviewVO.AuditTicketItem> tickets) {
        AdminAuditOverviewVO.AuditStats stats = new AdminAuditOverviewVO.AuditStats();
        LocalDate today = LocalDate.now();

        stats.setPendingDisputes(tickets.stream()
                .filter(ticket -> "DISPUTE".equals(ticket.getType()) && "PENDING".equals(ticket.getStatus()))
                .count());

        stats.setUrgentReports(tickets.stream()
                .filter(ticket -> "REPORT".equals(ticket.getType()) && "HIGH".equals(ticket.getRiskLevel()))
                .count());

        stats.setPlatformIntervention(tickets.stream()
                .filter(ticket -> "DISPUTE".equals(ticket.getType()) && !"CLOSED".equals(ticket.getStatus()))
                .count());

        stats.setTodayNewClues(tickets.stream()
                .filter(ticket -> ticket.getCreateTime() != null && today.equals(ticket.getCreateTime().toLocalDate()))
                .count());

        return stats;
    }

    private int normalizeLimit(AdminAuditQueryDTO queryDTO) {
        if (queryDTO == null || queryDTO.getLimit() == null) {
            return DEFAULT_LIMIT;
        }
        return Math.max(20, Math.min(queryDTO.getLimit(), DEFAULT_LIMIT));
    }

    private boolean matchesKeyword(AdminAuditOverviewVO.AuditTicketItem ticket, String keyword) {
        if (ticket == null || StringUtils.isBlank(keyword)) {
            return true;
        }
        String normalizedKeyword = keyword.trim().toLowerCase(Locale.ROOT);
        return containsIgnoreCase(ticket.getId(), normalizedKeyword)
                || containsIgnoreCase(ticket.getTitle(), normalizedKeyword)
                || containsIgnoreCase(ticket.getTarget(), normalizedKeyword)
                || containsIgnoreCase(ticket.getDescription(), normalizedKeyword);
    }

    private boolean containsIgnoreCase(String source, String keyword) {
        return source != null && source.toLowerCase(Locale.ROOT).contains(keyword);
    }

    private boolean matchesExact(String actual, String expected) {
        if (StringUtils.isBlank(expected) || "ALL".equalsIgnoreCase(expected)) {
            return true;
        }
        return StringUtils.equalsIgnoreCase(actual, expected);
    }

    private String buildDisputeTitle(AfterSale afterSale) {
        if (afterSale == null) {
            return "售后纠纷";
        }
        return "售后纠纷：" + normalizeBlank(afterSale.getReason(), "待确认原因");
    }

    private String buildDisputeDescription(AfterSale afterSale) {
        if (afterSale == null) {
            return "暂无纠纷描述";
        }
        if (StringUtils.isNotBlank(afterSale.getPlatformRemark())) {
            return afterSale.getPlatformRemark().trim();
        }
        if (StringUtils.isNotBlank(afterSale.getSellerRemark())) {
            return afterSale.getSellerRemark().trim();
        }
        return normalizeBlank(afterSale.getReason(), "暂无纠纷描述");
    }

    private String buildViolationDescription(UserViolation violation) {
        String base = normalizeBlank(violation.getDescription(), "系统识别到一条新的风险线索。");
        if (StringUtils.isBlank(violation.getPunish())) {
            return base;
        }
        return base + "；处罚结果：" + violation.getPunish();
    }

    private String normalizeBlank(String value, String defaultValue) {
        return StringUtils.isBlank(value) ? defaultValue : value.trim();
    }

    private String resolveDisputeStatus(String sourceStatus) {
        if (StringUtils.isBlank(sourceStatus)) {
            return "PENDING";
        }
        if ("CLOSED".equalsIgnoreCase(sourceStatus)) {
            return "CLOSED";
        }
        if ("DISPUTED".equalsIgnoreCase(sourceStatus)) {
            return "PENDING";
        }
        return "PROCESSING";
    }

    private String resolveDisputeRiskLevel(String sourceStatus) {
        if ("DISPUTED".equalsIgnoreCase(sourceStatus)) {
            return "HIGH";
        }
        if ("SELLER_REJECTED".equalsIgnoreCase(sourceStatus)) {
            return "MEDIUM";
        }
        return "LOW";
    }

    private String resolveReportStatus(String sourceStatus) {
        if (StringUtils.equalsIgnoreCase(sourceStatus, "PENDING")) {
            return "PENDING";
        }
        return "CLOSED";
    }

    private String resolveReportRiskLevel(ProductReportTicket ticket) {
        String reportType = ticket.getReportType() == null ? "" : ticket.getReportType().toLowerCase(Locale.ROOT);
        String description = ticket.getDescription() == null ? "" : ticket.getDescription().toLowerCase(Locale.ROOT);
        if (reportType.contains("fake")
                || reportType.contains("fraud")
                || reportType.contains("违禁")
                || description.contains("违禁")
                || description.contains("诈骗")) {
            return "HIGH";
        }
        if (StringUtils.equalsIgnoreCase(ticket.getStatus(), "PENDING")) {
            return "MEDIUM";
        }
        return "LOW";
    }

    private String resolveViolationRiskLevel(UserViolation violation) {
        Integer credit = violation.getCredit();
        if (credit != null && Math.abs(credit) >= 10) {
            return "HIGH";
        }
        if (credit != null && Math.abs(credit) >= 5) {
            return "MEDIUM";
        }
        if (StringUtils.containsIgnoreCase(violation.getPunish(), "ban")) {
            return "HIGH";
        }
        return "LOW";
    }
}
