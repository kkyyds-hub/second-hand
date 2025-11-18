package com.demo.controller.admin;

import com.demo.dto.base.BanRequest;
import com.demo.result.Result;
import com.demo.service.UserService;
import com.demo.service.ViolationService;
import io.swagger.annotations.Api;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;

@RestController
@Api(tags = "封禁管理")
@RequestMapping("/admin/users")
@Slf4j
public class ViolationController {
    @Autowired
    private ViolationService ViolationService;

    @PostMapping("/{userId}/ban")
    public Result<String> banUser(@PathVariable Long userId,
                                  @Valid @RequestBody BanRequest request) {

        ViolationService.banUser(userId, request.getReason());
        return Result.success("用户封禁成功");
    }

    @PostMapping("/{userId}/unban")
    public Result<String> unbanUser(@PathVariable Long userId) {
        log.info("用户解封: userId={}", userId);
        try {
            ViolationService.unbanUser(userId);
            return Result.success("用户解封成功");
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }

    }

}
