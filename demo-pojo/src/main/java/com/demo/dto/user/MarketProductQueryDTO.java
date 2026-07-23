package com.demo.dto.user;

import com.demo.dto.base.PageQueryDTO;
import lombok.Data;

import javax.validation.constraints.AssertTrue;
import javax.validation.constraints.DecimalMin;
import java.math.BigDecimal;

/**
 * 市场商品列表查询参数。
 */
@Data
public class MarketProductQueryDTO extends PageQueryDTO {

    /** 关键字（匹配标题/描述）。 */
    private String keyword;

    /** 商品分类。 */
    private String category;

    /** 可选最低价格（包含边界）。 */
    @DecimalMin(value = "0", inclusive = true, message = "最低价不能小于 0")
    private BigDecimal minPrice;

    /** 可选最高价格（包含边界）。 */
    @DecimalMin(value = "0", inclusive = true, message = "最高价不能小于 0")
    private BigDecimal maxPrice;

    /** 价格区间必须从低到高。 */
    @AssertTrue(message = "最低价不能大于最高价")
    public boolean isPriceRangeValid() {
        return minPrice == null || maxPrice == null || minPrice.compareTo(maxPrice) <= 0;
    }
}
