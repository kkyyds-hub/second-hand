package com.demo.service.serviceimpl;

import com.demo.avatar.AvatarStorageProvider;
import com.demo.avatar.AvatarStorageProviderFactory;
import com.demo.audit.AuditLogUtil;
import com.demo.constant.MessageConstant;
import com.demo.context.BaseContext;
import com.demo.dto.admin.AdminCreateUserRequest;
import com.demo.dto.user.*;
import com.demo.entity.User;
import com.demo.exception.BusinessException;
import com.demo.mapper.UserMapper;
import com.demo.security.InputSecurityGuard;
import com.demo.result.PageResult;
import com.demo.service.UserService;
import com.demo.vo.AvatarUploadConfigVO;
import com.demo.vo.UserVO;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 用户服务实现。
 * 包含用户资料、绑定关系、风控封禁、导出等业务。
 */
@Service
@Transactional
@Slf4j
public class UserServiceImpl implements UserService {
    /** 短信验证码缓存 key 前缀。 */
    private static final String SMS_CODE_KEY_PREFIX = "auth:sms:code:";
    /** 邮箱验证码缓存 key 前缀。 */
    private static final String EMAIL_CODE_KEY_PREFIX = "auth:email:code:";
    /** 管理员手动建档时的初始密码（建议用户首次登录后修改）。 */
    private static final String ADMIN_CREATE_DEFAULT_PASSWORD = "123456";

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private AvatarStorageProviderFactory avatarStorageProviderFactory;

    /**
     * 分页查询用户列表。
     */
    @Override
    public PageResult<UserVO> getUserPage(UserQueryDTO queryDTO) {
        log.info("分页查询用户: {}", queryDTO);
        PageHelper.startPage(queryDTO.getPage(), queryDTO.getPageSize());

        // 2. 执行普通查询（PageHelper会自动分页）
        List<User> userList = userMapper.selectUsers(queryDTO);

        // 3. 获取分页信息
        PageInfo<User> pageInfo = new PageInfo<>(userList);

        // 4. 转换为 VO
        List<UserVO> userVOList = convertToVOList(userList);

        // 5. 返回结果
        return new PageResult<>(
                userVOList,
                pageInfo.getTotal(),
                queryDTO.getPage(),
                queryDTO.getPageSize()
        );
    }

    /**
     * 更新当前用户资料。
     */
    @Override
    public UserVO updateProfile(UpdateProfileRequest request) {
        log.info("更新用户信息: {}", request);
        User user = getCurrentUserOrThrow();
        // Day18 P3-S2：昵称/简介是前端可控文本，先统一做 trim/长度/XSS 风险拦截。
        String nickname = InputSecurityGuard.normalizePlainText(request.getNickname(), "昵称", 20, true);
        String bio = InputSecurityGuard.normalizePlainText(request.getBio(), "简介", 150, false);
        // avatar 属于 URL 字段，先做空白归一化，后续由 avatarValidation 做协议/后缀校验。
        String avatar = StringUtils.isBlank(request.getAvatar()) ? null : request.getAvatar().trim();

        nicknameValidation(nickname);
        avatarValidation(avatar);
        bioValidation(bio);
        user.setNickname(nickname);
        user.setAvatar(avatar);
        user.setBio(bio);
        userMapper.updateProfile(user);
        UserVO userVO = new UserVO();
        BeanUtils.copyProperties(user, userVO);
        return userVO;
    }

    /**
     * 生成头像上传配置。
     */
    @Override
    public AvatarUploadConfigVO generateAvatarUploadConfig(AvatarUploadConfigRequest request, String requestBaseUrl) {
        AvatarStorageProvider provider = avatarStorageProviderFactory.getProvider();
        return provider.generateUploadConfig(getCurrentUserIdOrThrow(), request, requestBaseUrl);
    }

    @Override
    public String uploadAvatar(AvatarUploadTicketRequest request,
                               String contentType,
                               long contentLength,
                               InputStream inputStream,
                               String requestBaseUrl) {
        AvatarStorageProvider provider = avatarStorageProviderFactory.getProvider();
        return provider.uploadAvatar(
                getCurrentUserIdOrThrow(),
                request,
                contentType,
                contentLength,
                inputStream,
                requestBaseUrl
        );
    }

    /**
     * 修改密码。
     */
    @Override
    public void changePassword(ChangePasswordRequest request) {
        User user = getCurrentUserOrThrow();
        Long currentUserId = user.getId();
        boolean verified = false;
        String oldPassword = StringUtils.isNotBlank(request.getOldPassword())
                ? request.getOldPassword()
                : request.getCurrentPassword();

        // 1. 使用当前密码验证（如果传了）
        if (StringUtils.isNotBlank(oldPassword)) {
            if (!passwordEncoder.matches(oldPassword, user.getPassword())) {
                throw new BusinessException(MessageConstant.PASSWORD_ERROR);
            }
            verified = true;
        }

        // 2. 使用验证码验证（如果指定了渠道）
        if (StringUtils.isNotBlank(request.getVerifyChannel())) {
            String channel = request.getVerifyChannel().toLowerCase();
            String code = request.getCode();
            if (StringUtils.isBlank(code)) {
                throw new BusinessException("验证码不能为空");
            }

            switch (channel.toLowerCase()) {
                case "phone":
                    // 用绑定的手机号 + Redis 里的验证码做校验
                    verifySmsCode(user, code);
                    break;
                case "email":
                    // 用绑定的邮箱 + Redis 里的验证码做校验
                    verifyEmailCode(user, code);
                    break;
                default:
                    throw new BusinessException("不支持的验证渠道");
            }
            verified = true;
        }

        if (!verified) {
            throw new BusinessException("请提供当前密码或验证码进行验证");
        }

        // 3. 把新密码加密后再更新
        String encodedNewPassword = passwordEncoder.encode(request.getNewPassword());
        userMapper.updatePassword(currentUserId, encodedNewPassword);
    }


    /**
     * 绑定手机号。
     * 会先校验验证码，再校验手机号是否被其他账号占用。
     */
    @Override
    public UserVO bindPhone(BindPhoneRequest request) {
        User user = getCurrentUserOrThrow();
        Long currentUserId = user.getId();

        String mobile = request.getValue();

        // 1. 校验发到“新手机号”上的验证码
        verifySmsCodeForMobile(mobile, request.getVerifyCode());

        // 2. 校验唯一性：是否被其他账号占用
        User existed = userMapper.selectByMobile(mobile);
        if (existed != null && !existed.getId().equals(currentUserId)) {
            throw new BusinessException("该手机号已被其他账号绑定");
        }

        // 3. 更新绑定关系
        userMapper.updateMobile(currentUserId, mobile);
        stringRedisTemplate.delete(SMS_CODE_KEY_PREFIX + mobile);
        user.setMobile(mobile);
        return toUserVO(user);
    }


    /**
     * 绑定邮箱。
     * 会先校验验证码，再校验邮箱是否被其他账号占用。
     */
    @Override
    public UserVO bindEmail(BindEmailRequest request) {
        User user = getCurrentUserOrThrow();
        Long currentUserId = user.getId();

        String email = request.getValue();
        verifyEmailCodeForEmail(email, request.getVerifyCode());

        User existed = userMapper.selectByEmail(email);
        if (existed != null && !existed.getId().equals(currentUserId)) {
            throw new BusinessException("该邮箱已被其他账号绑定");
        }

        userMapper.updateEmail(currentUserId, email);
        stringRedisTemplate.delete(EMAIL_CODE_KEY_PREFIX + email);
        user.setEmail(email);
        return toUserVO(user);
    }



    /**
     * 解绑手机号。
     * 解绑前需要通过密码或验证码做二次验证。
     */
    @Override
    public void unbindPhone(UnbindContactRequest request) {
        User user = getCurrentUserOrThrow();
        Long currentUserId = user.getId();

        if (StringUtils.isBlank(user.getMobile())) {
            throw new BusinessException("当前账号未绑定手机号");
        }

        verifyUnbindRequest(user, request);
        userMapper.updateMobile(currentUserId, null);
        stringRedisTemplate.delete(SMS_CODE_KEY_PREFIX + user.getMobile());
    }


    /**
     * 解绑邮箱。
     * 解绑前需要通过密码或验证码做二次验证。
     */
    @Override
    public void unbindEmail(UnbindContactRequest request) {
        User user = getCurrentUserOrThrow();
        Long currentUserId = user.getId();
        if (StringUtils.isBlank(user.getEmail())) {
            throw new BusinessException("当前账号未绑定邮箱");
        }

        verifyUnbindRequest(user, request);
        userMapper.updateEmail(currentUserId, null);
        stringRedisTemplate.delete(EMAIL_CODE_KEY_PREFIX + user.getEmail());
    }

    /**
     * 校验用户是否具备卖家身份。
     */
    @Override
    public void requireSeller(Long userId) {
        if (userId == null) {
            throw new BusinessException("未登录或会话已失效");
        }
        Integer isSeller = userMapper.selectIsSellerById(userId);
        if (isSeller == null || !isSeller.equals(1)) {
            throw new BusinessException("仅卖家可执行该操作");
        }
    }

    /**
     * 管理员手动创建用户。
     * 当前策略：建档后默认激活，并按角色设置卖家标识。
     */
    @Override
    public UserVO createAdminUser(AdminCreateUserRequest request) {
        String name = InputSecurityGuard.normalizePlainText(request.getName(), "昵称", 20, true);
        String phone = request.getPhone() == null ? null : request.getPhone().trim();
        String role = request.getRole() == null ? "" : request.getRole().trim();

        User existed = userMapper.selectByMobile(phone);
        if (existed != null) {
            throw new BusinessException("手机号已被注册");
        }

        User user = new User();
        LocalDateTime now = LocalDateTime.now();
        user.setUsername(generateAdminUsername(phone));
        user.setNickname(name);
        user.setMobile(phone);
        user.setPassword(passwordEncoder.encode(ADMIN_CREATE_DEFAULT_PASSWORD));
        user.setCreditScore(100);
        user.setCreditLevel("lv3");
        user.setCreditUpdatedAt(now);
        user.setStatus("active");
        user.setIsSeller(isSellerRole(role) ? 1 : 0);

        userMapper.insertUser(user);
        log.info("管理员手动建档成功：id={}, phone={}, role={}, isSeller={}",
                user.getId(), phone, role, user.getIsSeller());
        return toUserVO(user);
    }

    /**
     * 判断角色是否属于卖家类型。
     */
    private boolean isSellerRole(String role) {
        if (StringUtils.isBlank(role)) {
            return false;
        }
        return "SELLER_PERSONAL".equalsIgnoreCase(role)
                || "SELLER_ENTERPRISE".equalsIgnoreCase(role)
                || "个人卖家".equals(role)
                || "企业商家".equals(role);
    }

    /**
     * 生成管理员建档使用的唯一用户名。
     */
    private String generateAdminUsername(String phone) {
        String suffix = (phone != null && phone.length() >= 4)
                ? phone.substring(phone.length() - 4)
                : String.valueOf(System.currentTimeMillis() % 10000);
        return "admin_u_" + suffix + "_" + System.currentTimeMillis();
    }


    private List<UserVO> convertToVOList(List<User> userList) {
        return userList.stream().map(user -> {
            UserVO userVO = new UserVO();
            BeanUtils.copyProperties(user, userVO);
            return userVO;
        }).collect(Collectors.toList());
    }

    private void bioValidation(String bio) {
        if (bio == null) {
            return;
        }
        if (bio.length() > 150) {
            throw new BusinessException("简介不能超过150个字符");
        }
        List<String> blockedWords = Arrays.asList("admin", "管理员", "违规");
        for (String word : blockedWords) {
            if (bio.toLowerCase().contains(word.toLowerCase())) {
                throw new BusinessException("简介包含敏感词，请更换");
            }
        }
    }

    private void avatarValidation(String avatar) {
        if (StringUtils.isBlank(avatar)) {
            return;
        }
        if (avatarStorageProviderFactory.getProvider().supportsAvatarUrl(avatar)) {
            return;
        }
        String lower = avatar.toLowerCase();
        boolean isHttp = lower.startsWith("http://") || lower.startsWith("https://");
        boolean supportedExt = lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png");
        if (!isHttp || !supportedExt) {
            throw new BusinessException("头像地址需为可访问的JPG/PNG链接");
        }
    }

    private void nicknameValidation(String nickname) {
        if (StringUtils.isBlank(nickname)) {
            throw new BusinessException("昵称不能为空");
        }
        // 长度校验更建议放在 DTO 上，这里简单兜底
        if (nickname.length() < 1 || nickname.length() > 20) {
            throw new BusinessException("昵称长度需在 1-20 个字符内");
        }

        List<String> blockedWords = Arrays.asList("admin", "管理员", "违规");
        for (String word : blockedWords) {
            if (nickname.toLowerCase().contains(word.toLowerCase())) {
                throw new BusinessException("昵称包含敏感词，请更换");
            }
        }
    }
    private void verifySmsCode(User user, String code) {
        if (StringUtils.isBlank(user.getMobile())) {
            throw new BusinessException("未绑定手机号，无法使用短信验证");
        }
        verifySmsCodeForMobile(user.getMobile(), code);
    }

    private void verifyEmailCode(User user, String code) {
        if (StringUtils.isBlank(user.getEmail())) {
            throw new BusinessException("未绑定邮箱，无法使用邮箱验证");
        }
        verifyEmailCodeForEmail(user.getEmail(), code);
    }
    /**
     * 解绑前的安全校验。
     * - 可以用当前密码验证
     * - 也可以用验证码验证（手机/邮箱）
     * - 至少通过一种验证，否则不允许解绑
     */
    private void verifyUnbindRequest(User user, UnbindContactRequest request) {
        boolean verified = false;

        // 1. 使用当前密码验证（如果传了）
        if (StringUtils.isNotBlank(request.getCurrentPassword())) {
            if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
                throw new BusinessException("当前密码不正确");
            }
            verified = true;
        }

        // 2. 使用验证码验证（如果指定了验证渠道）
        String channel = request.getVerifyChannel();
        if (StringUtils.isNotBlank(channel)) {
            String code = request.getVerifyCode();
            if (StringUtils.isBlank(code)) {
                throw new BusinessException("验证码不能为空");
            }

            switch (channel.toLowerCase()) {
                case "phone":
                    verifySmsCode(user, code);
                    break;
                case "email":
                    verifyEmailCode(user, code);
                    break;
                default:
                    throw new BusinessException("不支持的验证渠道");
            }
            verified = true;
        }

        // 3. 两种方式都没用，则提示必须至少选择一种验证方式
        if (!verified) {
            throw new BusinessException("请提供当前密码或验证码进行验证");
        }
    }

    private UserVO toUserVO(User user) {
        UserVO userVO = new UserVO();
        BeanUtils.copyProperties(user, userVO);
        return userVO;
    }
    /**
     * 校验某个手机号对应的短信验证码（适用于绑定新手机号场景）
     */
    private void verifySmsCodeForMobile(String mobile, String code) {
        if (StringUtils.isBlank(mobile)) {
            throw new BusinessException("手机号不能为空");
        }
        String cacheCode = stringRedisTemplate.opsForValue().get(SMS_CODE_KEY_PREFIX + mobile);
        if (StringUtils.isBlank(cacheCode) || !cacheCode.equals(code)) {
            throw new BusinessException("验证码错误或已过期");
        }
    }

    /**
     * 校验某个邮箱对应的验证码（适用于绑定新邮箱场景）。
     */
    private void verifyEmailCodeForEmail(String email, String code) {
        if (StringUtils.isBlank(email)) {
            throw new BusinessException("邮箱不能为空");
        }
        String cacheCode = stringRedisTemplate.opsForValue().get(EMAIL_CODE_KEY_PREFIX + email);
        if (StringUtils.isBlank(cacheCode) || !cacheCode.equals(code)) {
            throw new BusinessException("验证码错误或已过期");
        }
    }
    /**
     * 获取当前登录用户，如果未登录或用户不存在则抛业务异常
     */
    private User getCurrentUserOrThrow() {
        Long currentUserId = BaseContext.getCurrentId();
        if (currentUserId == null) {
            throw new BusinessException("未登录或会话已失效");
        }
        User user = userMapper.selectById(currentUserId);
        if (user == null) {
            throw new BusinessException(MessageConstant.ID_NOT_FOUND);
        }
        return user;
    }

    /**
     * 管理端封禁用户。
     *
     * P5-S1 问题背景：
     * 1) `UserServiceImpl` 类上已经声明了 `@Transactional`，默认会沿用数据库默认隔离级别；
     * 2) 首轮并发回归时，50 个封禁请求同时命中同一用户，只有 1 个线程成功完成状态更新，
     *    其余线程理论上应该全部识别为“已处于封禁状态”；
     * 3) 但实际有一部分线程在 CAS 失败后再次查询时，仍然读到了事务快照中的旧状态，
     *    被错误分流到 `CAS_CONFLICT`，返回“用户状态已变化，请刷新后重试”。
     *
     * 本次修复：
     * - 在方法级覆盖为 `READ_COMMITTED`，保证 CAS 失败后的再次查询能观察到
     *   并发线程已提交的 `banned` 状态；
     * - 配合下方短轮询，最终把重复封禁稳定收敛为“幂等成功”。
     */
    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED, rollbackFor = Exception.class)
    public String banUser(Long userId, String reason) {
        String auditId = AuditLogUtil.newAuditId();
        String normalizedReason = StringUtils.isBlank(reason) ? "未填写原因" : reason.trim();
        User user = userMapper.selectById(userId);
        if (user == null) {
            AuditLogUtil.failed(log, auditId, "USER_BAN", "ADMIN", String.valueOf(BaseContext.getCurrentId()), "USER", String.valueOf(userId), "USER_NOT_FOUND", "user not found");
            throw new BusinessException("用户不存在");
        }

        if ("banned".equalsIgnoreCase(user.getStatus())) {
            AuditLogUtil.success(log, auditId, "USER_BAN", "ADMIN", String.valueOf(BaseContext.getCurrentId()), "USER", String.valueOf(userId), "IDEMPOTENT", "already banned");
            return "用户已处于封禁状态";
        }

        int rows = userMapper.updateStatusByExpected(userId, user.getStatus(), "banned");
        if (rows == 0) {
            User latest = refreshUserAfterCasMissIfStillExpected(userId, user.getStatus());
            if (latest == null) {
                AuditLogUtil.failed(log, auditId, "USER_BAN", "ADMIN", String.valueOf(BaseContext.getCurrentId()), "USER", String.valueOf(userId), "USER_NOT_FOUND", "user disappeared during CAS");
                throw new BusinessException("用户不存在");
            }
            if ("banned".equalsIgnoreCase(latest.getStatus())) {
                AuditLogUtil.success(log, auditId, "USER_BAN", "ADMIN", String.valueOf(BaseContext.getCurrentId()), "USER", String.valueOf(userId), "IDEMPOTENT", "latest status banned");
                return "用户已处于封禁状态";
            }
            AuditLogUtil.failed(log, auditId, "USER_BAN", "ADMIN", String.valueOf(BaseContext.getCurrentId()), "USER", String.valueOf(userId), "CAS_CONFLICT", "latest status=" + latest.getStatus());
            throw new BusinessException("用户状态已变化，请刷新后重试");
        }

        log.info("用户封禁成功：userId={}, reason={}", userId, normalizedReason);
        AuditLogUtil.success(log, auditId, "USER_BAN", "ADMIN", String.valueOf(BaseContext.getCurrentId()), "USER", String.valueOf(userId), "SUCCESS", "status changed to banned, reason=" + normalizedReason);
        return "用户封禁成功";
    }

    /**
     * 兼容旧接口签名的封禁方法。
     * 历史调用方不传原因时，统一委托到带 `reason` 的新方法。
     */
    @Override
    public String banUser(Long userId) {
        return banUser(userId, null);
    }

    /**
     * 管理端解封用户。
     * 采用与封禁一致的 CAS + 短轮询策略，减少并发误判。
     */
    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED, rollbackFor = Exception.class)
    public String unbanUser(Long userId) {
        String auditId = AuditLogUtil.newAuditId();
        User user = userMapper.selectById(userId);
        if (user == null) {
            AuditLogUtil.failed(log, auditId, "USER_UNBAN", "ADMIN", String.valueOf(BaseContext.getCurrentId()), "USER", String.valueOf(userId), "USER_NOT_FOUND", "user not found");
            throw new BusinessException("用户不存在");
        }

        if ("active".equalsIgnoreCase(user.getStatus())) {
            AuditLogUtil.success(log, auditId, "USER_UNBAN", "ADMIN", String.valueOf(BaseContext.getCurrentId()), "USER", String.valueOf(userId), "IDEMPOTENT", "already active");
            return "用户已处于正常状态";
        }

        int rows = userMapper.updateStatusByExpected(userId, user.getStatus(), "active");
        if (rows == 0) {
            User latest = refreshUserAfterCasMissIfStillExpected(userId, user.getStatus());
            if (latest == null) {
                AuditLogUtil.failed(log, auditId, "USER_UNBAN", "ADMIN", String.valueOf(BaseContext.getCurrentId()), "USER", String.valueOf(userId), "USER_NOT_FOUND", "user disappeared during CAS");
                throw new BusinessException("用户不存在");
            }
            if ("active".equalsIgnoreCase(latest.getStatus())) {
                AuditLogUtil.success(log, auditId, "USER_UNBAN", "ADMIN", String.valueOf(BaseContext.getCurrentId()), "USER", String.valueOf(userId), "IDEMPOTENT", "latest status active");
                return "用户已处于正常状态";
            }
            AuditLogUtil.failed(log, auditId, "USER_UNBAN", "ADMIN", String.valueOf(BaseContext.getCurrentId()), "USER", String.valueOf(userId), "CAS_CONFLICT", "latest status=" + latest.getStatus());
            throw new BusinessException("用户状态已变化，请刷新后重试");
        }

        log.info("用户解封成功：userId={}", userId);
        AuditLogUtil.success(log, auditId, "USER_UNBAN", "ADMIN", String.valueOf(BaseContext.getCurrentId()), "USER", String.valueOf(userId), "SUCCESS", "status changed to active");
        return "用户解封成功";
    }

    /**
     * 导出用户 CSV。
     */
    @Override
    public String exportUsersCSV(String keyword, LocalDateTime startTime, LocalDateTime endTime) {
        List<User> users = userMapper.exportAllUsers(keyword, startTime, endTime);

        StringBuilder csv = new StringBuilder();
        // CSV 表头（文档 5.5.2 固定列顺序）
        csv.append("id,username,mobile,email,nickname,status,credit_score,credit_level,create_time,update_time\n");

        for (User user : users) {
            csv.append(user.getId()).append(",");
            csv.append(escapeCsv(user.getUsername())).append(",");
            csv.append(escapeCsv(user.getMobile())).append(",");
            csv.append(escapeCsv(user.getEmail())).append(",");
            csv.append(escapeCsv(user.getNickname())).append(",");
            csv.append(escapeCsv(user.getStatus())).append(",");
            csv.append(user.getCreditScore()).append(",");
            csv.append(escapeCsv(user.getCreditLevel())).append(",");
            csv.append(user.getCreateTime() != null ? user.getCreateTime().toString() : "").append(",");
            csv.append(user.getUpdateTime() != null ? user.getUpdateTime().toString() : "");
            csv.append("\n");
        }

        log.info("用户导出成功：count={}", users.size());
        return csv.toString();
    }

    /**
     * CSV 字段转义（处理逗号、引号、换行）
     */
    private String escapeCsv(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    /**
     * 用户状态 CAS 失败后的短轮询刷新。
     *
     * 设计目的：
     * 1) 如果当前线程更新失败，而数据库里很快就会出现目标状态，
     *    那么该请求应被判定为“幂等命中”，而不是“冲突失败”；
     * 2) 这里不是继续尝试写库，只是多读几次，等待并发线程提交结果变得可见；
     * 3) 本次窗口调整为 12 次 * 50ms ≈ 600ms，覆盖回归时观测到的短暂提交可见性抖动。
     *
     * 返回语义：
     * - 如果最新状态已经脱离 `expectedStatus`，调用方据此判断是否命中幂等；
     * - 如果始终没有变化，则把最后一次观察结果返回给上层做冲突分流。
     */
    private User refreshUserAfterCasMissIfStillExpected(Long userId, String expectedStatus) {
        User latest = userMapper.selectById(userId);
        if (latest == null || expectedStatus == null) {
            return latest;
        }
        for (int i = 0; i < 12; i++) {
            if (!expectedStatus.equalsIgnoreCase(latest.getStatus())) {
                return latest;
            }
            // 每次 sleep 的意义不是“等待很久”，而是给并发事务一个很短的提交和可见性窗口，
            // 让后续查询更有机会读到已经完成的目标状态。
            sleepQuietly(50L);
            User refreshed = userMapper.selectById(userId);
            if (refreshed == null) {
                return latest;
            }
            latest = refreshed;
        }
        return latest;
    }

    private void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    private Long getCurrentUserIdOrThrow() {
        Long currentUserId = BaseContext.getCurrentId();
        if (currentUserId == null) {
            throw new BusinessException("用户未登录");
        }
        return currentUserId;
    }

}

