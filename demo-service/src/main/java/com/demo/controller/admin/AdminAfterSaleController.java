package com.demo.controller.admin;

import com.demo.dto.aftersale.ArbitrateRequest;
import com.demo.result.Result;
import com.demo.service.AfterSaleService;
import io.swagger.annotations.Api;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.validation.constraints.Min;

/**
 * Day13 Step5 - 售后接口（管理员侧）
 */
@RestController
@RequestMapping("/admin/after-sales")
@Api(tags = "管理员售后接口")
@Slf4j
@Validated
public class AdminAfterSaleController {

    @Autowired
    private AfterSaleService afterSaleService;

    /**
     * 平台裁决
     * PUT /admin/after-sales/{afterSaleId}/arbitrate
     */
    @PutMapping("/{afterSaleId}/arbitrate")
    public Result<String> arbitrate(
            @PathVariable @Min(value = 1, message = "售后ID必须大于0") Long afterSaleId,
            @Validated @RequestBody ArbitrateRequest request) {
        log.info("平台裁决售后：afterSaleId={}, approved={}", afterSaleId, request.getApproved());

        String msg = afterSaleService.arbitrate(afterSaleId, request);
        return Result.success(msg);
    }
}
