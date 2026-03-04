package com.demo.service.serviceimpl;

import com.demo.audit.AuditLogUtil;
import com.demo.constant.JwtClaimsConstant;
import com.demo.dto.auth.*;
import com.demo.dto.user.PasswordLoginRequest;
import com.demo.entity.User;
import com.demo.entity.UserBan;
import com.demo.entity.UserOauthBind;
import com.demo.enumeration.CreditLevel;
import com.demo.enumeration.CreditReasonType;
import com.demo.enumeration.UserStatus;
import com.demo.exception.BusinessException;
import com.demo.mapper.UserBanMapper;
import com.demo.mapper.UserMapper;
import com.demo.mapper.UserOauthBindMapper;
import com.demo.properties.JwtProperties;
import com.demo.security.InputSecurityGuard;
import com.demo.service.AuthService;
import com.demo.service.CreditService;
import com.demo.utils.JwtUtil;
import com.demo.vo.UserVO;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Locale;
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
    private static final String OAUTH_CACHE_DB_TAG = "db:"; // 新口径缓存值：db:{userId}
    private static final long OAUTH_CACHE_TTL_DAYS = 30L;   // 避免永久脏缓存，周期性回源 DB 复核
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

    @Autowired
    private UserOauthBindMapper userOauthBindMapper;

    @Autowired
    private TransactionTemplate transactionTemplate;

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
        log.info("向手机号 {} 发送验证码，有效期5分钟", maskMobile(mobile));
    }


    /**
     * 手机号注册。
     */
    @Override
    public UserVO registerByPhone(PhoneRegisterRequest request) {
        String mobile = request.getMobile();
        String smsCode = request.getSmsCode();
        String rawPassword = request.getPassword();
        // Day18 P3-S2：昵称属于可展示文本，注册入口统一做输入安全守卫。
        String nickname = InputSecurityGuard.normalizePlainText(request.getNickname(), "昵称", 20, true);
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
        log.info("用户手机号 {} 注册成功，用户 ID={}", maskMobile(mobile), user.getId());
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
        // Day18 P3-S2：与手机号注册保持同口径，避免昵称字段治理不一致。
        String nickname = InputSecurityGuard.normalizePlainText(request.getNickname(), "昵称", 20, true);
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
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        int rows = userMapper.updateStatusByExpected(userId, "inactive", "active");
        if (rows == 0 && !"active".equalsIgnoreCase(user.getStatus())) {
            User latest = userMapper.selectById(userId);
            if (latest == null) {
                throw new BusinessException("用户不存在");
            }
            if (!"active".equalsIgnoreCase(latest.getStatus())) {
                throw new BusinessException("当前账号状态不允许激活：" + latest.getStatus());
            }
        }
        stringRedisTemplate.delete(key);
        log.info("邮箱激活完成，用户 ID={}", userId);
        User latestUser = userMapper.selectById(userId);
        return toUserVO(latestUser);

    }

    /**
     * 第三方登录。
     */
    @Override
    public AuthResponse loginWithThirdParty(ThirdPartyLoginRequest request) {
        // provider 统一规范化（大小写口径收口），避免 "GitHub" 与 "github" 产生双份绑定。
        String provider = InputSecurityGuard.normalizePlainText(request.getProvider(), "provider", 32, true)
                .toLowerCase(Locale.ROOT);
        String externalId = resolveExternalId(request);
        String redisKey = buildThirdPartyCacheKey(provider, externalId);

        // 第一优先：Redis 加速路径。
        // 说明：只要缓存命中且 user 存在，就不走 DB 绑定查询。
        // 对“历史 Redis-only 旧值（纯数字）”做一次懒迁移，迁移完成后改写为 db:{userId} 新格式。
        User cachedUser = tryLoginByOauthCache(redisKey, provider, externalId);
        if (cachedUser != null) {
            return buildAuthResponse(cachedUser);
        }

        // 第二优先：DB 真源查询（provider + externalId）。
        User dbBindUser = loadUserByOauthBind(provider, externalId, redisKey);
        if (dbBindUser != null) {
            return buildAuthResponse(dbBindUser);
        }

        // 最后一段：首次登录（Redis/DB 都没有绑定）。
        // 使用事务模板保证“创建用户 + 插入绑定”原子性，避免并发下出现孤儿用户。
        User createdUser = createAndBindThirdPartyUserWithRaceRecover(provider, externalId, redisKey);
        return buildAuthResponse(createdUser);
    }

    /**
     * 账号密码登录。
     */
    @Override
    public AuthResponse loginWithPassword(PasswordLoginRequest request) {
        String auditId = AuditLogUtil.newAuditId();
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
            AuditLogUtil.failed(log, auditId, "USER_LOGIN", "USER", loginId, "ACCOUNT", "-", "ACCOUNT_NOT_FOUND", "user not found by loginId");
            throw new BusinessException("用户名或密码错误");
        }
        UserStatus status = UserStatus.from(user.getStatus());
        // 先判断状态
        switch (status) {
            case INACTIVE:
                AuditLogUtil.failed(log, auditId, "USER_LOGIN", "USER", String.valueOf(user.getId()), "ACCOUNT", String.valueOf(user.getId()), "ACCOUNT_INACTIVE", "user inactive");
                throw new BusinessException("账号未激活，请先完成邮箱激活");
            case BANNED:
                AuditLogUtil.failed(log, auditId, "USER_LOGIN", "USER", String.valueOf(user.getId()), "ACCOUNT", String.valueOf(user.getId()), "ACCOUNT_BANNED", "user banned");
                throw new BusinessException("账号已被封禁，如有疑问请联系客服");
            case FROZEN:
                AuditLogUtil.failed(log, auditId, "USER_LOGIN", "USER", String.valueOf(user.getId()), "ACCOUNT", String.valueOf(user.getId()), "ACCOUNT_FROZEN", "user frozen");
                throw new BusinessException("账号已被暂时冻结，请稍后再试或联系管理员");
            case ACTIVE:
            default:
                break;
        }
        
        // 4. 校验密码
        String encodedPassword = user.getPassword();
        if (!passwordEncoder.matches(rawPassword, encodedPassword)) {
            // 先递增失败计数，再按最新计数判断是否触发冻结。
            long failCount = recordLoginFail(user.getId(), loginId);
            handleLoginRiskOnFail(user, auditId, failCount);
            AuditLogUtil.failed(log, auditId, "USER_LOGIN", "USER", String.valueOf(user.getId()), "ACCOUNT", String.valueOf(user.getId()), "PASSWORD_MISMATCH", "password mismatch");
            throw new BusinessException("用户名或密码错误");
        }
        resetLoginFailCounter(user);
        // 6. 生成 JWT 并返回
        String token = buildJwt(user);        // 这里重用你已经写好的方法
        UserVO userVO = toUserVO(user);
        AuditLogUtil.success(log, auditId, "USER_LOGIN", "USER", String.valueOf(user.getId()), "ACCOUNT", String.valueOf(user.getId()), "SUCCESS", "user login success");
        return new AuthResponse(token, userVO);
    }

    /**
     * 记录一次登录失败并返回当前窗口内失败次数。
     *
     * 说明：
     * 1) key 采用 userId（优先）保证同账号聚合；
     * 2) 第一次失败时设置窗口 TTL；
     * 3) 返回值用于后续风控判定与审计字段输出。
     */
    private long recordLoginFail(Long userId, String identifier) {
        String key = LOGIN_FAIL_KEY_PREFIX + (userId != null ? userId : identifier);
        Long count = stringRedisTemplate.opsForValue().increment(key);
        long safeCount = count == null ? 0L : count;
        if (count == 1) {
            // 第一次失败时设置过期时间
            stringRedisTemplate.expire(key, Duration.ofMinutes(FAIL_WINDOW_MINUTES));
        }
        return safeCount;
    }

    private void resetLoginFailCounter(User user) {
        String key = LOGIN_FAIL_KEY_PREFIX + user.getId();
        stringRedisTemplate.delete(key);
    }

    /**
     * 登录失败后的自动风控处理。
     *
     * @param user      当前登录用户
     * @param auditId   本次登录动作 auditId（用于串联 USER_LOGIN 与冻结事件）
     * @param failCount 当前失败窗口累计次数
     */
    private void handleLoginRiskOnFail(User user, String auditId, long failCount) {
        if (user == null) {
            return;
        }
        if (failCount >= MAX_FAIL_COUNT) {
            // 触发风控：仅 active -> frozen（CAS），避免覆盖并发状态迁移。
            int rows = userMapper.updateStatusByExpected(user.getId(), "active", "frozen");
            if (rows == 1) {
                Long banId = insertAutoRiskBan(user.getId(), "登录失败次数过多自动冻结");
                AuditLogUtil.success(
                        log,
                        auditId,
                        "LOGIN_RISK_FREEZE",
                        "SYSTEM",
                        "RISK_ENGINE",
                        "USER",
                        String.valueOf(user.getId()),
                        "SUCCESS",
                        "failCount=" + failCount + ",source=AUTO_RISK,banId=" + banId
                );
                return;
            }

            User latest = userMapper.selectById(user.getId());
            if (latest != null && "frozen".equalsIgnoreCase(latest.getStatus())) {
                log.info("幂等命中：action=freezeOnLoginRisk, idemKey=userId:{}, detail=latestStatus=frozen", user.getId());
                AuditLogUtil.success(
                        log,
                        auditId,
                        "LOGIN_RISK_FREEZE",
                        "SYSTEM",
                        "RISK_ENGINE",
                        "USER",
                        String.valueOf(user.getId()),
                        "IDEMPOTENT",
                        "failCount=" + failCount + ",latestStatus=frozen"
                );
                return;
            }
            log.info("登录风控冻结跳过：userId={}, latestStatus={}",
                    user.getId(), latest == null ? "NOT_FOUND" : latest.getStatus());
            AuditLogUtil.failed(
                    log,
                    auditId,
                    "LOGIN_RISK_FREEZE",
                    "SYSTEM",
                    "RISK_ENGINE",
                    "USER",
                    String.valueOf(user.getId()),
                    "CAS_CONFLICT",
                    "failCount=" + failCount + ",latestStatus=" + (latest == null ? "NOT_FOUND" : latest.getStatus())
            );
        }
    }

    /**
     * 写入自动风控封禁记录并触发信用重算。
     *
     * @return 本次写入 ban 记录 ID（若未生成则为 null）
     */
    private Long insertAutoRiskBan(Long userId, String reason) {
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
        return ban.getId();

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
        log.info("发送激活邮件到:{}，有效期24小时", maskEmail(user.getEmail()));
    }
    private String resolveExternalId(ThirdPartyLoginRequest request) {
        if (StringUtils.hasText(request.getExternalId())) {
            // 优先使用 externalId；入库/拼接前先做长度与风险片段校验。
            return InputSecurityGuard.normalizePlainText(request.getExternalId(), "externalId", 128, true);
        }
        // 回退策略：provider + authorizationCode 组合作为临时外部唯一标识。
        // 这里同样先规范化，避免把异常字符带入 Redis key / 日志。
        String provider = InputSecurityGuard.normalizePlainText(request.getProvider(), "provider", 32, true);
        String authCode = InputSecurityGuard.normalizePlainText(request.getAuthorizationCode(), "authorizationCode", 256, true);
        return provider + ":" + authCode;
    }
    private String buildJwt(User user) {
        Map<String, Object> claims = new HashMap<>();
        claims.put(JwtClaimsConstant.USER_ID, user.getId());
        return JwtUtil.createJWT(jwtProperties.getUserSecretKey(), jwtProperties.getUserTtl(), claims);
    }

    /**
     * 构建第三方登录 JWT 返回对象。
     */
    private AuthResponse buildAuthResponse(User user) {
        if (user == null) {
            throw new BusinessException("登录失败：用户不存在");
        }
        UserVO userVO = toUserVO(user);
        String token = buildJwt(user);
        return new AuthResponse(token, userVO);
    }

    /**
     * 构造 OAuth 缓存 key。
     */
    private String buildThirdPartyCacheKey(String provider, String externalId) {
        return THIRD_PARTY_BIND_KEY_PREFIX + provider + ":" + externalId;
    }

    /**
     * Redis 加速路径。
     *
     * 关键口径：
     * 1) 新缓存格式：db:{userId}，表示该映射已落库；
     * 2) 旧缓存格式：{userId}，表示历史 Redis-only 数据，登录时执行一次懒迁移；
     * 3) 若缓存 userId 对应用户不存在，视为脏缓存，直接删除并回源 DB。
     */
    private User tryLoginByOauthCache(String redisKey, String provider, String externalId) {
        String cachedValue = stringRedisTemplate.opsForValue().get(redisKey);
        if (!StringUtils.hasText(cachedValue)) {
            return null;
        }

        Long cachedUserId = parseUserIdFromOauthCache(cachedValue);
        if (cachedUserId == null) {
            // 值格式异常（可能人工误写），直接清理，避免后续重复报错。
            stringRedisTemplate.delete(redisKey);
            return null;
        }

        User user = userMapper.selectById(cachedUserId);
        if (user == null) {
            // 缓存命中但用户不存在，说明缓存已脏（用户删改/导库/历史残留），清掉后回源 DB。
            stringRedisTemplate.delete(redisKey);
            return null;
        }

        // 仅对历史旧缓存做一次 DB 回填，避免每次登录都访问绑定表。
        if (!cachedValue.startsWith(OAUTH_CACHE_DB_TAG)) {
            Long authoritativeUserId = backfillLegacyOauthCache(provider, externalId, cachedUserId);
            cacheThirdPartyBind(redisKey, authoritativeUserId);
            if (!authoritativeUserId.equals(cachedUserId)) {
                User authoritativeUser = userMapper.selectById(authoritativeUserId);
                if (authoritativeUser == null) {
                    throw new BusinessException("第三方账号绑定异常，请联系管理员");
                }
                return authoritativeUser;
            }
        }
        return user;
    }

    /**
     * DB 真源路径：按 provider + externalId 查询绑定，再回填 Redis。
     */
    private User loadUserByOauthBind(String provider, String externalId, String redisKey) {
        UserOauthBind bind = userOauthBindMapper.selectActiveByProviderAndExternalId(provider, externalId);
        if (bind == null) {
            return null;
        }
        User user = userMapper.selectById(bind.getUserId());
        if (user == null) {
            // DB 绑定存在但用户不存在，属于数据完整性问题，需要显式暴露。
            throw new BusinessException("第三方账号绑定异常，请联系管理员");
        }
        userOauthBindMapper.touchLoginTime(provider, externalId, LocalDateTime.now());
        cacheThirdPartyBind(redisKey, bind.getUserId());
        return user;
    }

    /**
     * 首次第三方登录：创建用户并写入绑定（带并发冲突恢复）。
     *
     * 并发说明：
     * - 两个请求同时首次登录同一 externalId 时，唯一键可能冲突；
     * - 冲突请求回滚后读取已有绑定，最终都指向同一 userId。
     */
    private User createAndBindThirdPartyUserWithRaceRecover(String provider, String externalId, String redisKey) {
        Long userId;
        try {
            userId = createAndBindThirdPartyUserTx(provider, externalId);
        } catch (DuplicateKeyException ex) {
            // 竞争下的正常分支：说明另一请求已经先完成绑定，当前请求直接回读即可。
            UserOauthBind existing = userOauthBindMapper.selectActiveByProviderAndExternalId(provider, externalId);
            if (existing == null) {
                throw new BusinessException("第三方登录失败，请稍后重试");
            }
            userOauthBindMapper.touchLoginTime(provider, externalId, LocalDateTime.now());
            userId = existing.getUserId();
            log.info("第三方登录并发冲突恢复：provider={}, externalId={}, userId={}",
                    provider, maskExternalId(externalId), userId);
        }

        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException("第三方登录失败：用户不存在");
        }
        cacheThirdPartyBind(redisKey, userId);
        return user;
    }

    /**
     * 事务块：创建用户 + 绑定落库（原子）。
     */
    private Long createAndBindThirdPartyUserTx(String provider, String externalId) {
        Long createdUserId = transactionTemplate.execute(status -> {
            // 双检：避免事务开启后仍重复创建。
            UserOauthBind existing = userOauthBindMapper.selectActiveByProviderAndExternalId(provider, externalId);
            if (existing != null) {
                userOauthBindMapper.touchLoginTime(provider, externalId, LocalDateTime.now());
                return existing.getUserId();
            }

            User user = createThirdPartyUser(provider, externalId);
            userMapper.insertUser(user);
            if (user.getId() == null) {
                throw new BusinessException("第三方登录失败：用户创建异常");
            }

            UserOauthBind bind = buildOauthBind(user.getId(), provider, externalId, LocalDateTime.now());
            userOauthBindMapper.insert(bind);
            log.info("第三方账号首次绑定成功：provider={}, externalId={}, userId={}",
                    provider, maskExternalId(externalId), user.getId());
            return user.getId();
        });
        if (createdUserId == null) {
            throw new BusinessException("第三方登录失败：事务未返回用户");
        }
        return createdUserId;
    }

    /**
     * 历史 Redis-only 缓存懒迁移。
     *
     * 行为：
     * 1) 先看 DB 是否已有绑定（DB 优先）；
     * 2) DB 无记录时尝试 insert ignore 回填；
     * 3) 回填竞争冲突时再次读 DB，确保返回最终一致 userId。
     */
    private Long backfillLegacyOauthCache(String provider, String externalId, Long cachedUserId) {
        LocalDateTime now = LocalDateTime.now();
        UserOauthBind existing = userOauthBindMapper.selectActiveByProviderAndExternalId(provider, externalId);
        if (existing != null) {
            userOauthBindMapper.touchLoginTime(provider, externalId, now);
            return existing.getUserId();
        }

        UserOauthBind backfill = buildOauthBind(cachedUserId, provider, externalId, now);
        int inserted = userOauthBindMapper.insertIgnore(backfill);
        if (inserted == 1) {
            return cachedUserId;
        }

        UserOauthBind recovered = userOauthBindMapper.selectActiveByProviderAndExternalId(provider, externalId);
        if (recovered != null) {
            return recovered.getUserId();
        }

        // 极小概率：冲突发生但立即读不到（如隔离级别/短暂异常），先保底返回缓存用户并记录告警。
        log.warn("第三方绑定懒迁移异常回退：provider={}, externalId={}, cachedUserId={}",
                provider, maskExternalId(externalId), cachedUserId);
        return cachedUserId;
    }

    /**
     * 构建绑定实体（统一默认值，避免各分支字段漏填）。
     */
    private UserOauthBind buildOauthBind(Long userId, String provider, String externalId, LocalDateTime now) {
        UserOauthBind bind = new UserOauthBind();
        bind.setUserId(userId);
        bind.setProvider(provider);
        bind.setExternalId(externalId);
        bind.setBindStatus(1);
        bind.setLastLoginTime(now);
        bind.setRemark("day18_oauth_binding");
        return bind;
    }

    /**
     * 写入 OAuth 缓存（统一使用 db:{userId} 作为新格式）。
     */
    private void cacheThirdPartyBind(String redisKey, Long userId) {
        stringRedisTemplate.opsForValue().set(
                redisKey,
                OAUTH_CACHE_DB_TAG + userId,
                Duration.ofDays(OAUTH_CACHE_TTL_DAYS)
        );
    }

    /**
     * 解析 OAuth 缓存值为 userId。
     *
     * 兼容两种格式：
     * - 新格式：db:{userId}
     * - 旧格式：{userId}
     */
    private Long parseUserIdFromOauthCache(String cachedValue) {
        if (!StringUtils.hasText(cachedValue)) {
            return null;
        }
        String trimmed = cachedValue.trim();
        String pureUserId = trimmed.startsWith(OAUTH_CACHE_DB_TAG)
                ? trimmed.substring(OAUTH_CACHE_DB_TAG.length())
                : trimmed;
        try {
            return Long.parseLong(pureUserId);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private User createThirdPartyUser(String provider, String externalId) {
        User user = buildBaseUser();
        user.setUsername(provider + "_" + externalId.substring(0, Math.min(12, externalId.length())));
        user.setStatus("active");
        // 该方法只负责构建实体，不做 insert。
        // 插入必须放在事务块里，与 user_oauth_bind 同事务提交。
        return user;
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
        String prefix = trimmed.substring(0, at);
        String domain = trimmed.substring(at + 1);
        return prefix.substring(0, 1) + "***@" + domain;
    }

    private String maskExternalId(String externalId) {
        if (!StringUtils.hasText(externalId)) {
            return "EMPTY";
        }
        String trimmed = externalId.trim();
        if (trimmed.length() <= 6) {
            return "***";
        }
        return trimmed.substring(0, 3) + "***" + trimmed.substring(trimmed.length() - 2);
    }

}


