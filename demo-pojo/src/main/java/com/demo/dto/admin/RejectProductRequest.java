package com.demo.dto.admin;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

@Data
public class RejectProductRequest {

    @NotBlank(message = "驳回原因不能为空")
    @Size(min = 1, max = 200, message = "驳回原因长度必须在 1-200 之间")
    private String reason;
}
