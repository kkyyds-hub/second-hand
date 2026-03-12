package com.demo.controller.admin;

import com.demo.dto.admin.AdminCreateUserRequest;
import com.demo.dto.user.UserQueryDTO;
import com.demo.result.PageResult;
import com.demo.result.Result;
import com.demo.service.UserService;
import com.demo.vo.UserVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.time.LocalDateTime;

@RestController
@Api(tags = "管理端-用户管理")
@RequestMapping("/admin/user")
@Slf4j
public class UserController {

    @Autowired
    private UserService userService;

    /**
     * 分页查询用户列表。
     * 支持关键字、状态、角色等组合筛选。
     */
    @GetMapping
    @ApiOperation("分页查询用户")
    public Result<PageResult<UserVO>> getUsers(@Valid UserQueryDTO queryDTO) {
        log.info("分页查询用户：page={}, pageSize={}, status={}, role={}",
                queryDTO.getPage(), queryDTO.getPageSize(), queryDTO.getStatus(), queryDTO.getRole());
        return Result.success(userService.getUserPage(queryDTO));
    }

    /**
     * 管理员手动建档。
     * 用于运营在后台直接创建用户基础信息。
     */
    @PostMapping
    @ApiOperation("管理员手动建档")
    public Result<UserVO> createUser(@Valid @RequestBody AdminCreateUserRequest request) {
        log.info("管理员手动建档：role={}", request.getRole());
        return Result.success(userService.createAdminUser(request));
    }

    /**
     * 封禁用户。
     * `reason` 由前端可选传入，便于审计与排查。
     */
    @PutMapping("/{userId}/ban")
    @ApiOperation("封禁用户")
    public Result<String> banUser(@PathVariable Long userId,
                                  @RequestParam(required = false) String reason) {
        log.info("封禁用户：userId={}, reason={}", userId, reason);
        return Result.success(userService.banUser(userId, reason));
    }

    /**
     * 解封用户。
     */
    @PutMapping("/{userId}/unban")
    @ApiOperation("解封用户")
    public Result<String> unbanUser(@PathVariable Long userId) {
        log.info("解封用户：userId={}", userId);
        return Result.success(userService.unbanUser(userId));
    }

    @GetMapping(value = "/export", produces = "text/csv; charset=utf-8")
    @ApiOperation("导出用户 CSV")
    public String exportUsers(@RequestParam(required = false) String keyword,
                              @RequestParam(required = false) String startTime,
                              @RequestParam(required = false) String endTime) {
        log.info("导出用户：keyword={}, startTime={}, endTime={}", keyword, startTime, endTime);

        LocalDateTime start = null;
        LocalDateTime end = null;
        if (startTime != null && !startTime.isEmpty()) {
            start = LocalDateTime.parse(startTime);
        }
        if (endTime != null && !endTime.isEmpty()) {
            end = LocalDateTime.parse(endTime);
        }

        return userService.exportUsersCSV(keyword, start, end);
    }
}
