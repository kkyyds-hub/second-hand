package com.demo.dto.user;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;

/**
 * Day5 - 卖家发货请求 DTO
 *
 * 对应接口：POST /user/orders/{orderId}/ship
 *
 * 字段建议与 orders 表保持一致（若你启用了 MyBatis 下划线转驼峰）：
 * shippingCompany -> shipping_company
 * trackingNo      -> tracking_no
 * remark          -> shipping_remark（可选）
 */
@Data
public class ShipOrderRequest {

    /**
     * 物流公司/快递公司（如：SF、EMS、圆通、中通等）
     * 建议：必填，2-32 字符；允许中文/英文/数字/空格/下划线/短横线/点
     */
    @NotBlank(message = "快递公司不能为空")
    @Size(min = 2, max = 32, message = "快递公司长度需在 2~32 字符")
    @Pattern(
            regexp = "^[\\p{L}0-9 _.-]{2,32}$",
            message = "快递公司格式不正确"
    )
    private String shippingCompany;

    /**
     * 运单号（通常为字母数字组合，有时带短横线）
     * 建议：必填，6-64 字符；仅允许字母/数字/短横线
     */
    @NotBlank(message = "运单号不能为空")
    @Size(min = 6, max = 64, message = "运单号长度需在 6~64 字符")
    @Pattern(
            regexp = "^[A-Za-z0-9-]{6,64}$",
            message = "运单号格式不正确"
    )
    private String trackingNo;

    /**
     * 发货备注（可选，不参与状态机判断）
     * 例如：放门口/易碎/周末不派送 等
     */
    @Size(max = 200, message = "备注长度不能超过 200 字符")
    private String remark;
}
