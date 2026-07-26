package com.demo.dto.cart;

import lombok.Data;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.Size;
import java.util.List;

/**
 * 批量删除购物车项请求 DTO。
 */
@Data
public class BatchDeleteCartItemsRequest {

    /** 购物车项 ID 列表。 */
    @NotEmpty(message = "购物车项 ID 列表不能为空")
    @Size(max = 50, message = "单次最多删除 50 项")
    private List<Long> cartItemIds;
}
