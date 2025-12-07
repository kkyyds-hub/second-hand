package com.demo.service;

import com.demo.dto.user.ProductDTO;
import com.demo.dto.user.ProductDetailDTO;
import com.demo.dto.user.ProductUpdateRequest;
import com.demo.dto.user.UserProductQueryDTO;
import com.demo.entity.Product;
import com.demo.entity.ProductViolation;
import com.github.pagehelper.PageInfo;

import javax.validation.Valid;
import java.util.List;

public interface ProductService {
    PageInfo<ProductDTO> getPendingApprovalProducts(int page, int size, String productName, String category, String status);

    void approveProduct(Long productId, boolean isApproved, String reason);

    List<ProductViolation> getProductViolations(Long productId);

    void addProductViolation(ProductViolation violation);

    void updateProductStatus(Long productId, String status);

    PageInfo<Product> getUserProducts(UserProductQueryDTO queryDTO);

    ProductDetailDTO getProductDetail(Long productId);

    ProductDetailDTO updateMyProduct(Long currentUserId, Long productId, @Valid ProductUpdateRequest request);

    void offShelfProductStatus(Long currentUserId, Long productId);
}
