package com.demo.dto.user;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 卖家小店商品统计（聚合查询）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SellerShopStatsDTO {

    /** 在售商品数量。 */
    private Long onSaleCount;
    /** 已售商品数量。 */
    private Long soldCount;
}
