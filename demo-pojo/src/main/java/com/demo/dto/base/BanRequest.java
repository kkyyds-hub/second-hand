package com.demo.dto.base;

import lombok.Data;
import javax.validation.constraints.NotBlank;

@Data
public class BanRequest {

    @NotBlank(message = "封禁原因不能为空")
    private String reason;         // 封禁原因

    private String operator;       // 操作人
    private String remark;         // 备注

    private Integer durationDays;  // 封禁时长（天）

    private Boolean isApproved;    // 审核结果（通过：true，未通过：false）

    private String reviewer;       // 审核人
    private String reviewRemark;   // 审核备注（如果有）
}
