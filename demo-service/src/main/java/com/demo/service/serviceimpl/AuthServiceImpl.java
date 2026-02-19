package com.demo.service.serviceimpl;

import com.demo.constant.JwtClaimsConstant;
import com.demo.dto.auth.*;
import com.demo.dto.user.PasswordLoginRequest;
import com.demo.entity.User;
import com.demo.entity.UserBan;
import com.demo.enumeration.CreditLevel;
import com.demo.enumeration.CreditReasonType;
import com.demo.enumeration.UserStatus;
import com.demo.exception.BusinessException;
import com.demo.mapper.UserBanMapper;
import com.demo.mapper.UserMapper;
import com.demo.properties.JwtProperties;
import com.demo.service.AuthService;
import com.demo.service.CreditService;
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

/**
 * 认证服务实现。
 * 覆盖注册、登录、激活、风控冻结等能力。
 */
@Service
@Slf4j
public class AuthServiceImpl implements AuthService {

    private static final String SMS_CODE_KEY_PREFIX = "auth:sms:code:";
    private static final String EMAIL_CODE_KEY_PREFIX = "auth:email:code:";
    private static final String SMS_RATE_LIMIT_KEY_PREFIX = "auth:sms:rate:";
    private static final String EMAIL_ACTIVATION_KEY_PREFIX = "auth:email:activation:";
    private static final String THIRD_PARTY_BIND_KEY_PREFIX = "auth:oauth:";
    private static final String LOGIN_FAIL_KEY_PREFIX = "auth:login_fail:"; // 后面拼 userId 或用户名
    private static final int MAX_FAIL_COUNT = 5;      // 允许最大连续失败次数
    private static final int FAIL_WINDOW_MINUTES = 30; // 统计窗口（分钟）


    @Autowired
    private UserMapper userMapper;

    @Autowired
    private CreditService creditService;


    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired(required = false)
    private JavaMailSender mailSender;

    @Autowired
    private JwtProperties jwtProperties;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private UserBanMapper userBanMapper;

    @Setter
    @Value("${spring.mail.username:}")
    private String mailFrom;

    /**
     * 发送短信验证码。
     */
    @Override
    public void sendSmsCode(SmsCodeRequest request) {
        String mobile = request.getMobile();
        enforceSmsRateLimit(mobile);
        String code = generateCode();
        stringRedisTemplate.opsForValue().set(SMS_CODE_KEY_PREFIX + mobile, code, Duration.ofMinutes(5));
        stringRedisTemplate.opsForValue().set(SMS_RATE_LIMIT_KEY_PREFIX + mobile, "1", Duration.ofMinutes(1));
        log.info("向手机号 {} 发送验证码:{}，有效期5分钟", mobile, code);
    }


    /**
     * 手机号注册。
     */
    @Override
    public UserVO registerByPhone(PhoneRegisterRequest request) {
        String mobile = request.getMobile();
        String smsCode = request.getSmsCode();
        String rawPassword = request.getPassword();
        String nickname = request.getNickname();
        validateSmsCode(mobile, smsCode);
        //校验手机号是否被注册
        User existed = userMapper.selectByMobile(mobile);
        if (existed != null) {
            throw new BusinessException("手机号已注册");
        }
        // 3. 构建 User 对象（密码要加密）
        User user = buildBaseUser();
        user.setMobile(mobile);
        user.setNickname(nickname);
        String encodedPassword = passwordEncoder.encode(rawPassword);
        user.setPassword(encodedPassword);
        userMapper.insertUser(user);
        log.info("用户手机号 {} 注册成功，用户 ID={}", mobile, user.getId());
        clearSmsCode(mobile);
        return toUserVO(user);
    }


    /**
     * 邮箱注册。
     */
    @Override
    public UserVO registerByEmail(EmailRegisterRequest request) {
        String email = request.getEmail();
        String emailCode = request.getEmailCode();
        String rawPassword = request.getPassword();
        String nickname = request.getNickname();
        // 1. 校验邮箱验证码
        validateEmailCode(email, emailCode);
        // 2. 校验邮箱是否已被注册
        User existed = userMapper.selectByEmail(email);
        if (existed != null) {
            throw new BusinessException("该邮箱已被注册");
        }

        // 3. 构建 User 对象
        User user = buildBaseUser();
        user.setEmail(email);
        user.setNickname(nickname);
        String encodedPassword = passwordEncoder.encode(rawPassword);
        user.setPassword(encodedPassword);
        userMapper.insertUser(user);
        clearEmailCode(email);
        sendActivationMail(user);
        return toUserVO(user);
    }



    /**
     * 邮箱激活。
     */
    @Override
    public UserVO activateEmail(EmailActivationRequest request) {
        String key = EMAIL_ACTIVATION_KEY_PREFIX + request.getToken();
        String userIdStr = stringRedisTemplate.opsForValue().get(key);
        if (!StringUtils.hasText(userIdStr)) {
            throw new BusinessException("激活链接已失效或不存在，请重新发送");
        }
        Long userId = Long.parseLong(userIdStr);
        userMapper.updateStatus(userId, "active", LocalDateTime.now());
        stringRedisTemplate.delete(key);
        log.info("邮箱激活完成，用户 ID={}", userId);
        User user = userMapper.selectById(userId);
        return toUserVO(user);

    }

    /**
     * 第三方登录。
     */
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

    /**
     * 账号密码登录。
     */
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
        UserStatus status = UserStatus.from(user.getStatus());
        // 先判断状态
        switch (status) {
            case INACTIVE:
                throw new BusinessException("账号未激活，请先完成邮箱激活");
            case BANNED:
                throw new BusinessException("账号已被封禁，如有疑问请联系客服");
            case FROZEN:
                throw new BusinessException("账号已被暂时冻结，请稍后再试或联系管理员");
            case ACTIVE:
            default:
                break;
        }
        
        // 4. 校验密码
        String encodedPassword = user.getPassword();
        if (!passwordEncoder.matches(rawPassword, encodedPassword)) {
            recordLoginFail(user.getId(), loginId);
            handleLoginRiskOnFail(user);
            throw new BusinessException("用户名或密码错误");
        }
        resetLoginFailCounter(user);
        // 6. 生成 JWT 并返回
        String token = buildJwt(user);        // 这里重用你已经写好的方法
        UserVO userVO = toUserVO(user);
        return new AuthResponse(token, userVO);
    }

    private void recordLoginFail(Long userId, String identifier) {
        String key = LOGIN_FAIL_KEY_PREFIX + (userId != null ? userId : identifier);
        Long count = stringRedisTemplate.opsForValue().increment(key);
        if (count == 1) {
            // 第一次失败时设置过期时间
            stringRedisTemplate.expire(key, Duration.ofMinutes(FAIL_WINDOW_MINUTES));
        }
    }

    private void resetLoginFailCounter(User user) {
        String key = LOGIN_FAIL_KEY_PREFIX + user.getId();
        stringRedisTemplate.delete(key);
    }

    private void handleLoginRiskOnFail(User user) {
        if (user == null) {
            return;
        }
        String key = LOGIN_FAIL_KEY_PREFIX + user.getId();
        String value = stringRedisTemplate.opsForValue().get(key);
        if (value == null) return;

        int count = Integer.parseInt(value);
        if (count >= MAX_FAIL_COUNT) {
            // 触发风控：临时冻结账号
            userMapper.updateStatus(user.getId(), UserStatus.FROZEN.name(), LocalDateTime.now());
            // 记录封禁记录（临时封锁，来源 AUTO_RISK）
            insertAutoRiskBan(user.getId(), "登录失败次数过多自动冻结");
        }
    }

    private void insertAutoRiskBan(Long userId, String reason) {
        LocalDateTime  now = LocalDateTime.now();
        UserBan ban = new UserBan();
        ban.setUserId(userId);
        ban.setBanType("TEMP");             // 此处先做临时封禁，如需永久封禁改为 "PERM"
        ban.setReason(reason);
        ban.setSource("AUTO_RISK");         // 表明是风控系统自动生成
        ban.setStartTime(now);
        //冻结 1 小时
        ban.setEndTime(now.plusHours(1));
        ban.setCreatedBy(null);             // 自动封禁，没有操作人
        ban.setCreateTime(now);

        userBanMapper.insertUserBan(ban);
        // 封禁记录插入后，active_bans 统计会变化，立即重算
        creditService.recalcUserCredit(userId, CreditReasonType.BAN_ACTIVE, ban.getId());

    }


    /**
     * 校验短信发送频率限制。
     */
    private void enforceSmsRateLimit(String mobile) {
        String rateKey = SMS_RATE_LIMIT_KEY_PREFIX + mobile;
        if (stringRedisTemplate.hasKey(rateKey)) {
            throw new BusinessException("验证码发送过于频繁，请稍后再试");
        }
    }

    /**
     * 校验短信验证码。
     */
    private void validateSmsCode(String mobile, String code) {
        String cacheCode = stringRedisTemplate.opsForValue().get(SMS_CODE_KEY_PREFIX + mobile);
        if (!StringUtils.hasText(cacheCode) || !cacheCode.equals(code)) {
            throw new BusinessException("验证码错误或已过期");
        }
    }
    /**
     * 校验邮箱验证码。
     */
    private void validateEmailCode(String email, String code) {
        String cacheCode = stringRedisTemplate.opsForValue().get(EMAIL_CODE_KEY_PREFIX + email);
        if (!StringUtils.hasText(cacheCode) || !cacheCode.equals(code)) {
            throw new BusinessException("验证码错误或已过期");
        }
    }

    /**
     * 生成 6 位数字验证码。
     */
    private String generateCode() {

        return String.format("%06d", ThreadLocalRandom.current().nextInt(0, 1000000));
    }

    private User buildBaseUser() {
        User user = new User();
        LocalDateTime now = LocalDateTime.now();

        int defaultScore = 100;
        user.setCreditScore(defaultScore);
        user.setCreditLevel(CreditLevel.fromScore(defaultScore).getDbValue()); // 新增
        user.setCreditUpdatedAt(now);                                         // 新增
        user.setStatus("active");                                             // 强烈建议新增（insertUser 会写 status）

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
    private void clearEmailCode(String email) {
        stringRedisTemplate.delete(EMAIL_CODE_KEY_PREFIX + email);
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
        message.setText("请在24小时内点击以下链接激活账号 " +
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


