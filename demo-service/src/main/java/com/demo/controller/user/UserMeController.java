package com.demo.controller.user;

import com.demo.dto.user.*;
import com.demo.result.Result;
import com.demo.service.UserService;
import com.demo.vo.AvatarUploadConfigVO;
import com.demo.vo.UserVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.validation.Valid;

/**
 * 当前用户个人资料与账号安全相关接口。
 */
@RestController
@RequestMapping("/user/me")
@Validated
@Slf4j
public class UserMeController {

    @Autowired
    private UserService userService;

    /**
     * 更新当前用户的个人资料字段。
     */
    @PatchMapping("/profile")
    public Result<UserVO> updateProfile(@Validated @RequestBody UpdateProfileRequest request) {
        log.info("更新用户信息: {}", request);
        return Result.success(userService.updateProfile(request));
    }

    /**
     * 生成头像上传配置。
     */
    @PostMapping("/upload-config")
    public Result<AvatarUploadConfigVO> getAvatarUploadConfig(@Validated @RequestBody AvatarUploadConfigRequest request) {
        log.info("获取头像上传配置: {}", request);
        return Result.success(userService.generateAvatarUploadConfig(request));
    }

    /**
     * 修改当前用户登录密码。
     */
    @PostMapping("/password")
    public Result<String> changePassword(@Validated @RequestBody ChangePasswordRequest request) {
        log.info("修改密码: {}", request);
        userService.changePassword(request);
        return Result.success("修改密码成功");
    }

    /**
     * 绑定手机号到当前用户账号。
     */
    @PostMapping("/bindings/phone")
    public Result<UserVO> bindPhone(@Validated @RequestBody BindPhoneRequest request) {
        log.info("绑定手机号: {}", request);
        return Result.success(userService.bindPhone(request));
    }

    /**
     * 绑定邮箱到当前用户账号。
     */
    @PostMapping("/bindings/email")
    public Result<UserVO> bindEmail(@Validated @RequestBody BindEmailRequest request) {
        log.info("绑定邮箱: {}", request);
        return Result.success(userService.bindEmail(request));
    }

    /**
     * 解绑当前用户账号的手机号。
     */
    @DeleteMapping("/bindings/phone")
    public Result<String> unbindPhone(@Validated @RequestBody UnbindContactRequest request) {
        log.info("解绑手机号: {}", request);
        userService.unbindPhone(request);
        return Result.success("解绑成功");
    }

    /**
     * 解绑当前用户账号的邮箱。
     */
    @DeleteMapping("/bindings/email")
    public Result<String> unbindEmail(@Valid @RequestBody UnbindContactRequest request) {
        log.info("解绑邮箱: {}", request);
        userService.unbindEmail(request);
        return Result.success("解绑成功");
    }
}
