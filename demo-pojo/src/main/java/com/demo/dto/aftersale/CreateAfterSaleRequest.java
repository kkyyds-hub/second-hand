package com.demo.dto.aftersale;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.util.List;

/**
 * Day13 Step5 - 买家发起售后申请
 */
@Data
public class CreateAfterSaleRequest {

    @NotNull(message = "订单 ID 不能为空")
    /** 订单 ID。 */
    private Long orderId;

    @NotBlank(message = "退货原因不能为空")
    @Size(min = 2, max = 200, message = "退货原因长度需在 2~200 字符")
    /** 字段：reason。 */
    private String reason;

    /**
     * 凭证图片（最多 3 张）
     */
    @Size(max = 3, message = "凭证图片最多 3 张")
    private List<String> evidenceImages;
}

