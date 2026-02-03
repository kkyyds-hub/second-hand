package com.demo.dto.payment;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;

/**
 * Day13 Step2 - 支付回调请求（模拟接口）
 * 统一接入层占位：未来支付宝/微信都回调到这里，由渠道字段区分
 */
@Data
public class PaymentCallbackRequest {

    /**
     * 支付渠道：mock / alipay / wechat
     */
    @NotBlank(message = "支付渠道不能为空")
    private String channel;

    /**
     * 业务订单号（对应 orders.order_no）
     */
    @NotBlank(message = "订单号不能为空")
    private String orderNo;

    /**
     * 第三方交易流水号
     */
    @NotBlank(message = "交易流水号不能为空")
    private String tradeNo;

    /**
     * 支付金额
     */
    @NotNull(message = "支付金额不能为空")
    private BigDecimal amount;

    /**
     * 支付状态：SUCCESS / FAIL / PENDING
     */
    @NotBlank(message = "支付状态不能为空")
    private String status;

    /**
     * 时间戳（秒）
     */
    @NotNull(message = "时间戳不能为空")
    private Long timestamp;

    /**
     * 签名（Day13 占位验签：sign 非空 + timestamp 在 5 分钟内）
     */
    @NotBlank(message = "签名不能为空")
    private String sign;
}
