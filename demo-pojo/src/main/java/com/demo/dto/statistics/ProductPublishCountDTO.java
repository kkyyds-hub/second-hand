package com.demo.dto.statistics;

import lombok.Data;

/**
 * 商品发布数量统计 DTO（按分类聚合）。
 */
@Data
public class ProductPublishCountDTO {

    /** 商品分类。 */
    private String category;

    /** 发布数量。 */
    private Long count;
}
