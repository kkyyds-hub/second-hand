package com.demo.dto.aftersale;

import lombok.Data;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

/**
 * Day13 Step5 - 后台裁决售后
 */
@Data
public class ArbitrateRequest {

    /**
     * 是否支持退款：true=支持，false=驳回
     */
    @NotNull(message = "裁决结果不能为空")
    private Boolean approved;

    /**
     * 裁决备注
     */
    @Size(max = 200, message = "备注长度不能超过 200 字符")
    private String remark;
}
