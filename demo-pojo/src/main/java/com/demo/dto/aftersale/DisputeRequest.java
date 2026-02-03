package com.demo.dto.aftersale;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

/**
 * Day13 Step5 - 买家提交纠纷（平台介入）
 */
@Data
public class DisputeRequest {

    @NotBlank(message = "纠纷说明不能为空")
    @Size(min = 2, max = 500, message = "纠纷说明长度需在 2~500 字符")
    private String content;
}
