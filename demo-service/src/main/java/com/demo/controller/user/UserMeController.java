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

@RestController
@RequestMapping("/user/me")
@Validated
@Slf4j
public class UserMeController {

    @Autowired
    private UserService userService;
    @PatchMapping("/profile")
    public Result<UserVO> updateProfile(@Validated @RequestBody UpdateProfileRequest request) {
        log.info("更新用户信息: {}", request);
        return Result.success(userService.updateProfile(request));
    }
    @PostMapping("/upload-config")
    public Result<AvatarUploadConfigVO> getAvatarUploadConfig(@Validated @RequestBody AvatarUploadConfigRequest request) {
        log.info("获取头像上传配置: {}", request);
        return Result.success(userService.generateAvatarUploadConfig(request));
    }

    @PostMapping("/password")
    public Result<String> changePassword(@Validated @RequestBody ChangePasswordRequest request) {
        log.info("修改密码: {}", request);
        userService.changePassword(request);
        return Result.success("修改密码成功");
    }
    @PostMapping("/bindings/phone")
    public Result<UserVO> bindPhone(@Validated @RequestBody BindPhoneRequest request) {
        log.info("绑定手机号: {}", request);
        return Result.success(userService.bindPhone(request));
    }

    @PostMapping("/bindings/email")
    public Result<UserVO> bindEmail(@Validated @RequestBody BindEmailRequest request) {
        log.info("绑定邮箱: {}", request);
        return Result.success(userService.bindEmail(request));
    }

    @DeleteMapping("/bindings/phone")
    public Result<String> unbindPhone(@Valid @RequestBody UnbindContactRequest request) {
        log.info("解绑手机号: {}", request);
        userService.unbindPhone(request);
        return Result.success("解绑成功");
    }

    @DeleteMapping("/bindings/email")
    public Result<String> unbindEmail(@Valid @RequestBody UnbindContactRequest request) {
        log.info("解绑邮箱: {}", request);
        userService.unbindEmail(request);
        return Result.success("解绑成功");
    }
    }

