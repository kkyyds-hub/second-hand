package com.demo.controller;

import com.demo.dto.payment.PaymentCallbackRequest;
import com.demo.result.Result;
import com.demo.service.OrderService;
import io.swagger.annotations.Api;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * Day13 Step2 - 支付回调统一接入（模拟接口）
 * 不需要登录鉴权（第三方回调）
 */
@RestController
@RequestMapping("/payment")
@Api(tags = "支付回调接口")
@Slf4j
public class PaymentController {

    @Autowired
    private OrderService orderService;

    /**
     * 统一支付回调（占位接口）
     * POST /payment/callback
     */
    @PostMapping("/callback")
    public Result<String> paymentCallback(@Validated @RequestBody PaymentCallbackRequest request) {
        log.info("收到支付回调: channel={}, orderNo={}, status={}, tradeNo={}",
                request.getChannel(), request.getOrderNo(), request.getStatus(), request.getTradeNo());

        String result = orderService.handlePaymentCallback(request);
        return Result.success(result);
    }
}
