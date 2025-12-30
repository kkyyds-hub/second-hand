package com.demo.dto.user;

import lombok.Data;

import javax.validation.constraints.Size;

/**
 * Day5 - 买家确认收货请求 DTO（可选）
 *
 * 对应接口：POST /user/orders/{orderId}/confirm-receipt
 *
 * 说明：
 * - 如果你想保持接口更“干净”，可以不接收 body，此 DTO 也可以不用。
 * - 如果你希望未来扩展“签收备注/异常收货”，保留此 DTO 会更省事。
 */
@Data
public class ConfirmReceiptRequest {

    /**
     * 签收备注（可选）
     * 示例：已签收/放驿站/外包装破损但商品完好 等
     */
    @Size(max = 200, message = "备注长度不能超过 200 字符")
    private String remark;
}
