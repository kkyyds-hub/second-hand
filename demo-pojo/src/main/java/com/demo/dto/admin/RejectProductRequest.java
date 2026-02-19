package com.demo.dto.admin;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

/**
 * 商品审核驳回请求参数。
 */
@Data
public class RejectProductRequest {

    /** 驳回原因。 */
    @NotBlank(message = "驳回原因不能为空")
    @Size(min = 1, max = 200, message = "驳回原因长度必须在 1-200 之间")
    private String reason;
}
