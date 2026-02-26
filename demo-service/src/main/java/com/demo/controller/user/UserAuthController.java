package com.demo.controller.user;

import com.demo.dto.auth.*;
import com.demo.dto.user.BindEmailRequest;
import com.demo.dto.user.BindPhoneRequest;
import com.demo.dto.user.PasswordLoginRequest;
import com.demo.result.Result;
import com.demo.service.AuthService;
import com.demo.vo.UserVO;
import io.swagger.annotations.Api;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户注册、登录入口
 */
@RestController
@RequestMapping("/user/auth")
@Validated
@Slf4j
@Api(tags = "用户认证")
public class UserAuthController {

    @Autowired
    private AuthService authService;


    /**
     * 发送短信验证码。
     */
    @PostMapping("/sms/send")
    public Result<String> sendSmsCode(@Validated @RequestBody SmsCodeRequest request) {
        log.info("发送短信验证码: mobile={}", maskMobile(request.getMobile()));
        authService.sendSmsCode(request);
        return Result.success("验证码发送成功，5分钟内有效");
    }

    /**
     * 手机号注册。
     */
    @PostMapping("/register/phone")
    public Result<UserVO> registerByPhone(@Validated  @RequestBody PhoneRegisterRequest request) {
        log.info("手机号注册: mobile={}, nicknameLength={}",
                maskMobile(request.getMobile()),
                request.getNickname() == null ? 0 : request.getNickname().length());
        return Result.success(authService.registerByPhone(request));
    }

    /**
     * 邮箱注册。
     */
    @PostMapping("/register/email")
    public Result<UserVO> registerByEmail(@Validated @RequestBody EmailRegisterRequest request) {
        log.info("邮箱注册: email={}, nicknameLength={}",
                maskEmail(request.getEmail()),
                request.getNickname() == null ? 0 : request.getNickname().length());
        return Result.success(authService.registerByEmail(request));
    }

    /**
     * 邮箱激活。
     */
    @PostMapping("/register/email/activate")
    public Result<UserVO> activateEmail(@Validated @RequestBody EmailActivationRequest request) {
        log.info("邮箱激活: tokenLen={}", request.getToken() == null ? 0 : request.getToken().length());
        return Result.success(authService.activateEmail(request));
    }

    /**
     * 第三方登录。
     */
    @PostMapping("/login/third-party")
    public Result<AuthResponse> thirdPartyLogin(@Validated @RequestBody ThirdPartyLoginRequest request) {
        log.info("第三方登录: provider={}, externalIdLen={}, authCodeLen={}",
                request.getProvider(),
                request.getExternalId() == null ? 0 : request.getExternalId().length(),
                request.getAuthorizationCode() == null ? 0 : request.getAuthorizationCode().length());
        return Result.success(authService.loginWithThirdParty(request));
    }

    /**
     * 账号密码登录。
     */
    @PostMapping("/login/password")
    public Result<AuthResponse> loginWithPassword(@Validated @RequestBody PasswordLoginRequest request) {
        return Result.success(authService.loginWithPassword(request));
    }

    private String maskMobile(String mobile) {
        if (!StringUtils.hasText(mobile)) {
            return "EMPTY";
        }
        String trimmed = mobile.trim();
        if (trimmed.length() <= 7) {
            return "***";
        }
        return trimmed.substring(0, 3) + "****" + trimmed.substring(trimmed.length() - 4);
    }

    private String maskEmail(String email) {
        if (!StringUtils.hasText(email)) {
            return "EMPTY";
        }
        String trimmed = email.trim();
        int at = trimmed.indexOf('@');
        if (at <= 1) {
            return "***@" + (at >= 0 ? trimmed.substring(at + 1) : "***");
        }
        String domain = trimmed.substring(at + 1);
        return trimmed.substring(0, 1) + "***@" + domain;
    }
}
