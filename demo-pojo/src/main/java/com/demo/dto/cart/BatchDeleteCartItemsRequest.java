package com.demo.dto.cart;

import lombok.Data;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.util.List;

/**
 * 批量删除购物车项请求 DTO。
 *
 * 校验口径：
 * 1) 列表非空；
 * 2) 每个元素非 null 且大于 0；
 * 3) 去重后的有效数量上限（50）由 Service 统一校验，
 *    不在 DTO 用原始数组长度限制，避免误伤“重复 ID 去重后仍合法”的请求。
 */
@Data
public class BatchDeleteCartItemsRequest {

    /** 购物车项 ID 列表。 */
    @NotEmpty(message = "购物车项 ID 列表不能为空")
    private List<@NotNull(message = "购物车项 ID 不能为空")
                 @Min(value = 1, message = "购物车项 ID 必须大于 0") Long> cartItemIds;
}
