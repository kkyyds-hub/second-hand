package com.demo.service.serviceimpl;

import com.demo.constant.MessageConstant;
import com.demo.context.BaseContext;
import com.demo.dto.user.*;
import com.demo.entity.User;
import com.demo.exception.BusinessException;
import com.demo.mapper.UserMapper;
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
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
@Slf4j
public class UserServiceImpl implements UserService {
    private static final String SMS_CODE_KEY_PREFIX = "auth:sms:code:";
    private static final String EMAIL_CODE_KEY_PREFIX = "auth:email:code:";

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Override
    public PageResult<UserVO> getUserPage(UserQueryDTO queryDTO) {
        log.info("分页查询用户: {}", queryDTO);
        PageHelper.startPage(queryDTO.getPage(), queryDTO.getSize());

        // 2. 执行普通查询（PageHelper会自动分页）
        List<User> userList = userMapper.selectUsers(queryDTO);

        // 3. 获取分页信息
        PageInfo<User> pageInfo = new PageInfo<>(userList);

        // 4. 转换为VO
        List<UserVO> userVOList = convertToVOList(userList);

        // 5. 返回结果
        return new PageResult<>(
                userVOList,
                pageInfo.getTotal(),
                queryDTO.getPage(),
                queryDTO.getSize()
        );
    }

    @Override
    public UserVO updateProfile(UpdateProfileRequest request) {
        log.info("更新用户信息: {}", request);
        User user = getCurrentUserOrThrow();
        nicknameValidation(request.getNickname());
        avatarValidation(request.getAvatar());
        bioValidation(request.getBio());
        user.setNickname(request.getNickname());
        user.setAvatar(request.getAvatar());
        user.setBio(request.getBio());
        user.setUpdateTime(LocalDateTime.now());
        userMapper.updateProfile(user);
        UserVO userVO = new UserVO();
        BeanUtils.copyProperties(user, userVO);
        return userVO;
    }

    @Override
    public AvatarUploadConfigVO generateAvatarUploadConfig(AvatarUploadConfigRequest request) {
        String normalizedFileName = request.getFileName().trim();
        String lowerFileName = normalizedFileName.toLowerCase();

        if (!lowerFileName.endsWith(".jpg") && !lowerFileName.endsWith(".jpeg") && !lowerFileName.endsWith(".png")) {
            throw new BusinessException("文件名需以 .jpg/.jpeg/.png 结尾");
        }

        // 补充一次 contentType 与扩展名的匹配校验
        boolean isPng = lowerFileName.endsWith(".png");
        if (isPng && !"image/png".equalsIgnoreCase(request.getContentType())) {
            throw new BusinessException("PNG 头像需使用 image/png 上传");
        }
        if (!isPng && !"image/jpeg".equalsIgnoreCase(request.getContentType())) {
            throw new BusinessException("JPEG 头像需使用 image/jpeg 上传");
        }

        String suffix = lowerFileName.substring(lowerFileName.lastIndexOf('.'));
        String objectKey = "avatars/" + UUID.randomUUID() + suffix;

        // 这里使用示例 OSS 域名生成资源与上传地址；实际项目中请替换为真实存储配置
        String resourceUrl = "https://oss.example.com/" + objectKey;
        String signature = UUID.randomUUID().toString().replace("-", "");
        OffsetDateTime expireAt = OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(5);
        String uploadUrl = resourceUrl + "?signature=" + signature + "&expires=" + expireAt.toEpochSecond();

        return AvatarUploadConfigVO.builder()
                .uploadUrl(uploadUrl)
                .resourceUrl(resourceUrl)
                .expiresIn(300)
                .extraHeaders(Map.of("content-type", request.getContentType()))
                .build();
    }

    @Override
    public void changePassword(ChangePasswordRequest request) {
        User user = getCurrentUserOrThrow();
        Long currentUserId = user.getId();
        boolean verified = false;

        // 1. 使用当前密码验证（如果传了）
        if (StringUtils.isNotBlank(request.getOldPassword())) {
            // 加密密码校验一定要用 matches，而不是 equals
            if (!passwordEncoder.matches(request.getOldPassword(), user.getPassword())) {
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
            // 验证码校验逻辑略...
            verified = true;
        }

        if (!verified) {
            throw new BusinessException("请提供当前密码或验证码进行验证");
        }

        // 3. 把新密码加密后再更新
        String encodedNewPassword = passwordEncoder.encode(request.getNewPassword());
        userMapper.updatePassword(currentUserId, encodedNewPassword, LocalDateTime.now());
    }


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
        LocalDateTime now = LocalDateTime.now();
        userMapper.updateMobile(currentUserId, mobile, now);
        stringRedisTemplate.delete(SMS_CODE_KEY_PREFIX + mobile);
        user.setMobile(mobile);
        user.setUpdateTime(now);
        return toUserVO(user);
    }


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

        LocalDateTime now = LocalDateTime.now();
        userMapper.updateEmail(currentUserId, email, now);
        stringRedisTemplate.delete(EMAIL_CODE_KEY_PREFIX + email);
        user.setEmail(email);
        user.setUpdateTime(now);
        return toUserVO(user);
    }



    @Override
    public void unbindPhone(UnbindContactRequest request) {
        User user = getCurrentUserOrThrow();
        Long currentUserId = user.getId();

        if (StringUtils.isBlank(user.getMobile())) {
            throw new BusinessException("当前账号未绑定手机号");
        }

        verifyUnbindRequest(user, request);
        LocalDateTime now = LocalDateTime.now();
        userMapper.updateMobile(currentUserId, null, now);
        stringRedisTemplate.delete(SMS_CODE_KEY_PREFIX + user.getMobile());
    }


    @Override
    public void unbindEmail(UnbindContactRequest request) {
      User user = getCurrentUserOrThrow();
      Long currentUserId = user.getId();
        if (StringUtils.isBlank(user.getEmail())) {
            throw new BusinessException("当前账号未绑定邮箱");
        }

        verifyUnbindRequest(user, request);
        LocalDateTime now = LocalDateTime.now();
        userMapper.updateEmail(currentUserId, null, now);
        stringRedisTemplate.delete(EMAIL_CODE_KEY_PREFIX + user.getEmail());
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
        // 长度校验更建议放在 DTO 上，这里简单兜个底
        if (nickname.length() < 1 || nickname.length() > 20) {
            throw new BusinessException("昵称长度需在1-20个字符内");
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
     * 解绑前的安全校验：
     * - 可以用当前密码验证
     * - 也可以用验证码验证（手机号 / 邮箱）
     * - 至少通过一种验证，否则不允许解绑
     */
    private void verifyUnbindRequest(User user, UnbindContactRequest request) {
        boolean verified = false;

        // 1. 使用当前密码验证（如果传了）
        if (StringUtils.isNotBlank(request.getCurrentPassword())) {
            if (!request.getCurrentPassword().equals(user.getPassword())) {
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
     * 校验某个邮箱对应的验证码（适用于绑定新邮箱场景）
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


}