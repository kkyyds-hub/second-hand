package com.demo.service.serviceimpl;

import com.demo.constant.ProductMessageConstant;
import com.demo.dto.admin.ForceOffShelfRequest;
import com.demo.dto.admin.ResolveProductReportRequest;
import com.demo.dto.user.ProductReportRequest;
import com.demo.dto.user.ProductReportResponse;
import com.demo.entity.Product;
import com.demo.entity.ProductReportTicket;
import com.demo.entity.ProductViolation;
import com.demo.enumeration.ProductReportResolveAction;
import com.demo.enumeration.ProductReportStatus;
import com.demo.exception.BusinessException;
import com.demo.mapper.ProductMapper;
import com.demo.mapper.ProductReportTicketMapper;
import com.demo.security.InputSecurityGuard;
import com.demo.service.ProductGovernanceEventService;
import com.demo.service.ProductReportService;
import com.demo.service.ProductService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 商品举报工单服务实现（Day16 Step4）。
 *
 * 核心目标：
 * 1) 落地“买家举报 -> 管理员处理”闭环。
 * 2) 处理动作支持 dismiss / force_off_shelf。
 * 3) 处理流程采用条件更新 + 幂等回查，避免并发重复处理。
 */
@Service
@Slf4j
@Transactional
public class ProductReportServiceImpl implements ProductReportService {

    @Autowired
    private ProductReportTicketMapper productReportTicketMapper;

    @Autowired
    private ProductMapper productMapper;

    @Autowired
    private ProductService productService;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ProductGovernanceEventService productGovernanceEventService;

    /**
     * 买家提交商品举报工单。
     * 固定流程：
     * 1) 校验举报人/商品存在性（仅允许举报市场可见商品）。
     * 2) 生成工单号（RPT-yyyyMMdd-xxxxxx）。
     * 3) 插入 PENDING 工单。
     */
    @Override
    public ProductReportResponse createReport(Long reporterId, Long productId, ProductReportRequest request) {
        if (reporterId == null) {
            throw new BusinessException(ProductMessageConstant.PRODUCT_NO_PERMISSION_OPERATE);
        }
        if (request == null) {
            throw new BusinessException("举报参数不能为空");
        }
        // Day18 P3-S2：举报类型/描述是高频回显文本，统一做输入安全守卫。
        String reportType = InputSecurityGuard.normalizePlainText(request.getReportType(), "reportType", 64, true);
        String description = InputSecurityGuard.normalizePlainText(request.getDescription(), "description", 500, true);

        // 限定举报入口只针对“市场可见”商品（on_sale）。
        Product marketProduct = productMapper.getMarketProductById(productId);
        if (marketProduct == null) {
            throw new BusinessException(ProductMessageConstant.PRODUCT_NOT_FOUND_OR_UNAVAILABLE);
        }

        ProductReportTicket ticket = new ProductReportTicket();
        ticket.setProductId(productId);
        ticket.setReporterId(reporterId);
        ticket.setReportType(reportType);
        ticket.setDescription(description);
        ticket.setEvidenceUrls(toEvidenceJson(request.getEvidenceUrls()));
        ticket.setStatus(ProductReportStatus.PENDING.getCode());

        // 工单号冲突时重试，避免唯一键偶发冲突导致用户失败。
        for (int attempt = 0; attempt < 5; attempt++) {
            ticket.setTicketNo(generateTicketNo());
            try {
                productReportTicketMapper.insert(ticket);
                return new ProductReportResponse(ticket.getTicketNo());
            } catch (DuplicateKeyException ex) {
                log.warn("举报工单号冲突，准备重试: ticketNo={}, attempt={}", ticket.getTicketNo(), attempt + 1);
            }
        }
        throw new BusinessException(ProductMessageConstant.PRODUCT_REPORT_SUBMIT_FAILED_RETRY);
    }

    /**
     * 管理员处理举报工单。
     * 幂等口径：
     * 1) 若工单已非 PENDING，返回“工单已处理”。
     * 2) 条件更新 rows=0 时回查，若已被并发处理，也返回“工单已处理”。
     *
     * 处理动作：
     * - dismiss：工单改为 RESOLVED_INVALID
     * - force_off_shelf：工单改为 RESOLVED_VALID，并联动强制下架 + 违规记录
     */
    @Override
    public String resolveReport(Long resolverId, String ticketNo, ResolveProductReportRequest request) {
        if (resolverId == null) {
            throw new BusinessException(ProductMessageConstant.PRODUCT_NO_PERMISSION_OPERATE);
        }
        // 工单号/备注都先规范化，避免非法字符进入条件更新与事件载荷。
        String normalizedTicketNo = InputSecurityGuard.normalizePlainText(ticketNo, "ticketNo", 32, true);
        if (request == null) {
            throw new BusinessException("处理参数不能为空");
        }
        ProductReportResolveAction action;
        try {
            action = ProductReportResolveAction.fromCode(request.getAction());
        } catch (IllegalArgumentException ex) {
            throw new BusinessException("action 仅支持 dismiss/force_off_shelf");
        }
        String remark = InputSecurityGuard.normalizePlainText(request.getRemark(), "remark", 255, false);

        ProductReportTicket ticket = productReportTicketMapper.selectByTicketNo(normalizedTicketNo);
        if (ticket == null) {
            throw new BusinessException(ProductMessageConstant.PRODUCT_REPORT_TICKET_NOT_FOUND);
        }

        ProductReportStatus currentStatus = ProductReportStatus.fromCode(ticket.getStatus());
        if (currentStatus != ProductReportStatus.PENDING) {
            return ProductMessageConstant.PRODUCT_REPORT_ALREADY_RESOLVED;
        }

        ProductReportStatus targetStatus = (action == ProductReportResolveAction.DISMISS)
                ? ProductReportStatus.RESOLVED_INVALID
                : ProductReportStatus.RESOLVED_VALID;

        int rows = productReportTicketMapper.resolveIfPending(
                normalizedTicketNo,
                resolverId,
                action.getCode(),
                remark,
                targetStatus.getCode()
        );
        if (rows == 0) {
            ProductReportTicket latest = productReportTicketMapper.selectByTicketNo(normalizedTicketNo);
            if (latest == null) {
                throw new BusinessException(ProductMessageConstant.PRODUCT_REPORT_TICKET_NOT_FOUND);
            }
            if (ProductReportStatus.fromCode(latest.getStatus()) != ProductReportStatus.PENDING) {
                return ProductMessageConstant.PRODUCT_REPORT_ALREADY_RESOLVED;
            }
            throw new BusinessException(ProductMessageConstant.PRODUCT_REPORT_RESOLVE_FAILED_RETRY);
        }

        // 成立单：联动强制下架与违规记录。
        if (action == ProductReportResolveAction.FORCE_OFF_SHELF) {
            ForceOffShelfRequest forceRequest = new ForceOffShelfRequest();
            forceRequest.setReasonCode("violation_reported");
            forceRequest.setReasonText(buildForceOffShelfReasonText(remark, ticket.getDescription()));
            forceRequest.setReportTicketNo(normalizedTicketNo);
            productService.forceOffShelfProduct(resolverId, ticket.getProductId(), forceRequest);

            ProductViolation violation = new ProductViolation();
            violation.setProductId(ticket.getProductId());
            violation.setViolationType(ticket.getReportType());
            violation.setDescription(ticket.getDescription());
            violation.setEvidenceUrls(ticket.getEvidenceUrls());
            violation.setPunishmentResult("force_off_shelf");
            violation.setCreditScoreChange(null);
            violation.setStatus("active");
            violation.setRecordTime(LocalDateTime.now());
            productService.addProductViolation(violation);
        }

        // Day16 Step5：举报单处理成功后，统一投递 PRODUCT_REPORT_RESOLVED 事件给通知消费者。
        productGovernanceEventService.publishProductReportResolved(
                ticket,
                resolverId,
                action.getCode(),
                targetStatus.getCode(),
                remark
        );

        return ProductMessageConstant.PRODUCT_REPORT_RESOLVE_SUCCESS;
    }

    /**
     * 生成 Day16 风格举报工单号。
     * 示例：RPT-20260221-123456
     */
    private String generateTicketNo() {
        String day = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        int seq = ThreadLocalRandom.current().nextInt(0, 1_000_000);
        return "RPT-" + day + "-" + String.format("%06d", seq);
    }

    /**
     * 证据 URL 列表转 JSON 字符串。
     */
    private String toEvidenceJson(List<String> evidenceUrls) {
        if (evidenceUrls == null || evidenceUrls.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(evidenceUrls);
        } catch (JsonProcessingException ex) {
            throw new BusinessException("举报证据序列化失败");
        }
    }

    /**
     * 组装强制下架原因文本，优先处理备注，其次回退举报描述。
     */
    private String buildForceOffShelfReasonText(String remark, String description) {
        String reasonText = normalizeBlankToNull(remark);
        if (reasonText == null) {
            reasonText = normalizeBlankToNull(description);
        }
        if (reasonText == null) {
            reasonText = "举报成立，已强制下架";
        }
        if (reasonText.length() > 255) {
            return reasonText.substring(0, 255);
        }
        return reasonText;
    }

    /**
     * 把空白字符串归一化为 null。
     */
    private String normalizeBlankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
