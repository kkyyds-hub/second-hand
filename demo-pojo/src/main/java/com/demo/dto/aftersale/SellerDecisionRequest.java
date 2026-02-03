package com.demo.dto.aftersale;

import lombok.Data;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

/**
 * Day13 Step5 - 卖家处理售后（同意/拒绝）
 */
@Data
public class SellerDecisionRequest {

    /**
     * 是否同意：true=同意，false=拒绝
     */
    @NotNull(message = "处理结果不能为空")
    private Boolean approved;

    /**
     * 备注
     */
    @Size(max = 200, message = "备注长度不能超过 200 字符")
    private String remark;
}
