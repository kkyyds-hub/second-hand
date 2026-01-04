package com.demo.controller.user;

import com.demo.context.BaseContext;
import com.demo.dto.user.ProductCreateRequest;
import com.demo.dto.user.ProductDetailDTO;
import com.demo.dto.user.ProductUpdateRequest;
import com.demo.dto.user.UserProductQueryDTO;
import com.demo.entity.Product;
import com.demo.result.Result;
import com.demo.service.ProductService;
import com.demo.result.PageResult;


import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/user/products")
@Validated
@Slf4j
public class UserProductController {

    @Autowired
    private ProductService productService;

    @GetMapping
    public Result<PageResult<Product>> getProducts(@Validated UserProductQueryDTO queryDTO) {
        log.info("获取用户商品列表");
        Long currentUserId = BaseContext.getCurrentId();

        // 统一从登录态注入，前端不传 userId
        queryDTO.setUserId(currentUserId);

        PageResult<Product> pageResult = productService.getUserProducts(queryDTO);
        return Result.success(pageResult);
    }


    //商品详情跳转
    @GetMapping("/{productId}")
    public Result<ProductDetailDTO> getProductDetail(@PathVariable("productId") Long productId) {
        log.info("获取商品详情");
        ProductDetailDTO productDTO = productService.getProductDetail(productId);
        return Result.success(productDTO);
    }

    @PutMapping("/{productId}")
    public Result<ProductDetailDTO> updateMyProduct(
            @PathVariable Long productId,
            @Validated @RequestBody ProductUpdateRequest request) {

        Long currentUserId = BaseContext.getCurrentId();
        ProductDetailDTO dto = productService.updateMyProduct(currentUserId, productId, request);
        return Result.success(dto);
    }

    @PutMapping("/{productId}/off-shelf")
    public Result<String> offShelf(@PathVariable("productId") Long productId) {
        Long currentUserId = BaseContext.getCurrentId();
        productService.offShelfProductStatus(currentUserId, productId);
        return Result.success("下架成功");
    }

    @PostMapping
    public Result<ProductDetailDTO> createProduct(@Validated @RequestBody ProductCreateRequest request) {
        Long currentUserId = BaseContext.getCurrentId();
        ProductDetailDTO dto = productService.createProduct(currentUserId, request);
        return Result.success(dto);
    }

    @DeleteMapping("/{productId}")
    public Result<String> deleteMyProduct(@PathVariable("productId") Long productId) {
        Long currentUserId = BaseContext.getCurrentId();
        productService.deleteMyProduct(currentUserId, productId);
        return Result.success("删除成功");
    }

    @PutMapping("/{productId}/resubmit")
    public Result<ProductDetailDTO> resubmit(@PathVariable("productId") Long productId) {
        Long currentUserId = BaseContext.getCurrentId();
        return Result.success(productService.resubmitProduct(currentUserId, productId));
    }

    @PutMapping("/{productId}/on-shelf")
    public Result<ProductDetailDTO> onShelf(@PathVariable("productId") Long productId) {
        Long currentUserId = BaseContext.getCurrentId();
        return Result.success(productService.onShelfProduct(currentUserId, productId));
    }
    @PutMapping("/{productId}/withdraw")
    public Result<ProductDetailDTO> withdraw(@PathVariable("productId") Long productId) {
        Long currentUserId = BaseContext.getCurrentId();
        return Result.success(productService.withdrawProduct(currentUserId, productId));
    }


}
