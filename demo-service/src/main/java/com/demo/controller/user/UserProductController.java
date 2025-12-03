package com.demo.controller.user;

import com.demo.context.BaseContext;
import com.demo.dto.user.ProductDetailDTO;
import com.demo.dto.user.ProductUpdateRequest;
import com.demo.dto.user.UserProductQueryDTO;
import com.demo.entity.Product;
import com.demo.result.Result;
import com.demo.service.ProductService;
import com.github.pagehelper.PageInfo;
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
    public Result<PageInfo<Product>> getProducts(@Validated UserProductQueryDTO queryDTO) {
        log.info("获取用户商品列表");
        PageInfo<Product> pageInfo = productService.getUserProducts(queryDTO);

        return Result.success(pageInfo);
    }

    //商品详情跳转
    @GetMapping("/{productId}")
    public Result<ProductDetailDTO> getProductDetail(@PathVariable Long productId) {
        log.info("获取商品详情");
        ProductDetailDTO productDTO = productService.getProductDetail(productId);
        return Result.success(productDTO);
    }

    @PutMapping("/user/products/{productId}")
    public Result<ProductDetailDTO> updateMyProduct(
            @PathVariable Long productId,
            @Validated @RequestBody ProductUpdateRequest request) {

        Long currentUserId = BaseContext.getCurrentId();
        ProductDetailDTO dto = productService.updateMyProduct(currentUserId, productId, request);
        return Result.success(dto);
    }


}
