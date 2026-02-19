package com.demo.dto.user;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;

/**
 * 商品编辑请求参数。
 */
@Data
public class ProductUpdateRequest {

    /** 商品标题。 */
    @NotBlank
    private String title;

    /** 商品描述。 */
    private String description;

    /** 商品价格。 */
    @NotNull
    private BigDecimal price;

    /** 商品图片 URL 列表。 */
    private List<String> images;
}
