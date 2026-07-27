package com.demo.dto.user;

import lombok.Data;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;

/**
 * 卖家小店商品查询参数。
 */
@Data
public class SellerShopProductQueryDTO {

    /**
     * 商品状态筛选（on_sale / sold）。
     * 默认 on_sale。
     */
    private String status = "on_sale";

    /** 页码（默认 1）。 */
    @Min(1)
    private Integer page = 1;

    /** 每页条数（默认 12，最大 24）。 */
    @Min(1)
    @Max(24)
    private Integer pageSize = 12;

    /** 排除指定商品 ID（用于"该卖家的其他商品"场景）。 */
    private Long excludeProductId;
}
