package com.demo.controller;

import com.demo.dto.auth.*;
import com.demo.result.Result;
import com.demo.service.AuthService;
import com.demo.vo.UserVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;

/**
 * 用户注册、登录入口
 */
@RestController
@RequestMapping("/api/auth")
@Validated
public class AuthController {

    @Autowired
    private AuthService authService;

    @PostMapping("/sms/send")
    public Result<String> sendSmsCode(@Valid @RequestBody SmsCodeRequest request) {
        authService.sendSmsCode(request);
        return Result.success("验证码发送成功，5分钟内有效");
    }

    @PostMapping("/register/phone")
    public Result<UserVO> registerByPhone(@Valid @RequestBody PhoneRegisterRequest request) {
        return Result.success(authService.registerByPhone(request));
    }

    @PostMapping("/register/email")
    public Result<UserVO> registerByEmail(@Valid @RequestBody EmailRegisterRequest request) {
        return Result.success(authService.registerByEmail(request));
    }

    @PostMapping("/register/email/activate")
    public Result<UserVO> activateEmail(@Valid @RequestBody EmailActivationRequest request) {
        return Result.success(authService.activateEmail(request));
    }

    @PostMapping("/login/third-party")
    public Result<AuthResponse> loginWithThirdParty(@Valid @RequestBody ThirdPartyLoginRequest request) {
        return Result.success(authService.loginWithThirdParty(request));
    }
}
