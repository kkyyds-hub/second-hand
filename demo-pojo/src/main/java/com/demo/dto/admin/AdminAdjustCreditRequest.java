package com.demo.dto.admin;

import lombok.Data;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

/**
 * 管理员调整信用分请求
 */
@Data
public class AdminAdjustCreditRequest {

    @NotNull(message = "用户 ID 不能为空")
    /** 用户 ID。 */
    private Long userId;

    @NotNull(message = "信用分变动值不能为空")
    /** 字段：delta。 */
    private Integer delta;

    @Size(max = 64, message = "调整原因长度不能超过64")
    /** 字段：reason。 */
    private String reason;

    private Long refId;  // 关联业务 ID（可选）
}


