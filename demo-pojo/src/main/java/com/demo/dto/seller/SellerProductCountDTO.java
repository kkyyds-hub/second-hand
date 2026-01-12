package com.demo.dto.seller;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 卖家商品统计（单行汇总）
 * 字段命名需与 SQL 列别名完全一致（MyBatis 自动映射依赖别名）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SellerProductCountDTO {

    /** 商品总数 */
    private Long totalProducts;

    /** 待审核 */
    private Long underReviewProducts;

    /** 在售 */
    private Long onSaleProducts;

    /** 已下架 */
    private Long offShelfProducts;

    /** 已售出 */
    private Long soldProducts;
}
