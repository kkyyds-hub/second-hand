package com.demo.dto.base;

import lombok.Data;
import javax.validation.constraints.NotBlank;

@Data
public class BanRequest {

    @NotBlank(message = "封禁原因不能为空")
    private String reason;

    private String violationType;  // ✅ 违规类型
    private String operator;       // 操作人
    private String remark;        // 备注
    private Integer durationDays; // 封禁时长（天）
}