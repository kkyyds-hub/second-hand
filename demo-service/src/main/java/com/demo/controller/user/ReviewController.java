package com.demo.controller.user;

import com.demo.context.BaseContext;
import com.demo.dto.base.PageQueryDTO;
import com.demo.dto.review.ReviewCreateRequest;
import com.demo.dto.review.ReviewItemDTO;
import com.demo.result.PageResult;
import com.demo.result.Result;
import com.demo.service.ReviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;

@RestController
@RequestMapping("/user/reviews")
@RequiredArgsConstructor
@Validated
/**
 * ReviewController 业务组件。
 */
public class ReviewController {

    private final ReviewService reviewService;

    /**
     * 创建评价（买家对卖家）
     */
    @PostMapping
    public Result<Long> createReview(@Valid @RequestBody ReviewCreateRequest request) {
        Long currentUserId = BaseContext.getCurrentId();
        Long reviewId = reviewService.createReview(currentUserId, request);
        return Result.success(reviewId);
    }

    /**
     * 我发出的评价（分页）
     */
    @GetMapping("/mine")
    public Result<PageResult<ReviewItemDTO>> listMyReviews(PageQueryDTO query) {
        Long currentUserId = BaseContext.getCurrentId();
        return Result.success(reviewService.listMyReviews(currentUserId, query));
    }
}
