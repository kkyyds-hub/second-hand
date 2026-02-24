package com.demo.dto.admin;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

/**
 * 管理员处理举报工单请求参数。
 * 对应接口：PUT /admin/products/reports/{ticketNo}/resolve
 */
@Data
public class ResolveProductReportRequest {
    /** 处理动作（dismiss / force_off_shelf）。 */
    @NotBlank(message = "action 不能为空")
    @Size(max = 32, message = "action 长度不能超过32")
    private String action;

    /** 处理备注。 */
    @Size(max = 255, message = "remark 长度不能超过255")
    private String remark;
}

