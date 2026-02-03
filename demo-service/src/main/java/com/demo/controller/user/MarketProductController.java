package com.demo.controller.user;

import com.demo.dto.base.PageQueryDTO;
import com.demo.dto.review.ReviewItemDTO;
import com.demo.dto.user.MarketProductDetailDTO;
import com.demo.dto.user.MarketProductQueryDTO;
import com.demo.dto.user.MarketProductSummaryDTO;
import com.demo.result.PageResult;
import com.demo.result.Result;
import com.demo.service.ProductService;
import com.demo.service.ReviewService;
import io.swagger.annotations.Api;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.validation.constraints.Min;

@RestController
@RequestMapping("/user/market/products")
@Validated
@Slf4j
@Api(tags = "用户市场接口")
@RequiredArgsConstructor
public class MarketProductController {

    private final ProductService productService;
    private final ReviewService reviewService;

    @GetMapping
    public Result<PageResult<MarketProductSummaryDTO>> listMarketProducts(@Validated MarketProductQueryDTO queryDTO) {
        log.info("市场商品列表 query={}", queryDTO);
        PageResult<MarketProductSummaryDTO> pageResult = productService.getMarketProductList(queryDTO);
        return Result.success(pageResult);
    }

    @GetMapping("/{productId}")
    public Result<MarketProductDetailDTO> getMarketProductDetail(@PathVariable Long productId) {
        log.info("市场商品详情 productId={}", productId);
        MarketProductDetailDTO dto = productService.getMarketProductDetail(productId);
        return Result.success(dto);
    }

    /**
     * 商品评价列表（分页）
     * GET /user/market/products/{productId}/reviews
     */
    @GetMapping("/{productId}/reviews")
    public Result<PageResult<ReviewItemDTO>> listProductReviews(
            @PathVariable @Min(1) Long productId,
            PageQueryDTO query) {
        log.info("查询商品评价列表 productId={}, page={}, pageSize={}",
                productId, query.getPage(), query.getPageSize());
        return Result.success(reviewService.listProductReviews(productId, query));
    }
}
