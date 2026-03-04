package com.demo.controller.admin;

import com.demo.context.BaseContext;
import com.demo.dto.admin.ForceOffShelfRequest;
import com.demo.dto.admin.RejectProductRequest;
import com.demo.dto.admin.ResolveProductReportRequest;
import com.demo.dto.user.ProductDTO;
import com.demo.entity.ProductViolation;
import com.demo.enumeration.ProductStatus;
import com.demo.exception.BusinessException;
import com.demo.exception.DatabaseUpdateException;
import com.demo.exception.ProductNotFoundException;
import com.demo.result.Result;
import com.demo.service.ProductReportService;
import com.demo.service.ProductService;
import com.demo.result.PageResult;
import io.swagger.annotations.Api;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;

/**
 * ProductController 业务组件。
 */
@RestController
@RequestMapping("/admin/products")
@Api(tags = "商品管理")
@Slf4j
public class ProductController {

    @Autowired
    private ProductService productService;

    @Autowired
    private ProductReportService productReportService;

    /**
     * 获取待审核商品分页列表。
     */
    @GetMapping("/pending-approval")
    public Result<PageResult<ProductDTO>> getPendingApprovalProducts(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(value = "pageSize", required = false) Integer pageSize,
            @RequestParam(value = "size", required = false) Integer size,
            @RequestParam(required = false) String productName,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String status) {
            int ps = (pageSize != null) ? pageSize : (size != null ? size : 10);

            PageResult<ProductDTO> pageResult = productService.getPendingApprovalProducts(page, ps, productName, category, status);
            return Result.success(pageResult);


    }
    /**
     * Day7：审核通过（PUT）
     */
    @PutMapping("/{productId}/approve")
    public Result<String> approveProductV2(@PathVariable("productId") Long productId) {
        // 审核幂等语义：
        // 1) 首次通过返回“商品审核通过”
        // 2) 重复通过返回“已处理”
        String message = productService.approveProduct(productId, true, null);
        return Result.success(message);
    }

    /**
     * Day7：审核驳回（PUT + DTO）
     */
    @PutMapping("/{productId}/reject")
    public Result<String> rejectProductV2(@PathVariable("productId") Long productId,
                                          @Valid @RequestBody RejectProductRequest request) {
        // 审核幂等语义：
        // 1) 首次驳回返回“商品审核驳回”
        // 2) 重复驳回返回“已处理”
        String message = productService.approveProduct(productId, false, request.getReason());
        return Result.success(message);
    }

    /**
     * Day16 Step3：管理员强制下架。
     * 接口：PUT /admin/products/{productId}/force-off-shelf
     * 作用：
     * 1) 对 under_review/on_sale 商品执行强制下架到 off_shelf
     * 2) 写入下架原因（products.reason）
     * 3) 记录状态审计日志（product_status_audit_log）
     * 4) 若商品已是 off_shelf，按幂等返回“商品已下架”
     */
    @PutMapping("/{productId}/force-off-shelf")
    public Result<String> forceOffShelf(@PathVariable("productId") Long productId,
                                        @Valid @RequestBody ForceOffShelfRequest request) {
        Long operatorId = BaseContext.getCurrentId();
        String message = productService.forceOffShelfProduct(operatorId, productId, request);
        return Result.success(message);
    }

    /**
     * Day16 Step4：管理员处理举报工单。
     * 接口：PUT /admin/products/reports/{ticketNo}/resolve
     * 处理动作：
     * 1) dismiss：举报不成立，工单关闭
     * 2) force_off_shelf：举报成立，联动强制下架 + 违规记录
     */
    @PutMapping("/reports/{ticketNo}/resolve")
    public Result<String> resolveReport(@PathVariable("ticketNo") String ticketNo,
                                        @Valid @RequestBody ResolveProductReportRequest request) {
        Long resolverId = BaseContext.getCurrentId();
        String message = productReportService.resolveReport(resolverId, ticketNo, request);
        return Result.success(message);
    }


    /**
     * 审批商品（兼容旧接口，建议使用 Day7 的 PUT 接口）。
     */
    @Deprecated
    @PostMapping("/{productId}/approve")
    public Result<String> approveProduct(
            @PathVariable("productId") Long productId,
            @RequestParam(value = "isApproved") boolean isApproved,
            @RequestParam(value = "reason", required = false) String reason) {
        try {
            String message = productService.approveProduct(productId, isApproved, reason);
            return Result.success(message);
        } catch (Exception e) {
            log.error("商品审核失败", e);
            return Result.error("商品审核失败");
        }
    }

    /**
     * 根据商品 ID 获取违规记录。
     */
    @GetMapping("/{productId}/violations")
    public Result<PageResult<ProductViolation>> getProductViolations(
            @PathVariable Long productId,
            @RequestParam(value = "page", required = false, defaultValue = "1") Integer page,
            @RequestParam(value = "pageSize", required = false, defaultValue = "20") Integer pageSize) {
        PageResult<ProductViolation> violations = productService.getProductViolations(productId, page, pageSize);
        return Result.success(violations);
    }

    /**
     * 为指定商品新增违规记录。
     */
    @PostMapping("/{productId}/violations")
    public ResponseEntity<Void> addProductViolation(@PathVariable Long productId, @RequestBody ProductViolation violation) {
        violation.setProductId(productId);
        productService.addProductViolation(violation);
        return new ResponseEntity<>(HttpStatus.CREATED);
    }

    /**
     * 兼容旧管理端入口：按目标状态触发统一状态迁移。
     * 说明：
     * 1) 该接口仍保留原 URL，避免前端一次性改动过大；
     * 2) 但实现已改为走统一迁移内核，不再允许“直接改库状态”；
     * 3) 返回文案透传 Service 结果，便于区分“真实迁移成功”和“幂等命中（已处理）”。
     */
    @PostMapping("/{productId}/update-status")
    public Result<String> updateProductStatus(@PathVariable Long productId, @RequestParam String status) {
        try {
            // 核心：这里不再用“中文 isValidStatus”，而是统一转 dbValue，再传给 service
            String statusDb = normalizeStatus(status);
            if (statusDb == null) {
                return Result.error("status 不能为空");
            }

            String message = productService.updateProductStatus(productId, statusDb);
            return Result.success(message);
        } catch (BusinessException e) {
            return Result.error(e.getMessage());
        } catch (ProductNotFoundException e) {
            log.error("商品未找到: {}", productId, e);
            return Result.error("商品未找到");
        } catch (DatabaseUpdateException e) {
            log.error("数据库更新失败", e);
            return Result.error("数据库更新失败");
        } catch (Exception e) {
            log.error("商品状态更新失败", e);
            return Result.error("商品状态更新失败");
        }
    }

    /**
     * 后台入参 status 统一规范化：
     * - 统一复用 ProductStatus.normalizeToDbValue，避免维护第二套状态映射
     * - 兼容中文：上架/在售/已售/下架/审核中/待审核
     * - 允许“全部/空”表示不筛选（返回 null）
     */
    private String normalizeStatus(String status) {
        if (status == null) {
            return null;
        }
        String normalized = status.trim();
        if (normalized.isEmpty() || "全部".equals(normalized)) {
            return null;
        }
        try {
            return ProductStatus.normalizeToDbValue(normalized);
        } catch (IllegalArgumentException ex) {
            throw new BusinessException("非法商品状态: " + status
                    + "，允许: on_sale/sold/off_shelf/under_review 或 上架/在售/已售/下架/审核中/待审核");
        }
    }
}
