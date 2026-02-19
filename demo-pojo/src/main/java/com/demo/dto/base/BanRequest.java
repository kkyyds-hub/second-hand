package com.demo.dto.base;

import lombok.Data;

import javax.validation.constraints.NotBlank;

/**
 * 封禁/审核封禁请求参数。
 */
@Data
public class BanRequest {

    /** 封禁原因。 */
    @NotBlank(message = "封禁原因不能为空")
    private String reason;

    /** 操作人。 */
    private String operator;
    /** 备注。 */
    private String remark;

    /** 封禁时长（天）。 */
    private Integer durationDays;

    /** 审核结果（true=通过，false=不通过）。 */
    private Boolean isApproved;

    /** 审核人。 */
    private String reviewer;
    /** 审核备注。 */
    private String reviewRemark;
}
