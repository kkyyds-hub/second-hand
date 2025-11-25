package com.demo.service.serviceimpl;

import com.demo.dto.auth.*;
import com.demo.service.AuthService;
import com.demo.vo.UserVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class AuthServiceImpl implements AuthService {
    @Override
    public void sendSmsCode(SmsCodeRequest request) {

    }

    @Override
    public UserVO registerByPhone(PhoneRegisterRequest request) {
        return null;
    }

    @Override
    public UserVO registerByEmail(EmailRegisterRequest request) {
        return null;
    }

    @Override
    public UserVO activateEmail(EmailActivationRequest request) {
        return null;
    }

    @Override
    public AuthResponse loginWithThirdParty(ThirdPartyLoginRequest request) {
        return null;
    }
}
