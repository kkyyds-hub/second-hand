package com.demo.controller.user;

import com.demo.context.BaseContext;
import com.demo.dto.aftersale.CreateAfterSaleRequest;
import com.demo.dto.aftersale.DisputeRequest;
import com.demo.dto.aftersale.SellerDecisionRequest;
import com.demo.result.Result;
import com.demo.service.AfterSaleService;
import io.swagger.annotations.Api;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.validation.constraints.Min;

/**
 * Day13 Step5 - 售后接口（用户侧）
 */
@RestController
@RequestMapping("/user/after-sales")
@Api(tags = "用户售后接口")
@Slf4j
@Validated
public class AfterSaleController {

    @Autowired
    private AfterSaleService afterSaleService;

    /**
     * 买家发起售后申请
     * POST /user/after-sales
     */
    @PostMapping
    public Result<Long> createAfterSale(@Validated @RequestBody CreateAfterSaleRequest request) {
        Long currentUserId = BaseContext.getCurrentId();
        log.info("买家发起售后：userId={}, orderId={}", currentUserId, request.getOrderId());

        Long afterSaleId = afterSaleService.createAfterSale(currentUserId, request);
        return Result.success(afterSaleId);
    }

    /**
     * 卖家处理售后（同意/拒绝）
     * PUT /user/after-sales/{afterSaleId}/seller-decision
     */
    @PutMapping("/{afterSaleId}/seller-decision")
    public Result<String> sellerDecision(
            @PathVariable @Min(value = 1, message = "售后ID必须大于0") Long afterSaleId,
            @Validated @RequestBody SellerDecisionRequest request) {
        Long currentUserId = BaseContext.getCurrentId();
        log.info("卖家处理售后：userId={}, afterSaleId={}, approved={}",
                currentUserId, afterSaleId, request.getApproved());

        String msg = afterSaleService.sellerDecision(afterSaleId, currentUserId, request);
        return Result.success(msg);
    }

    /**
     * 买家提交纠纷（平台介入）
     * POST /user/after-sales/{afterSaleId}/dispute
     */
    @PostMapping("/{afterSaleId}/dispute")
    public Result<String> submitDispute(
            @PathVariable @Min(value = 1, message = "售后ID必须大于0") Long afterSaleId,
            @Validated @RequestBody DisputeRequest request) {
        Long currentUserId = BaseContext.getCurrentId();
        log.info("买家提交纠纷：userId={}, afterSaleId={}", currentUserId, afterSaleId);

        String msg = afterSaleService.submitDispute(afterSaleId, currentUserId, request);
        return Result.success(msg);
    }
}
