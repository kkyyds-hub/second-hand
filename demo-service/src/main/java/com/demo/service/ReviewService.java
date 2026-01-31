package com.demo.service;

import com.demo.dto.base.PageQueryDTO;
import com.demo.dto.review.ReviewCreateRequest;
import com.demo.dto.review.ReviewItemDTO;
import com.demo.result.PageResult;

public interface ReviewService {

    /**
     * 创建评价（买家对卖家）
     * @param currentUserId 当前登录用户 ID
     * @param request 评价请求
     * @return 评价 ID
     */
    Long createReview(Long currentUserId, ReviewCreateRequest request);

    /**
     * 我发出的评价（分页）
     * @param currentUserId 当前登录用户 ID
     * @param query 分页参数
     * @return 分页结果
     */
    PageResult<ReviewItemDTO> listMyReviews(Long currentUserId, PageQueryDTO query);

    /**
     * 商品评价列表（分页）
     * @param productId 商品 ID
     * @param query 分页参数
     * @return 分页结果
     */
    PageResult<ReviewItemDTO> listProductReviews(Long productId, PageQueryDTO query);
}