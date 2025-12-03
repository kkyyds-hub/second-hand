package com.demo.controller.admin;

import com.demo.dto.user.ProductDTO;
import com.demo.entity.ProductViolation;
import com.demo.exception.DatabaseUpdateException;
import com.demo.exception.ProductNotFoundException;
import com.demo.result.Result;
import com.demo.service.ProductService;
import com.github.pagehelper.PageInfo;
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
    public Result<PageInfo<ProductDTO>> getPendingApprovalProducts(
            @RequestParam int page,
            @RequestParam int size,
            @RequestParam(required = false) String productName,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String status) {
        try {
            PageInfo<ProductDTO> pageInfo = productService.getPendingApprovalProducts(page, size, productName, category, status);
            return Result.success(pageInfo);
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
        if (!isValidStatus(status)) {
            return Result.error("无效的商品状态");
        }
        try {
            productService.updateProductStatus(productId, status);
            return Result.success("商品状态更新成功");
        } catch (ProductNotFoundException e) {
            log.error("商品未找到: " + productId, e);
            return Result.error("商品未找到");
        } catch (DatabaseUpdateException e) {
            log.error("数据库更新失败", e);
            return Result.error("数据库更新失败");
        } catch (Exception e) {
            log.error("商品状态更新失败", e);
            return Result.error("商品状态更新失败");
        }
    }

    private boolean isValidStatus(String status) {
        // 假设有效的状态是：上架、已售、下架
        return "上架".equals(status) || "已售".equals(status) || "下架".equals(status);
    }
}
