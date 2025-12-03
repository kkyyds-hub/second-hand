package com.demo.service.serviceimpl;

import com.demo.constant.JwtClaimsConstant;
import com.demo.dto.auth.*;
import com.demo.dto.user.PasswordLoginRequest;
import com.demo.entity.User;
import com.demo.exception.BusinessException;
import com.demo.exception.RegistrationException;
import com.demo.mapper.UserMapper;
import com.demo.properties.JwtProperties;
import com.demo.service.AuthService;
import com.demo.utils.JwtUtil;
import com.demo.vo.UserVO;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Service
@Slf4j
public class AuthServiceImpl implements AuthService {

    private static final String SMS_CODE_KEY_PREFIX = "auth:sms:code:";
    private static final String SMS_RATE_LIMIT_KEY_PREFIX = "auth:sms:rate:";
    private static final String EMAIL_ACTIVATION_KEY_PREFIX = "auth:email:activation:";
    private static final String THIRD_PARTY_BIND_KEY_PREFIX = "auth:oauth:";

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired(required = false)
    private JavaMailSender mailSender;

    @Autowired
    private JwtProperties jwtProperties;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Setter
    @Value("${spring.mail.username:}")
    private String mailFrom;

    @Override
    public void sendSmsCode(SmsCodeRequest request) {
        String mobile = request.getMobile();
        enforceSmsRateLimit(mobile);
        String code = generateCode();
        stringRedisTemplate.opsForValue().set(SMS_CODE_KEY_PREFIX + mobile, code, Duration.ofMinutes(5));
        stringRedisTemplate.opsForValue().set(SMS_RATE_LIMIT_KEY_PREFIX + mobile, "1", Duration.ofMinutes(1));
        log.info("向手机号 {} 发送验证码:{}，有效期5分钟", mobile, code);
    }


    @Override
    public UserVO registerByPhone(PhoneRegisterRequest request) {
        String mobile = request.getMobile();
        validateSmsCode(mobile, request.getCode());
        if (userMapper.selectByMobile(mobile) != null) {
            throw new RegistrationException("手机号已存在，请直接登录");
        }
        User user = buildBaseUser();
        user.setMobile(mobile);
        user.setUsername("u" + request.getMobile());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setStatus("active");
        userMapper.insertUser(user);
        log.info("用户 {} 注册成功", user.getUsername());
        clearSmsCode(mobile);
        return toUserVO(user);
    }


    @Override
    public UserVO registerByEmail(EmailRegisterRequest request) {
        if (userMapper.selectByEmail(request.getEmail()) != null) {
            throw new RegistrationException("该邮箱已注册，请直接登录");
        }

        User user = buildBaseUser();
        user.setEmail(request.getEmail());
        user.setUsername(request.getEmail());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setStatus("pending_activation");

        userMapper.insertUser(user);
        sendActivationMail(user);
        return toUserVO(user);
    }


    @Override
    public UserVO activateEmail(EmailActivationRequest request) {
        String key = EMAIL_ACTIVATION_KEY_PREFIX + request.getToken();
        String userIdStr = stringRedisTemplate.opsForValue().get(key);
        if (!StringUtils.hasText(userIdStr)) {
            throw new RegistrationException("激活链接已失效或不存在，请重新发送");
        }
        Long userId = Long.parseLong(userIdStr);
        userMapper.updateStatus(userId, "active", LocalDateTime.now());
        stringRedisTemplate.delete(key);
        log.info("邮箱激活完成，用户ID={}", userId);
        User user = userMapper.selectById(userId);
        return toUserVO(user);

    }

    @Override
    public AuthResponse loginWithThirdParty(ThirdPartyLoginRequest request) {
        String provider = request.getProvider().toLowerCase();
        String externalId = resolveExternalId(request);
        String redisKey = THIRD_PARTY_BIND_KEY_PREFIX + provider + ":" + externalId;
        Long userId;
        String cachedUserId = stringRedisTemplate.opsForValue().get(redisKey);
        if (StringUtils.hasText(cachedUserId)) {
            userId = Long.valueOf(cachedUserId);
        } else {
            User newUser = createThirdPartyUser(provider, externalId);
            userId = newUser.getId();
            stringRedisTemplate.opsForValue().set(redisKey, String.valueOf(userId));
        }

        User user = userMapper.selectById(userId);
        UserVO userVO = toUserVO(user);
        String token = buildJwt(user);
        return new AuthResponse(token, userVO);
    }

    @Override
    public AuthResponse loginWithPassword(PasswordLoginRequest request) {
        String loginId = request.getLoginId();
        String rawPassword = request.getPassword();

        // 1. 先按手机号查
        User user = userMapper.selectByMobile(loginId);
        // 2. 如果手机号没查到，再按邮箱查
        if (user == null) {
            user = userMapper.selectByEmail(loginId);
        }

        // 3. 用户不存在
        if (user == null) {
            throw new BusinessException("用户名或密码错误");
        }

        // 4. 校验密码 —— 这里有两种情况：
        // 情况 A：你已经把密码用 BCrypt 加密后存库（推荐正式做法）
        String encodedPassword = user.getPassword();
        if (!passwordEncoder.matches(rawPassword, encodedPassword)) {
            throw new BusinessException("用户名或密码错误");
        }

        // 如果你现在库里还都是明文密码，可以在学习阶段先用 equals：
        // if (!Objects.equals(rawPassword, user.getPassword())) {
        //     throw new BusinessException("用户名或密码错误");
        // }

        // 5.（可选）校验账号状态
        // if ("DISABLED".equals(user.getStatus())) {
        //     throw new BusinessException("账号已被禁用");
        // }

        // 6. 生成 JWT 并返回
        String token = buildJwt(user);        // 这里重用你已经写好的方法
        UserVO userVO = toUserVO(user);
        return new AuthResponse(token, userVO);
    }




    private void enforceSmsRateLimit(String mobile) {
        String rateKey = SMS_RATE_LIMIT_KEY_PREFIX + mobile;
        if (stringRedisTemplate.hasKey(rateKey)) {
            throw new RegistrationException("验证码发送过于频繁，请稍后再试");
        }
    }

    private void validateSmsCode(String mobile, String code) {
        String cacheCode = stringRedisTemplate.opsForValue().get(SMS_CODE_KEY_PREFIX + mobile);
        if (!StringUtils.hasText(cacheCode) || !cacheCode.equals(code)) {
            throw new RegistrationException("验证码错误或已过期");
        }
    }
    private String generateCode() {
        return String.format("%06d", ThreadLocalRandom.current().nextInt(0, 1000000));
    }

    private User buildBaseUser() {
        User user = new User();
        user.setCreditScore(100);
        LocalDateTime now = LocalDateTime.now();
        user.setCreateTime(now);
        user.setUpdateTime(now);
        return user;
    }

    private UserVO toUserVO(User user) {
        UserVO userVO = new UserVO();
        BeanUtils.copyProperties(user, userVO);
        return userVO;
    }

    private void clearSmsCode(String mobile) {
        stringRedisTemplate.delete(SMS_CODE_KEY_PREFIX + mobile);
        stringRedisTemplate.delete(SMS_RATE_LIMIT_KEY_PREFIX + mobile);
    }

    private void sendActivationMail(User user) {
        if (mailSender == null || !StringUtils.hasText(mailFrom)) {
            log.warn("未配置邮件发送服务，跳过激活邮件发送");
            return;
        }
        String token = UUID.randomUUID().toString().replace("-", "");
        stringRedisTemplate.opsForValue().set(EMAIL_ACTIVATION_KEY_PREFIX + token,
                String.valueOf(user.getId()), Duration.ofHours(24));

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(mailFrom);
        message.setTo(user.getEmail());
        message.setSubject("账户激活通知");
        message.setText("请在24小时内点击以下链接激活账户: " +
                "https://example.com/activate?token=" + token);
        mailSender.send(message);
        log.info("发送激活邮件到:{}，token={}，有效期24小时", user.getEmail(), token);
    }
    private String resolveExternalId(ThirdPartyLoginRequest request) {
        if (StringUtils.hasText(request.getExternalId())) {
            return request.getExternalId();
        }
        return request.getProvider() + ":" + request.getAuthorizationCode();
    }
    private String buildJwt(User user) {
        Map<String, Object> claims = new HashMap<>();
        claims.put(JwtClaimsConstant.USER_ID, user.getId());
        return JwtUtil.createJWT(jwtProperties.getUserSecretKey(), jwtProperties.getUserTtl(), claims);
    }
    private User createThirdPartyUser(String provider, String externalId) {
        User user = buildBaseUser();
        user.setUsername(provider + "_" + externalId.substring(0, Math.min(12, externalId.length())));
        user.setStatus("active");
        userMapper.insertUser(user);
        log.info("为第三方账号 {}-{} 创建新用户，ID={}", provider, externalId, user.getId());
        return user;
    }

}
