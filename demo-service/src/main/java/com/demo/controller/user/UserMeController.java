package com.demo.controller.user;

import com.demo.dto.user.AvatarUploadConfigRequest;
import com.demo.dto.user.UpdateProfileRequest;
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
    public Result<AvatarUploadConfigVO> getAvatarUploadConfig(@Valid @RequestBody AvatarUploadConfigRequest request) {
        log.info("获取头像上传配置: {}", request);
        return Result.success(userService.generateAvatarUploadConfig(request));
    }
    }

