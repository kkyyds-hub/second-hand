package com.demo.dto.wallet;

import lombok.Data;

import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * Day13 Step8 - 提现申请请求
 */
@Data
public class WithdrawApplyRequest {

    @NotNull(message = "提现金额不能为空")
    @DecimalMin(value = "0.01", message = "提现金额最小为0.01")
    /** 字段：amount。 */
    private BigDecimal amount;

    @NotBlank(message = "银行卡号不能为空")
    @Size(min = 4, max = 32, message = "银行卡号长度需在4~32字符")
    /** 字段：bankCardNo。 */
    private String bankCardNo;
}
