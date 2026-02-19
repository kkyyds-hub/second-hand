package com.demo.controller.user;

import com.demo.context.BaseContext;
import com.demo.dto.user.ProductCreateRequest;
import com.demo.dto.user.ProductDetailDTO;
import com.demo.dto.user.ProductUpdateRequest;
import com.demo.dto.user.UserProductQueryDTO;
import com.demo.entity.Product;
import com.demo.result.PageResult;
import com.demo.result.Result;
import com.demo.service.ProductService;
import com.demo.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * 卖家商品管理接口。
 */
@RestController
@RequestMapping("/user/products")
@Validated
@Slf4j
public class UserProductController {

    @Autowired
    private ProductService productService;

    @Autowired
    private UserService userService;

    /**
     * 查询当前卖家商品列表。
     */
    @GetMapping
    public Result<PageResult<Product>> getProducts(@Validated UserProductQueryDTO queryDTO) {
        Long currentUserId = BaseContext.getCurrentId();
        userService.requireSeller(currentUserId);
        queryDTO.setUserId(currentUserId);
        PageResult<Product> pageResult = productService.getUserProducts(queryDTO);
        return Result.success(pageResult);
    }

    /**
     * 查询商品详情。
     */
    @GetMapping("/{productId}")
    public Result<ProductDetailDTO> getProductDetail(@PathVariable("productId") Long productId) {
        ProductDetailDTO productDTO = productService.getProductDetail(productId);
        return Result.success(productDTO);
    }

    /**
     * 修改商品信息。
     */
    @PutMapping("/{productId}")
    public Result<ProductDetailDTO> updateMyProduct(
            @PathVariable Long productId,
            @Validated @RequestBody ProductUpdateRequest request) {
        Long currentUserId = BaseContext.getCurrentId();
        userService.requireSeller(currentUserId);
        ProductDetailDTO dto = productService.updateMyProduct(currentUserId, productId, request);
        return Result.success(dto);
    }

    /**
     * 下架商品。
     */
    @PutMapping("/{productId}/off-shelf")
    public Result<String> offShelf(@PathVariable("productId") Long productId) {
        Long currentUserId = BaseContext.getCurrentId();
        userService.requireSeller(currentUserId);
        productService.offShelfProductStatus(currentUserId, productId);
        return Result.success("下架成功");
    }

    /**
     * 创建商品。
     */
    @PostMapping
    public Result<ProductDetailDTO> createProduct(@Validated @RequestBody ProductCreateRequest request) {
        Long currentUserId = BaseContext.getCurrentId();
        userService.requireSeller(currentUserId);
        ProductDetailDTO dto = productService.createProduct(currentUserId, request);
        return Result.success(dto);
    }

    /**
     * 删除商品。
     */
    @DeleteMapping("/{productId}")
    public Result<String> deleteMyProduct(@PathVariable("productId") Long productId) {
        Long currentUserId = BaseContext.getCurrentId();
        userService.requireSeller(currentUserId);
        productService.deleteMyProduct(currentUserId, productId);
        return Result.success("删除成功");
    }

    /**
     * 重新提交审核。
     */
    @PutMapping("/{productId}/resubmit")
    public Result<ProductDetailDTO> resubmit(@PathVariable("productId") Long productId) {
        Long currentUserId = BaseContext.getCurrentId();
        userService.requireSeller(currentUserId);
        return Result.success(productService.resubmitProduct(currentUserId, productId));
    }

    /**
     * Day16 语义冻结说明：
     * 1. 该入口保留为兼容路径。
     * 2. 业务语义等价于“重新提交审核”（off_shelf -> under_review）。
     * 3. 不再表示“直接上架到 on_sale”。
     */
    @PutMapping("/{productId}/on-shelf")
    public Result<ProductDetailDTO> onShelf(@PathVariable("productId") Long productId) {
        Long currentUserId = BaseContext.getCurrentId();
        userService.requireSeller(currentUserId);
        return Result.success(productService.onShelfProduct(currentUserId, productId));
    }

    /**
     * 撤回审核中的商品到下架状态。
     */
    @PutMapping("/{productId}/withdraw")
    public Result<ProductDetailDTO> withdraw(@PathVariable("productId") Long productId) {
        Long currentUserId = BaseContext.getCurrentId();
        userService.requireSeller(currentUserId);
        return Result.success(productService.withdrawProduct(currentUserId, productId));
    }
}
