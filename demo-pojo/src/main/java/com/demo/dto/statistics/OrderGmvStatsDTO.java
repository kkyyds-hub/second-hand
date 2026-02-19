package com.demo.dto.statistics;

import lombok.Data;
import java.math.BigDecimal;

/**
 * 订单 GMV 统计 DTO。
 */
@Data
public class OrderGmvStatsDTO {

    /** 订单数量。 */
    private Long orderCount;

    /** 成交总金额（GMV）。 */
    private BigDecimal gmv;
}
