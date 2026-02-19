package com.demo.controller.user;

import com.demo.context.BaseContext;
import com.demo.dto.user.UserCreditDTO;
import com.demo.dto.user.UserCreditLogDTO;
import com.demo.result.Result;
import com.demo.service.CreditService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 用户信用分查询接口。
 */
@RestController
@Api(tags = "用户信用")
@RequestMapping("/user/credit")
public class UserCreditController {

    @Autowired
    private CreditService creditService;

    /**
     * 查询当前用户信用分概览。
     */
    @GetMapping
    @ApiOperation("查询我的信用信息")
    public Result<UserCreditDTO> myCredit() {
        Long userId = BaseContext.getCurrentId();
        return Result.success(creditService.getCredit(userId));
    }

    /**
     * 查询当前用户信用分流水，支持限制返回条数。
     */
    @GetMapping("/logs")
    @ApiOperation("查询我的信用流水")
    public Result<List<UserCreditLogDTO>> myCreditLogs(@RequestParam(required = false, defaultValue = "50") Integer limit) {
        Long userId = BaseContext.getCurrentId();
        return Result.success(creditService.listLogs(userId, limit));
    }
}
