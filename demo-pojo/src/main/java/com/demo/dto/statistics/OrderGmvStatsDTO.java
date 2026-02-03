package com.demo.dto.statistics;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class OrderGmvStatsDTO {
    private Long orderCount;
    private BigDecimal gmv;
}
