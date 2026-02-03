package com.demo.controller.admin;

import com.demo.result.Result;
import com.demo.service.StatisticsService;
import io.swagger.annotations.Api;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Map;

/**
 * Day13 Step7 - 统计面板 API
 */
@RestController
@RequestMapping("/admin/statistics")
@Api(tags = "管理员统计接口")
@Slf4j
public class StatisticsController {

    @Autowired
    private StatisticsService statisticsService;

    /**
     * 查询DAU
     * GET /admin/statistics/dau?date=2026-01-31
     */
    @GetMapping("/dau")
    public Result<Long> getDAU(@RequestParam String date) {
        log.info("管理员查询DAU：date={}", date);
        LocalDate localDate = LocalDate.parse(date);
        Long dau = statisticsService.countDAU(localDate);
        return Result.success(dau);
    }

    /**
     * 查询商品发布量
     * GET /admin/statistics/product-publish?date=2026-01-31
     */
    @GetMapping("/product-publish")
    public Result<Map<String, Object>> getProductPublish(@RequestParam String date) {
        log.info("管理员查询商品发布量：date={}", date);
        LocalDate localDate = LocalDate.parse(date);
        Map<String, Object> result = statisticsService.countProductPublish(localDate);
        return Result.success(result);
    }

    /**
     * 查询成交订单量与GMV
     * GET /admin/statistics/order-gmv?date=2026-01-31
     */
    @GetMapping("/order-gmv")
    public Result<Map<String, Object>> getOrderAndGMV(@RequestParam String date) {
        log.info("管理员查询订单GMV：date={}", date);
        LocalDate localDate = LocalDate.parse(date);
        Map<String, Object> result = statisticsService.countOrderAndGMV(localDate);
        return Result.success(result);
    }
}
