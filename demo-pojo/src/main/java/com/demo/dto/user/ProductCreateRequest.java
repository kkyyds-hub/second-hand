package com.demo.dto.user;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.Data;

import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;

@Data
/**
 * ProductCreateRequest 业务组件。
 */
public class ProductCreateRequest {

    @NotBlank(message = "商品标题不能为空")
    /** 字段：title。 */
    private String title;

    /** 业务语义说明。 */
    private String description;

    @NotNull(message = "商品价格不能为空")
    @DecimalMin(value = "0.01", message = "商品价格必须大于 0")
    /** 字段：price。 */
    private BigDecimal price;

    /**
     * 数据库是用逗号拼接存 images(text)，所以请求用 List<String> 更友好
     * 同时兼容前端可能传 imageUrls（避免接口对接踩坑）
     */
    @JsonAlias({"imageUrls"})
    private List<String> images;

    /**
     * 数据库允许为空：category varchar(60) default null
     * 所以这里不强制 NotBlank（Day3 最小闭环不引入复杂枚举校验）
     */
    private String category;
}
