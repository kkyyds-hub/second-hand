package com.demo.controller.admin;

import com.demo.dto.user.ProductDTO;
import com.demo.entity.ProductViolation;
import com.demo.exception.BusinessException;
import com.demo.exception.DatabaseUpdateException;
import com.demo.exception.ProductNotFoundException;
import com.demo.result.Result;
import com.demo.service.ProductService;
import com.github.pagehelper.PageInfo;
import com.demo.result.PageResult;
import io.swagger.annotations.Api;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/admin/products")
@Api(tags = "商品管理")
@Slf4j
public class ProductController {

    @Autowired
    private ProductService productService;

    // 获取待审核商品列表
    @GetMapping("/pending-approval")
    public Result<PageResult<ProductDTO>> getPendingApprovalProducts(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(value = "pageSize", required = false) Integer pageSize,
            @RequestParam(value = "size", required = false) Integer size,
            @RequestParam(required = false) String productName,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String status) {
        try {
            int ps = (pageSize != null) ? pageSize : (size != null ? size : 10);

            PageResult<ProductDTO> pageResult = productService.getPendingApprovalProducts(page, ps, productName, category, status);
            return Result.success(pageResult);

        } catch (Exception e) {
            log.error("获取待审核商品列表失败", e);
            return Result.error("获取待审核商品列表失败");
        }
    }


    // 审批商品
    @PostMapping("/{productId}/approve")
    public Result<String> approveProduct(
            @PathVariable("productId") Long productId,
            @RequestParam(value = "isApproved") boolean isApproved,
            @RequestParam(value = "reason", required = false) String reason) {
        try {
            productService.approveProduct(productId, isApproved, reason);
            return Result.success("商品审核成功");
        } catch (Exception e) {
            log.error("商品审核失败", e);
            return Result.error("商品审核失败");
        }
    }

    // 根据商品ID获取违规记录
    @GetMapping("/{productId}/violations")
    public ResponseEntity<List<ProductViolation>> getProductViolations(@PathVariable Long productId) {
        List<ProductViolation> violations = productService.getProductViolations(productId);
        return new ResponseEntity<>(violations, HttpStatus.OK);
    }

    // 添加商品违规记录
    @PostMapping("/{productId}/violations")
    public ResponseEntity<Void> addProductViolation(@PathVariable Long productId, @RequestBody ProductViolation violation) {
        violation.setProductId(productId);
        productService.addProductViolation(violation);
        return new ResponseEntity<>(HttpStatus.CREATED);
    }

    // 更新商品状态
    @PostMapping("/{productId}/update-status")
    public Result<String> updateProductStatus(@PathVariable Long productId, @RequestParam String status) {
        try {
            // 核心：这里不再用“中文 isValidStatus”，而是统一转 dbValue，再传给 service
            String statusDb = normalizeStatus(status);
            if (statusDb == null) {
                return Result.error("status 不能为空");
            }

            productService.updateProductStatus(productId, statusDb);
            return Result.success("商品状态更新成功");
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
     * - 允许 dbValue：on_sale / sold / off_shelf / under_review
     * - 兼容中文：上架 / 已售 / 下架 / 审核中（兼容旧测试，不影响内部口径）
     * - 允许“全部/空”表示不筛选（返回 null）
     */
    private String normalizeStatus(String status) {
        if (status == null || status.isBlank() || "全部".equals(status)) {
            return null;
        }
        status = status.trim();

        // 1) 兼容中文（旧接口/旧测试不至于全挂）
        switch (status) {
            case "上架":
            case "在售":
                return "on_sale";
            case "已售":
                return "sold";
            case "下架":
                return "off_shelf";
            case "审核中":
            case "待审核":
                return "under_review";
            default:
                // 2) 允许直接传 dbValue
                if (isDbStatus(status)) {
                    return status;
                }
                throw new BusinessException("非法商品状态: " + status
                        + "，允许: on_sale/sold/off_shelf/under_review 或 上架/已售/下架/审核中");
        }
    }

    private boolean isDbStatus(String status) {
        return "on_sale".equals(status)
                || "sold".equals(status)
                || "off_shelf".equals(status)
                || "under_review".equals(status);
    }
}
