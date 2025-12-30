package com.demo.dto.user;

import lombok.Data;

import javax.validation.constraints.Size;

/**
 * Day5 - 买家确认收货请求（可选 DTO）
 * 对应：POST /user/orders/{orderId}/confirm-receipt
 *
 * 说明：
 * - 你也可以不接 body（更简洁），但保留 DTO 便于后续扩展“签收备注/异常签收”
 */
@Data
public class ConfirmReceiptRequest {

    @Size(max = 200, message = "备注长度不能超过 200 字符")
    private String remark; // 签收备注（可选）
}
