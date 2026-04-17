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
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import javax.servlet.http.HttpServletRequest;
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
        log.info("更新用户信息: hasNickname={}, hasAvatar={}, hasBio={}",
                request.getNickname() != null,
                request.getAvatar() != null,
                request.getBio() != null);
        return Result.success(userService.updateProfile(request));
    }

    /**
     * 生成头像上传配置。
     */
    @PostMapping("/upload-config")
    public Result<AvatarUploadConfigVO> getAvatarUploadConfig(@Validated @RequestBody AvatarUploadConfigRequest request) {
        log.info("获取头像上传配置: fileName={}, contentType={}", request.getFileName(), request.getContentType());
        return Result.success(userService.generateAvatarUploadConfig(request, currentBaseUrl()));
    }

    /**
     * 上传头像到本地文件存储。
     */
    @PutMapping("/avatar/upload")
    public Result<String> uploadAvatar(@Validated AvatarUploadTicketRequest request,
                                       HttpServletRequest httpServletRequest) {
        log.info("上传头像: key={}, contentType={}, contentLength={}",
                request.getKey(),
                httpServletRequest.getContentType(),
                httpServletRequest.getContentLengthLong());
        return Result.success(userService.uploadAvatar(
                request,
                httpServletRequest.getContentType(),
                httpServletRequest.getContentLengthLong(),
                safeInputStream(httpServletRequest),
                currentBaseUrl()
        ));
    }

    /**
     * 修改当前用户登录密码。
     */
    @PostMapping("/password")
    public Result<String> changePassword(@Validated @RequestBody ChangePasswordRequest request) {
        boolean hasOldPassword = (request.getOldPassword() != null && !request.getOldPassword().isEmpty())
                || (request.getCurrentPassword() != null && !request.getCurrentPassword().isEmpty());
        log.info("修改密码: verifyChannel={}, hasOldPassword={}, hasCode={}",
                request.getVerifyChannel(),
                hasOldPassword,
                request.getCode() != null && !request.getCode().isEmpty());
        userService.changePassword(request);
        return Result.success("修改密码成功");
    }

    /**
     * 绑定手机号到当前用户账号。
     */
    @PostMapping("/bindings/phone")
    public Result<UserVO> bindPhone(@Validated @RequestBody BindPhoneRequest request) {
        log.info("绑定手机号: verifyCodeLen={}, targetValueLen={}",
                request.getVerifyCode() == null ? 0 : request.getVerifyCode().length(),
                request.getValue() == null ? 0 : request.getValue().length());
        return Result.success(userService.bindPhone(request));
    }

    /**
     * 绑定邮箱到当前用户账号。
     */
    @PostMapping("/bindings/email")
    public Result<UserVO> bindEmail(@Validated @RequestBody BindEmailRequest request) {
        log.info("绑定邮箱: verifyCodeLen={}, targetValueLen={}",
                request.getVerifyCode() == null ? 0 : request.getVerifyCode().length(),
                request.getValue() == null ? 0 : request.getValue().length());
        return Result.success(userService.bindEmail(request));
    }

    /**
     * 解绑当前用户账号的手机号。
     */
    @DeleteMapping("/bindings/phone")
    public Result<String> unbindPhone(@Validated @RequestBody UnbindContactRequest request) {
        log.info("解绑手机号: verifyChannel={}, hasCurrentPassword={}, hasVerifyCode={}",
                request.getVerifyChannel(),
                request.getCurrentPassword() != null && !request.getCurrentPassword().isEmpty(),
                request.getVerifyCode() != null && !request.getVerifyCode().isEmpty());
        userService.unbindPhone(request);
        return Result.success("解绑成功");
    }

    /**
     * 解绑当前用户账号的邮箱。
     */
    @DeleteMapping("/bindings/email")
    public Result<String> unbindEmail(@Valid @RequestBody UnbindContactRequest request) {
        log.info("解绑邮箱: verifyChannel={}, hasCurrentPassword={}, hasVerifyCode={}",
                request.getVerifyChannel(),
                request.getCurrentPassword() != null && !request.getCurrentPassword().isEmpty(),
                request.getVerifyCode() != null && !request.getVerifyCode().isEmpty());
        userService.unbindEmail(request);
        return Result.success("解绑成功");
    }

    private String currentBaseUrl() {
        String baseUrl = ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString();
        // Vite proxy may forward requests with a synthetic "/api" prefix; upload URLs must point to real backend routes.
        if (baseUrl.endsWith("/api")) {
            return baseUrl.substring(0, baseUrl.length() - "/api".length());
        }
        return baseUrl;
    }

    private java.io.InputStream safeInputStream(HttpServletRequest request) {
        try {
            return request.getInputStream();
        } catch (java.io.IOException ex) {
            throw new IllegalStateException("read avatar upload stream failed", ex);
        }
    }
}
