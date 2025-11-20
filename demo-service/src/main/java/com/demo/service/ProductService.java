package com.demo.service;

import com.demo.dto.user.ProductDTO;
import com.github.pagehelper.PageInfo;

public interface ProductService {
    PageInfo<ProductDTO> getPendingApprovalProducts(int page, int size, String productName, String category, String status);

    void approveProduct(Long productId, boolean isApproved, String reason);
}
