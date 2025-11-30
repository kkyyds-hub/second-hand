package com.demo.controller.user;

import com.demo.dto.auth.*;
import com.demo.dto.user.BindEmailRequest;
import com.demo.dto.user.BindPhoneRequest;
import com.demo.result.Result;
import com.demo.service.AuthService;
import com.demo.vo.UserVO;
import io.swagger.annotations.Api;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
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


    @PostMapping("/sms/send")
    public Result<String> sendSmsCode(@Validated @RequestBody SmsCodeRequest request) {
        log.info("发送短信验证码: {}", request);
        authService.sendSmsCode(request);
        return Result.success("验证码发送成功，5分钟内有效");
    }

    @PostMapping("/register/phone")
    public Result<UserVO> registerByPhone(@Validated  @RequestBody PhoneRegisterRequest request) {
        log.info("手机号注册: {}", request);
        return Result.success(authService.registerByPhone(request));
    }

    @PostMapping("/register/email")
    public Result<UserVO> registerByEmail(@Validated @RequestBody EmailRegisterRequest request) {
        log.info("邮箱注册: {}", request);
        return Result.success(authService.registerByEmail(request));
    }

    @PostMapping("/register/email/activate")
    public Result<UserVO> activateEmail(@Validated @RequestBody EmailActivationRequest request) {
        log.info("邮箱激活: {}", request);
        return Result.success(authService.activateEmail(request));
    }

    @PostMapping("/login/third-party")
    public Result<AuthResponse> thirdPartyLogin(@Validated @RequestBody ThirdPartyLoginRequest request) {
        log.info("第三方登录: {}", request);
        return Result.success(authService.loginWithThirdParty(request));
    }
}
