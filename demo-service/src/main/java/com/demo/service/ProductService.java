package com.demo.service;

import com.demo.dto.user.*;
import com.demo.entity.Product;
import com.demo.entity.ProductViolation;
import com.github.pagehelper.PageInfo;
import com.demo.result.PageResult;

import javax.validation.Valid;
import java.util.List;

public interface ProductService {
    PageResult<ProductDTO> getPendingApprovalProducts(int page, int pageSize, String productName, String category, String status);

    void approveProduct(Long productId, boolean isApproved, String reason);

    List<ProductViolation> getProductViolations(Long productId);

    void addProductViolation(ProductViolation violation);

    void updateProductStatus(Long productId, String status);

    PageResult<Product> getUserProducts(UserProductQueryDTO queryDTO);

    ProductDetailDTO getProductDetail(Long productId);

    ProductDetailDTO updateMyProduct(Long currentUserId, Long productId, @Valid ProductUpdateRequest request);

    void offShelfProductStatus(Long currentUserId, Long productId);

    ProductDetailDTO createProduct(Long currentUserId, ProductCreateRequest request);

    PageResult<MarketProductSummaryDTO> getMarketProductList(MarketProductQueryDTO queryDTO);

    MarketProductDetailDTO getMarketProductDetail(Long productId);

    void deleteMyProduct(Long currentUserId, Long productId);


    ProductDetailDTO resubmitProduct(Long currentUserId, Long productId);
}
