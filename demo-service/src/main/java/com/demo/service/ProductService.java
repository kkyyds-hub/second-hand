package com.demo.service;

import com.demo.dto.user.ProductDTO;
import com.demo.entity.ProductViolation;
import com.github.pagehelper.PageInfo;

import java.util.List;

public interface ProductService {
    PageInfo<ProductDTO> getPendingApprovalProducts(int page, int size, String productName, String category, String status);

    void approveProduct(Long productId, boolean isApproved, String reason);

    List<ProductViolation> getProductViolations(Long productId);

    void addProductViolation(ProductViolation violation);
}
