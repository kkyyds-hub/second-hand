package com.demo.dto.user;

import com.demo.dto.base.PageQueryDTO;
import lombok.Data;

/**
 * 市场商品列表查询参数。
 */
@Data
public class MarketProductQueryDTO extends PageQueryDTO {

    /** 关键字（匹配标题/描述）。 */
    private String keyword;

    /** 商品分类。 */
    private String category;
}
