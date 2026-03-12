package com.demo.controller.admin;

import com.demo.result.Result;
import com.demo.service.AdminDashboardService;
import com.demo.vo.admin.AdminDashboardOverviewVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * 管理后台首页聚合接口。
 *
 * 用途：
 * 1. 给前端 Dashboard 首页提供一个“一次请求拿全量首页数据”的入口；
 * 2. 避免前端自己同时请求统计、待审核商品、违规统计、售后纠纷等多个接口再拼装；
 * 3. 后续如果首页模块扩展，也优先在这里统一维护。
 */
@RestController
@RequestMapping("/admin/dashboard")
@Api(tags = "管理后台首页")
@Slf4j
public class AdminDashboardController {

    @Autowired
    private AdminDashboardService adminDashboardService;

    /**
     * 查询首页总览。
     *
     * 示例：
     * GET /admin/dashboard/overview?date=2026-03-12
     *
     * 如果前端没传 date，则默认按今天统计。
     */
    @GetMapping("/overview")
    @ApiOperation("管理后台首页总览")
    public Result<AdminDashboardOverviewVO> overview(
            @RequestParam(value = "date", required = false) String date) {
        LocalDate targetDate = (date == null || date.trim().isEmpty())
                ? LocalDate.now()
                : LocalDate.parse(date.trim());
        log.info("查询管理后台总览: date={}", targetDate);
        return Result.success(adminDashboardService.getOverview(targetDate));
    }
}
