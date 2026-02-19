package com.demo.controller.admin;

import com.demo.dto.user.UserQueryDTO;
import com.demo.result.PageResult;
import com.demo.result.Result;
import com.demo.service.UserService;
import com.demo.vo.UserVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import javax.validation.Valid;

@RestController
@Api(tags = "用户管理")
@RequestMapping("/admin/user")
@Slf4j
/**
 * UserController 业务组件。
 */
public class UserController {

    @Autowired
    private UserService userService;

    @GetMapping
    @ApiOperation("用户分页查询")
    /**
     * 分页查询用户列表。
     */
    public Result<PageResult<UserVO>> getUsers(@Valid UserQueryDTO queryDTO) {
        log.info("用户分页查询: page={}, pageSize={}", queryDTO.getPage(), queryDTO.getPageSize());
        PageResult<UserVO> pageResult = userService.getUserPage(queryDTO);
        return Result.success(pageResult);
    }

    /**
     * Day13 Step7 - 封禁用户
     * PUT /admin/user/{userId}/ban
     */
    @PutMapping("/{userId}/ban")
    @ApiOperation("封禁用户")
    public Result<String> banUser(@PathVariable Long userId) {
        log.info("管理员封禁用户：userId={}", userId);
        String msg = userService.banUser(userId);
        return Result.success(msg);
    }

    /**
     * Day13 Step7 - 解封用户
     * PUT /admin/user/{userId}/unban
     */
    @PutMapping("/{userId}/unban")
    @ApiOperation("解封用户")
    public Result<String> unbanUser(@PathVariable Long userId) {
        log.info("管理员解封用户：userId={}", userId);
        String msg = userService.unbanUser(userId);
        return Result.success(msg);
    }

    /**
     * Day13 Step7 - 导出用户 CSV
     * GET /admin/user/export?keyword=&startTime=&endTime=
     */
    @GetMapping(value = "/export", produces = "text/csv; charset=utf-8")
    @ApiOperation("导出用户CSV")
    public String exportUsers(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime) {
        log.info("管理员导出用户：keyword={}, startTime={}, endTime={}", keyword, startTime, endTime);

        java.time.LocalDateTime start = null;
        java.time.LocalDateTime end = null;
        if (startTime != null && !startTime.isEmpty()) {
            start = java.time.LocalDateTime.parse(startTime);
        }
        if (endTime != null && !endTime.isEmpty()) {
            end = java.time.LocalDateTime.parse(endTime);
        }

        return userService.exportUsersCSV(keyword, start, end);
    }

}

