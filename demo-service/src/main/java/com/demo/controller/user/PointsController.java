package com.demo.controller.user;

import com.demo.context.BaseContext;
import com.demo.entity.PointsLedger;
import com.demo.result.PageResult;
import com.demo.result.Result;
import com.demo.service.PointsService;
import io.swagger.annotations.Api;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * Day13 Step8 - 用户积分接口
 */
@RestController
@RequestMapping("/user/points")
@Api(tags = "用户积分接口")
@Slf4j
public class PointsController {

    @Autowired
    private PointsService pointsService;

    /**
     * 查询积分总额
     * GET /user/points/total
     */
    @GetMapping("/total")
    public Result<Integer> getTotalPoints() {
        Long currentUserId = BaseContext.getCurrentId();
        log.info("查询积分总额：userId={}", currentUserId);
        Integer total = pointsService.getTotalPoints(currentUserId);
        return Result.success(total);
    }

    /**
     * 查询积分流水（分页）
     * GET /user/points/ledger?page=1&pageSize=10
     */
    @GetMapping("/ledger")
    public Result<PageResult<PointsLedger>> listPoints(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        Long currentUserId = BaseContext.getCurrentId();
        log.info("查询积分流水：userId={}, page={}, pageSize={}", currentUserId, page, pageSize);
        PageResult<PointsLedger> result = pointsService.listPoints(currentUserId, page, pageSize);
        return Result.success(result);
    }
}
