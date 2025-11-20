package com.demo.controller.admin;

import com.demo.dto.user.ProductDTO;
import com.demo.result.Result;
import com.demo.service.ProductService;
import com.github.pagehelper.PageInfo;
import io.swagger.annotations.Api;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/admin/products")
@Api(tags = "商品管理")
@Slf4j
public class ProductController {

    @Autowired
    private ProductService productService;

    // 获取待审核商品列表
    @GetMapping("/pending-approval")
    public Result<PageInfo<ProductDTO>> getPendingApprovalProducts(@RequestParam int page, @RequestParam int size, @RequestParam(required = false) String productName, @RequestParam(required = false) String category, @RequestParam(required = false) String status) {
        try {
            PageInfo<ProductDTO> pageInfo = productService.getPendingApprovalProducts(page, size, productName, category, status);
            return Result.success(pageInfo);
        } catch (Exception e) {
            log.error("获取待审核商品列表失败", e);
            return Result.error("获取待审核商品列表失败");
        }

    }
    //审批商品
    @GetMapping("/{productId}/approve")
    public Result<String> approveProduct(@PathVariable("productId") Long productId,
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

}
