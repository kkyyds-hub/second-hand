package com.demo.dto.admin;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

/**
 * 管理员强制下架请求参数。
 * 对应接口：PUT /admin/products/{productId}/force-off-shelf
 */
@Data
public class ForceOffShelfRequest {

    /** 强制下架原因编码（如 violation_reported）。 */
    @NotBlank(message = "reasonCode 不能为空")
    @Size(max = 64, message = "reasonCode 长度不能超过64")
    private String reasonCode;

    /** 强制下架原因文本（写入 products.reason 与审计日志）。 */
    @NotBlank(message = "reasonText 不能为空")
    @Size(max = 255, message = "reasonText 长度不能超过255")
    private String reasonText;

    /** 关联举报单号（可选，后续举报闭环会使用）。 */
    @Size(max = 32, message = "reportTicketNo 长度不能超过32")
    private String reportTicketNo;
}

