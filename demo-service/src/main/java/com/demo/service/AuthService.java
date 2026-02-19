package com.demo.service;

import com.demo.dto.auth.*;
import com.demo.dto.user.PasswordLoginRequest;
import com.demo.vo.UserVO;

/**
 * AuthService 接口。
 */
public interface AuthService {

    /**
     * 发送短信验证码
     */
    void sendSmsCode(SmsCodeRequest request);

    /**
     * 使用手机号和验证码注册
     */
    UserVO registerByPhone(PhoneRegisterRequest request);

    /**
     * 通过邮箱注册并发送激活邮件
     */
    UserVO registerByEmail(EmailRegisterRequest request);

    /**
     * 激活邮箱账号
     */
    UserVO activateEmail(EmailActivationRequest request);

    /**
     * 第三方快捷登录
     */
    AuthResponse loginWithThirdParty(ThirdPartyLoginRequest request);

    /**
     * 登录
     */
    AuthResponse loginWithPassword(PasswordLoginRequest request);

}
